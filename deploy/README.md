# PulseOps 部署工具与接入说明

本文是交付工具说明，不代表生产安装已执行。`install.sh`、`update.sh` 与 `rollback.sh` 只管理 `/opt/pulseops`、`/etc/pulseops`、`/var/lib/pulseops` 和 `pulseops.service`。它们不会修改阿里云资源、DNS、Caddy、其他服务或防火墙。

## 已知环境与选择

当前目标环境为 Alibaba Linux 3，已安装 Java 21。现有 Caddy 2 容器 `ai-chat-m1-caddy-1` 使用 `ai-chat-m1_default` 网络并占用 80/443，配置文件从宿主 `/root/ai-chat-m1/Caddyfile` 只读绑定到容器 `/etc/caddy/Caddyfile`。8080 已被 Nacos 使用。部署规划使用新子域 `pulse.ldjava.com` 和独立的 18080 端口，保留旧站点与旧服务。

检查时约有 420 MB 可用内存与 2 GB swap；这是一次观测，不是长期资源保证。构建在本地或 CI 执行，ECS 只运行产物。模板使用 64 MB 初始堆、192 MB 最大堆、SerialGC、128 MB metaspace 上限、48 MB code cache 和 512 MB `MemoryMax`。堆大小不等于进程总内存，部署后仍需观察 RSS、swap、GC 与旧服务状态。不要同时启动 systemd 和 Docker 两份应用。

**本机采用宿主 systemd 运行应用，复用已有 Docker bridge 接入 Caddy**：应用绑定 `172.19.0.1:18080`，现有 Caddy 容器地址为 `172.19.0.5`。不新增应用容器、不占用 8080。实际环境文件设置 `SERVER_ADDRESS=172.19.0.1`、`SERVER_PORT=18080`、`PULSE_TRUSTED_PROXIES=172.19.0.5`；Caddy 片段的通用上游 `pulseops:18080` 必须替换为 `172.19.0.1:18080`。[Docker bridge 官方说明](https://docs.docker.com/engine/network/drivers/bridge/)

这套模板依赖现有 Docker 网桥。`pulseops.service` 的 `Wants` 与 `After` 均包含 `docker.service`，使正常开机流程先启动 Docker，再启动绑定其 bridge 地址的应用。安装脚本在首次启动前就复制完整服务单元并执行 `daemon-reload`，因此此顺序从首次启动生效，无需后补 drop-in。这里使用 `Wants` 而非 `Requires`，不会修改、停止或重启现有 Docker 服务，也不承诺 Docker 故障时应用仍可连通；网络被删除或地址变化仍需重新核验。

## systemd 与 Caddy 的网络边界

bridge 网络容器中的 `127.0.0.1` 指容器本身，**不能把宿主只监听 `127.0.0.1:18080` 的应用作为 Caddy 上游**。systemd 方案需要先查询 `ai-chat-m1_default` 的真实 gateway、bridge 接口和 Caddy 容器地址：

```bash
docker network inspect ai-chat-m1_default
docker inspect ai-chat-m1-caddy-1
ip -4 address show
ss -lntp
```

本机核实的 gateway 为 `172.19.0.1`，应用与 Caddy 上游均使用该地址的 18080。宿主防火墙允许本机健康检查与对应 bridge/Caddy 来源访问该端口，并确认 ECS 安全组没有开放公网 18080。不要直接改成宿主 `0.0.0.0`，也不要通过清空 iptables、停止 firewalld、重启 Docker 或重建旧网络解决连通性。

防火墙规则依赖现有 iptables/nftables/firewalld 管理方式，因此本仓库不盲目安装通用规则。最终接入前分别从宿主与 Caddy 容器验证后端健康，并从外部确认 18080 不可直连。后端已实现 `PULSE_TRUSTED_PROXIES` 精确 IP 信任：只填写已核实的 Caddy 来源 IP，Caddy 用 `header_up X-Real-IP {remote_host}` 覆盖客户端传入值。容器重建后地址可能改变，应复核，不信任任意来源的转发头。

## 构建与打包

在开发机或 CI 构建，生产机不运行 npm/Maven。常规命令：

```bash
npm --prefix frontend ci
npm --prefix frontend run build
mvn --batch-mode --no-transfer-progress -f backend/pom.xml clean verify
bash deploy/package.sh 20261010-01 backend/target/pulseops-1.0.0.jar
```

如果使用 Vue 内嵌 JAR 的部署方式，应先确认最终 JAR 包含前端静态文件且 SPA 路由测试通过；打包脚本不会私自改写 JAR。如果需要另存前端产物，可以在 `package.sh` 最后增加 `frontend/dist`。这些静态文件只被归档，脚本不会自动发布到 Caddy。

产物在 `artifacts/pulseops-<版本>.tar.gz`。包内只有应用 JAR、可选前端、部署工具、空密钥示例与校验和；没有生产环境文件。`SHA256SUMS` 校验传输完整性，不是签名或发布者身份证明。解压前可先列出归档内容，使用自己构建且来源明确的包，并解压到新的独立目录。

## 首次安装

先在服务器上创建独立 MySQL 数据库和最小范围账户，并通过 RDS 内网连接确认访问。生产环境必须显式使用 MySQL，安装脚本不会接受默认 H2。不要修改已有业务数据库。

在包外准备 root 独占的环境文件，例如 `/root/pulseops.env`，参考 `pulseops.env.example`。必填项使用 `KEY=value` 格式，不加 shell 引号、不含空白或反斜杠，使用 LF 换行；脚本只解析值，不会执行或 `source` 该文件。密码留空的示例不能启动。管理员密码至少 12 字符；`PULSE_SECURE_COOKIE=true`；监听端口固定 18080。数据库 URL 中的 `?`、`&` 和密码中的 `$` 无需 shell 转义，因为文件不会被 shell 执行。

```bash
chmod 600 /root/pulseops.env
sha256sum --check SHA256SUMS
sudo bash deploy/install.sh "$(cat RELEASE_ID)" app.jar /root/pulseops.env
```

安装会先校验输入与预定路径，发现已有安装目录、服务文件或 `pulseops` 账户时拒绝覆盖；这也意味着失败的首次安装需要先检查保留现场，不能不断重复运行强行覆盖。成功路径依次创建专用无登录账户、保存不可变版本 JAR、写入 root-only 环境文件、备份配置、启动应用、等待健康检查通过，再设置开机启动。

健康检查失败会停止新应用，并保留文件便于诊断。失败原因可能是数据库、绑定地址、端口、密码或资源限制；查看服务日志，不要输出环境文件或将其粘贴到工单中。

```bash
systemctl status pulseops --no-pager
journalctl -u pulseops -n 100 --no-pager
systemctl show pulseops -p MemoryCurrent -p MemoryPeak -p MainPID
```

## 更新与应用回滚

每次使用新的版本 ID；已有版本目录不可覆盖。更新前先确认数据库迁移对旧版本保持兼容，并准备 RDS 备份/恢复方案。脚本不会导出、删除或回退数据库。

```bash
sudo bash deploy/update.sh 20261010-02 app.jar
sudo bash deploy/rollback.sh
# 或指定一个确实存在的历史版本：
sudo bash deploy/rollback.sh 20261010-01
```

更新先备份环境文件、服务单元和当前版本指向到 `/opt/pulseops/backups/<UTC时间>-<进程号>/`，该目录权限为 700、配置副本为 600。随后核验新旧 JAR 校验和，原子切换 `current` 符号链接并重启。健康检查通过后记录 `previous`；失败则尝试切回旧 JAR。每次需要停止/启动应用，**这不是零停机发布**。

“应用回滚”只切换 JAR 和可选静态文件目录，不恢复数据库、环境配置或 Caddy。若新迁移破坏了旧代码的兼容性，旧 JAR 也可能无法启动，脚本会明确失败而不会自动逆向迁移。版本与备份不会自动删除，后续清理前需明确保留策略。

## Caddy 新站点接入

`Caddyfile.fragment.example` 只是待合并的新增站点片段，**不能覆盖完整旧配置**。该片段假定 Vue 已打进 JAR 并实现 SPA 路由；只有 API 的 JAR 不能提供完整网站。

片段已包含 32 KB 请求体上限与 `X-Real-IP` 覆盖。`request_body max_size 32KB` 由 Caddy 限制读取体积，超过限制的读取应返回 413；该规则需要结合实际代理链路验收，不能用字段长度校验替代。[Caddy 请求体限制](https://caddyserver.com/docs/caddyfile/directives/request_body)

安全接入顺序：

1. 核验 `pulse.ldjava.com` 的 DNS 指向与站点备案信息；保留既有域名记录。
2. 备份完整宿主 Caddyfile，记录旧站点的访问基线。
3. 在独立候选文件中追加新站点，使用最终选定的容器名或 bridge gateway 上游。
4. 检查 mount 类型。当前是单文件 bind mount：在宿主原子替换文件可能让容器继续看到旧 inode。应在已有文件中写入已审阅的完整候选内容以保留 inode，或采用已验证的挂载更新方式；不要为了换配置重建整个旧 Compose 项目。
5. 在现有容器中执行 `caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`。验证失败立即原地恢复备份，不 reload。
6. 验证成功后使用 `caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile`；不要 stop/start 旧 Caddy。Caddy 的 reload 是更新运行配置的官方方式。[Caddy 命令文档](https://caddyserver.com/docs/command-line#caddy-reload)
7. 同时验证所有旧站点、新站点 HTTPS、公开 API、管理员会话和 SPA 深链接。失败时原地恢复备份、validate 后 reload，并复核旧站点。

在该环境中相应的容器命令前缀为 `docker exec ai-chat-m1-caddy-1`。以上操作由最终部署步骤执行，本仓库脚本不会自行运行它们。

## CI 与验证边界

`.github/workflows/ci.yml` 运行 Java 21 的 Maven `clean verify`、Node 22 的 `npm ci` 与类型检查/生产构建，以及部署脚本的 Bash 语法检查。工作流只有仓库读取权限，不包含 SSH 密钥、数据库凭证或自动部署动作。Actions 用法参考官方 [checkout](https://github.com/actions/checkout)、[setup-java](https://github.com/actions/setup-java) 与 [setup-node](https://github.com/actions/setup-node)。

脚本本地通过语法检查不代表 Alibaba Linux 上的安装、资源限制、Docker 连通性或回滚已经验证。实际执行后，把命令、环境和结果写入 [验收记录](../docs/03-acceptance.md)，并保留尚未验证项。
