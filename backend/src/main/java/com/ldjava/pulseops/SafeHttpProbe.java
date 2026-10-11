package com.ldjava.pulseops;

import org.springframework.stereotype.Component;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

/** Resolves once, checks every address, then connects to that exact address with verified TLS SNI. */
@Component
public class SafeHttpProbe {
    private final PulseProperties properties;
    private final ThreadPoolExecutor dnsWorkers = new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r,"pulse-dns"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r,"pulse-deadline"); t.setDaemon(true); return t;
    });
    SafeHttpProbe(PulseProperties properties) { this.properties = properties; }
    public record Result(Instant checkedAt, String status, long latencyMs, Integer statusCode, String error) {}
    public URI validate(String value) {
        try {
            URI uri = URI.create(value.strip());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw ApiException.badRequest("仅支持 HTTPS 443 端口，URL 不得包含凭据或片段");
            if (!properties.hostList().contains(host.toLowerCase(Locale.ROOT)))
                throw ApiException.badRequest("目标域名不在服务器配置的允许列表中");
            if (host.endsWith(".") || host.contains(":") || host.matches("[0-9.]+"))
                throw ApiException.badRequest("目标必须使用允许列表中的完整域名");
            return URI.create(uri.toASCIIString());
        } catch (IllegalArgumentException e) { throw ApiException.badRequest("请输入有效的 HTTPS URL"); }
    }
    InetAddress[] resolvePublic(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) if (!isPublic(address)) throw new UnknownHostException("Target address is not public");
        if (addresses.length == 0) throw new UnknownHostException("No address");
        return addresses;
    }
    private InetAddress[] resolveWithin(String host, long deadline) throws IOException {
        Future<InetAddress[]> future;
        try { future = dnsWorkers.submit(() -> resolvePublic(host)); }
        catch (RejectedExecutionException e) { throw new SocketTimeoutException("DNS capacity unavailable"); }
        try { return future.get(remaining(deadline),TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { future.cancel(true); throw new SocketTimeoutException("DNS timeout"); }
        catch (InterruptedException e) { future.cancel(true); Thread.currentThread().interrupt(); throw new IOException("Interrupted",e); }
        catch (ExecutionException e) { if (e.getCause() instanceof UnknownHostException unknown) throw unknown; throw new IOException("DNS failed",e); }
    }
    @PreDestroy void stop() { dnsWorkers.shutdownNow(); deadlines.shutdownNow(); }
    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress(); int first = b[0] & 255;
        if (b.length == 4) {
            int second = b[1] & 255, third = b[2] & 255;
            return first != 0 && first != 10 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254) && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && (second == 168 || (second == 0 && (third == 0 || third == 2))))
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        // Only native globally routable IPv6 2000::/3; reject transition and documentation networks.
        return (first & 0xe0) == 0x20 && !(first == 0x20 && (b[1] & 255) == 2)
                && !(first == 0x20 && (b[1] & 255) == 1 && ((b[2] & 255) == 0 || ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)));
    }
    public Result check(String url, int timeoutMs, int expectedStatus) {
        Instant checkedAt = Instant.now(); long start = System.nanoTime();
        try {
            URI uri = validate(url); String host = uri.getHost();
            long deadline = start + timeoutMs * 1_000_000L;
            InetAddress[] addresses = resolveWithin(host,deadline);
            // Prefer IPv4 when an ECS has no IPv6 egress. Every candidate was validated above.
            InetAddress selected = java.util.Arrays.stream(addresses).filter(a -> a instanceof Inet4Address).findFirst().orElse(addresses[0]);
            try (Socket raw = new Socket()) {
                // Socket read timeouts alone do not bound a TLS handshake with many slow reads.
                ScheduledFuture<?> closer = deadlines.schedule(() -> { try { raw.close(); } catch (IOException ignored) {} },remaining(deadline),TimeUnit.MILLISECONDS);
                try {
                raw.connect(new InetSocketAddress(selected, 443), remaining(deadline));
                raw.setSoTimeout(remaining(deadline));
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, 443, true)) {
                    SSLParameters params = tls.getSSLParameters(); params.setEndpointIdentificationAlgorithm("HTTPS");
                    params.setServerNames(List.of(new SNIHostName(host))); tls.setSSLParameters(params);
                    tls.setSoTimeout(remaining(deadline)); tls.startHandshake();
                    String path = uri.getRawPath(); if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                    String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nUser-Agent: PulseOps/1.0\r\nAccept: */*\r\nConnection: close\r\n\r\n";
                    tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII)); tls.getOutputStream().flush();
                    InputStream input = tls.getInputStream(); StringBuilder line = new StringBuilder();
                    while (line.length() < 2048) {
                        tls.setSoTimeout(remaining(deadline)); int c = input.read(); if (c < 0 || c == '\n') break;
                        if (c != '\r') line.append((char)c);
                    }
                    String[] parts = line.toString().split(" ", 3);
                    if (parts.length < 2 || !parts[0].startsWith("HTTP/1.") || !parts[1].matches("[1-5][0-9]{2}")) throw new IOException("Invalid HTTP response");
                    int code = Integer.parseInt(parts[1]); boolean up = code == expectedStatus;
                    return new Result(checkedAt, up ? "UP" : "DOWN", elapsed(start), code, up ? null : "HTTP " + code + "，预期 " + expectedStatus);
                }
                } finally { closer.cancel(false); }
            }
        } catch (SocketTimeoutException e) { return failed(checkedAt, start, "请求超时"); }
        catch (UnknownHostException e) { return failed(checkedAt, start, "DNS 无法解析或目标地址不允许"); }
        catch (SSLException e) { return failed(checkedAt, start, "TLS 连接或证书验证失败"); }
        catch (Exception e) { return failed(checkedAt, start, elapsed(start) >= timeoutMs ? "请求超时" : "目标连接失败"); }
    }
    private static int remaining(long deadline) throws SocketTimeoutException {
        long millis = (deadline - System.nanoTime()) / 1_000_000;
        if (millis <= 0) throw new SocketTimeoutException(); return (int)Math.min(millis, Integer.MAX_VALUE);
    }
    private static long elapsed(long start) { return Math.max(1, (System.nanoTime() - start) / 1_000_000); }
    private static Result failed(Instant at, long start, String error) { return new Result(at, "DOWN", elapsed(start), null, error); }
    public static String publicUrl(String value) {
        URI uri = URI.create(value); String path = uri.getRawPath();
        return uri.getScheme() + "://" + uri.getHost() + (path == null || path.isEmpty() ? "/" : path);
    }
}
