package com.ldjava.pulseops;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.*;

class SafeHttpProbeTest {
    final SafeHttpProbe probe = new SafeHttpProbe(new PulseProperties("admin","unused-for-unit","example.com,localhost,127.0.0.1","","",30,30,false,"127.0.0.1"));
    @Test void urlValidationRejectsCredentialsFragmentsPortsAndNonAllowlistedHosts() {
        for (String url : new String[]{"http://example.com/","https://example.com:8443/","https://user:pass@example.com/","https://example.com/#fragment","https://evil.example.com/","https://127.0.0.1/","https://example.com.evil.org/"})
            assertThatThrownBy(() -> probe.validate(url)).as(url).isInstanceOf(ApiException.class);
        assertThat(probe.validate("https://example.com/status?token=private").getHost()).isEqualTo("example.com");
        assertThat(SafeHttpProbe.publicUrl("https://example.com/status?token=private")).isEqualTo("https://example.com/status");
    }
    @Test void privateMetadataMulticastAndNonGlobalAddressesAreRejected() throws Exception {
        for (String ip : new String[]{"0.0.0.0","10.0.0.1","127.0.0.1","169.254.169.254","172.16.1.2","192.168.0.1","100.100.100.200","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1","2001:db8::1","2002:7f00:1::"})
            assertThat(SafeHttpProbe.isPublic(InetAddress.getByName(ip))).as(ip).isFalse();
        assertThat(SafeHttpProbe.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeHttpProbe.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
    @Test void resolvingAnAllowlistedLocalHostIsStillRejected() {
        assertThatThrownBy(() -> probe.resolvePublic("localhost")).isInstanceOf(java.net.UnknownHostException.class);
        var result = probe.check("https://localhost/",500,200);
        assertThat(result.status()).isEqualTo("DOWN"); assertThat(result.error()).contains("DNS");
        probe.stop();
    }
}
