# 恋吧 AI 智能体 —— 阿里云 + 宝塔面板 + Docker 部署手册

> 本手册面向零基础用户，请**严格按章节顺序**操作，每一步都有验证方法。
> 遇到报错时，先看第 8 章「常见故障排查」。

---

## 0. 先看懂：这个项目是怎么跑起来的

```
用户浏览器
   │  https://你的域名  (80/443 端口)
   ▼
阿里云安全组（只放行 22 / 宝塔面板端口 / 80 / 443）
   ▼
宝塔面板的 Nginx  ← 申请 SSL 证书、强制 HTTPS 都在这里
   │  反向代理到 127.0.0.1:8090（仅服务器内部）
   ▼
┌─────────────────────────────────────────────────────┐
│              Docker（宝塔安装的容器引擎）              │
│                                                     │
│  frontend 容器  (8090)  ← 前端页面 + /api 转发        │
│       │                                             │
│       ▼                                             │
│  backend 容器   (8123)  ← Java 后端 /api             │
│       │                                             │
│       ├──▶ mcp-server 容器 (8127)  图片搜索工具服务   │
│       └──▶ postgres 容器   (5432)  PostgreSQL+pgvector│
└─────────────────────────────────────────────────────┘
```

**几个关键结论（记住即可）：**

1. **外网只能通过 80/443 访问**，8123、8127、5432、8090 都只在服务器内部通信，安全组绝不放行它们。
2. 前端、后端、MCP、数据库 4 个服务全部跑在 Docker 容器里，一条 `docker compose up -d` 全部启动，开机自动重启。
3. 镜像在你的 Windows 电脑上构建好再上传服务器 —— 2G 内存的服务器带不动 Maven 构建，这样最稳。
4. 数据库表结构由程序启动时自动创建（Flyway 迁移），无需手动建表。
5. AI 密钥（通义千问/DeepSeek/OSS 等）已打包进后端镜像，不需要你配置任何 API Key。
6. 唯一的敏感配置是**数据库密码**，由构建脚本自动生成随机强密码并写入 `.env`。

**项目在服务器上的目录规划：**

```
/www/wwwroot/lianba-ai-agent/          ← 部署目录
├── docker-compose.prod.yml            容器编排文件
├── .env                               数据库密码配置
├── install.sh                         一键安装脚本
├── lianba-ai-agent-images.tar         镜像包（≈1.5GB）
└── tmp/                               运行中产生的解析文件（自动创建，需要备份）
```

---

## 1. 准备阶段（服务器 + 宝塔环境）

### 1.1 准备服务器

如果你已经有服务器（2核2G 及以上，Ubuntu 22.04 / Alibaba Cloud Linux 3 / CentOS 系均可），跳到 1.2。

如还未购买，建议（以阿里云购买页为准）：

| 项目 | 建议 |
|------|------|
| 产品 | 轻量应用服务器 或 ECS 通用型 |
| 配置 | 2核2G 起步（本项目已针对 2G 做内存优化）；预算允许直接上 2核4G |
| 系统盘 | 40G 以上 |
| 地域 | 离你的用户近的（如 华东1-杭州） |
| 镜像 | 宝塔面板镜像（购买时可直接选）或纯净 Ubuntu 22.04 |

**阿里云安全组 / 防火墙放行规则**（在阿里云控制台「安全组」里配置入方向）：

| 端口 | 用途 | 是否必须 |
|------|------|---------|
| 22 | SSH 远程登录 | 必须 |
| 8888 | 宝塔面板（你可以在宝塔里改成其它端口） | 必须 |
| 80 | HTTP | 必须 |
| 443 | HTTPS | 必须 |
| 8090 | 临时验收用（第 6 章验收完就删掉这条规则） | 临时 |

> ⚠️ 8123 / 8127 / 5432 **永远不要**放行，它们是内网服务端口。

### 1.2 安装宝塔面板

1. 用 SSH 工具登录服务器（Windows 自带命令：打开 PowerShell，输入 `ssh root@你的服务器公网IP`，回车后输入密码）。
   - 如果觉得命令行难用，推荐安装免费的图形化工具 **FinalShell** 或 **XShell**。
2. 执行宝塔官方安装命令（**以宝塔官网 www.bt.cn 首页最新命令为准**，Ubuntu 示例）：
   ```bash
   wget -O install.sh https://download.bt.cn/install/install-ubuntu_6.0.sh && bash install.sh ed8484bec
   ```
   CentOS / Alibaba Cloud Linux 系则用官网对应的 `install_6.0.sh` 命令。
3. 安装完成后，屏幕上会显示**面板地址、用户名、密码**，务必先复制保存。
4. 浏览器打开面板地址 → 首次进入通常需要绑定宝塔账号（免费注册一个即可）。
5. 进入面板后，按提示安装推荐套件（选 **Nginx**，其它 PHP/MySQL 可以不装，节省内存）。

### 1.3 在宝塔里安装 Docker

1. 宝塔面板左侧菜单 → **Docker**（或 软件商店 → 搜索"Docker管理器"）。
2. 点击安装，等待完成（约 1-3 分钟）。
3. 验证：打开宝塔的「终端」，输入：
   ```bash
   docker --version && docker compose version
   ```
   两行都输出版本号即为成功（旧版宝塔 Docker 管理器可能只有 `docker-compose --version`，也可以，install.sh 脚本会自动兼容）。

### 1.4 创建 SWAP 交换分区（2G 内存必做，4G 可跳过）

作用：内存不够时把部分数据挪到磁盘上，防止服务被杀掉。宝塔终端里执行：

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
```

验证：执行 `free -h`，应看到 Swap 一行有 2.0Gi。

### 1.5 域名解析与备案（中国大陆服务器必须）

1. 到域名注册商（阿里云/腾讯云等）添加解析：记录类型 `A`，主机记录填你的前缀（如 `love`），记录值填**服务器公网 IP**。
2. **备案检查**：中国大陆的服务器，域名没有 ICP 备案的话，访问会被拦截（打不开或提示未备案）。
   - 没备案：立刻在阿里云「备案」服务里提交（一般 7~20 个工作日）。
   - 未备案期间：可以先跳过第 5 章，用 `http://服务器IP:8090` 完成验收（需临时放行安全组 8090）。

---

## 2. 在本地电脑构建镜像（Windows）

> 为什么不在服务器上构建？服务器只有 2G 内存，Maven 构建极易内存溢出。本地构建好再上传是最稳妥的方式。

### 2.1 前置条件

- 本机已安装 **Docker Desktop** 并处于运行状态（任务栏小鲸鱼图标不转圈即正常）。
- 本项目代码目录：`g:\IdeaProjects\lianba-ai-agent`
- 已配置 Docker 镜像加速器（本项目调试时已自动配置好 daocloud/1ms/rat 三个加速源；若以后换电脑，参考第 8.8 节重新配置）。

### 2.2 一键构建（推荐）

打开 PowerShell，执行：

```powershell
cd g:\IdeaProjects\lianba-ai-agent
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\build-images.ps1
```

脚本会自动完成：

1. 环境检查（Docker 是否运行）
2. 自动生成 `.env`（随机 24 位数据库密码，已存在则复用）
3. 构建 前端 / 后端 / MCP 三个镜像（首次约 10~30 分钟，取决于网络）
4. 导出发布包到 `deploy\release\` 目录

**构建成功后的产物（4 个文件）：**

```
deploy\release\
├── lianba-ai-agent-images.tar    镜像包（约 1.2~1.6GB）
├── docker-compose.prod.yml       生产编排文件
├── .env                          数据库密码配置
└── install.sh                    服务器端一键安装脚本
```

### 2.3 构建常见报错

| 报错关键字 | 原因 | 处理 |
|-----------|------|------|
| `failed to resolve reference` / `dial tcp ... timeout` | Docker Hub 网络不通 | 重新配置镜像加速器（见 8.8 节），重启 Docker Desktop 后重试 |
| `no space left on device` | 本机 Docker 磁盘不足 | Docker Desktop → Settings → Resources 增大磁盘，并清理无用镜像 |
| Maven 下载超时 | 依赖下载网络抖动 | 重跑一次脚本即可（已下载的依赖有缓存） |

---

## 3. 上传发布包到服务器

把 `deploy\release\` 里的 **4 个文件**上传到服务器的 `/www/wwwroot/lianba-ai-agent/`。

### 方式 A：WinSCP（推荐，免费且稳定）

1. 下载安装 WinSCP（winscp.net）。
2. 新建连接：协议 `SFTP`，主机名=服务器公网 IP，端口 22，用户名 `root`，密码=服务器密码。
3. 登录后，右侧定位到 `/www/wwwroot/`，右键新建目录 `lianba-ai-agent`。
4. 把本地 4 个文件拖进去，等待传输完成（1.5GB 左右，网速 1MB/s 约需 25 分钟，请耐心等待）。
   - 传输大文件时不要中途断开，WinSCP 支持断点续传。

### 方式 B：宝塔文件管理器

1. 宝塔面板 → 文件 → 进入 `/www/wwwroot/`，新建目录 `lianba-ai-agent`。
2. 进入目录 → 上传 → 选择 4 个文件。
3. 如果提示"文件过大"上传失败，说明面板有上传大小限制，改用方式 A（WinSCP）。

### 上传完成后的验证

宝塔终端执行：

```bash
ls -lh /www/wwwroot/lianba-ai-agent/
```

应看到 4 个文件，其中 `lianba-ai-agent-images.tar` 大小 1GB 以上。

> `.env` 是隐藏文件（以点开头），`ls -l` 不显示它时用 `ls -la` 查看。

---

## 4. 一键部署（服务器端）

在宝塔「终端」里执行：

```bash
cd /www/wwwroot/lianba-ai-agent
bash install.sh
```

脚本会自动完成：检查环境 → 导入镜像（1~3 分钟）→ 按顺序启动 4 个容器 → 轮询健康检查。

**成功标志**（脚本最后输出）：

```
  安装成功！后端健康检查已通过。
```

同时会列出 4 个容器的状态（`docker compose ps`），STATUS 一栏应全部为 `Up` 或 `Up (healthy)`。

> 后端是 Java 应用，首次启动需 30~90 秒属正常；健康检查脚本最多等待 150 秒。

**如果脚本报错失败**，按输出提示执行：

```bash
docker compose -f docker-compose.prod.yml logs --tail=100 backend   # 后端日志
docker compose -f docker-compose.prod.yml logs --tail=50 mcp-server # MCP 日志
free -h                                                             # 内存检查
```

把报错内容反馈给技术同学。

**备用方案（脚本万一无法运行时，逐条手动执行）：**

```bash
cd /www/wwwroot/lianba-ai-agent
docker load -i lianba-ai-agent-images.tar
mkdir -p tmp
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
curl http://127.0.0.1:8090/api/health
```

最后一条命令返回含 `"ok"` 的 JSON 即部署成功。

---

## 5. 宝塔建站 + HTTPS（网站入口）

### 5.1 添加站点

1. 宝塔面板 → 网站 → 添加站点：
   - 域名：填你的域名（如 `love.example.com`）
   - 根目录：默认即可（用不到，前端由 Docker 容器提供）
   - PHP 版本：**纯静态**
   - 数据库：不创建
2. 提交后站点列表会出现你的域名。

### 5.2 先申请 SSL 证书（顺序很重要，先证书后反代）

1. 站点右侧「设置」→「SSL」→「Let's Encrypt」。
2. 勾选你的域名 → 申请（免费，自动续期）。申请成功后先**不要**开强制 HTTPS。
3. 若申请失败提示验证通不过，多半是域名解析未生效或未备案，先处理解析/备案。

### 5.3 配置反向代理（修改站点配置文件）

站点「设置」→「配置文件」，找到 `server { ... }` 块，按下面三步改：

**第 1 步：删除宝塔自带的静态资源缓存配置块**（这两个块会让静态请求 404 而不是转发给容器，必须删掉）：

```nginx
    # 找到并删除类似下面这两段（内容可能略有差异，以你的实际配置为准）
    location ~ .*\.(gif|jpg|jpeg|png|bmp|swf)$
    {
        expires      30d;
        error_log /dev/null;
        access_log off;
    }

    location ~ .*\.(js|css)?$
    {
        expires      12h;
        error_log /dev/null;
        access_log off;
    }
```

**第 2 步：粘贴反代配置**（放在 server 块内）：

```nginx
    # ======== 恋吧 AI 反向代理配置（整段粘贴）========
    # 证书自动续期验证目录（必须保留，否则 90 天后证书续期会失败）
    # 注意：下面的 root 要改成你站点的真实根目录 ——
    # 在配置文件靠上的位置找 "root /www/wwwroot/xxx;" 那一行，把路径抄过来
    location ^~ /.well-known/acme-challenge/ {
        root /www/wwwroot/你的域名根目录;
        allow all;
    }

    # 所有请求转发给前端容器
    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # ↓↓↓ SSE 流式输出必须的配置（AI 回复是否能逐字显示就靠它）↓↓↓
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;

        # 文件上传大小限制
        client_max_body_size 50m;
    }
    # ======== 反代配置结束 ========
```

保存后宝塔会自动重载 Nginx（若没有，点「服务」→ Nginx 重载）。

**第 3 步：开启强制 HTTPS**：SSL → 右上角「强制 HTTPS」打开。

### 5.4 检查

宝塔终端执行：

```bash
nginx -t
```

输出 `syntax is ok` 和 `test is successful` 即配置无误。

---

## 6. 验收测试（逐项打勾）

浏览器打开 `https://你的域名`，逐项测试：

| # | 测试项 | 预期结果 | 说明 |
|---|--------|---------|------|
| 1 | 打开首页 | 页面正常显示，地址栏是小锁标志 | 打不开→第 8.1 节 |
| 2 | 注册账号 | 能收到邮箱验证码（163 邮箱发出，检查垃圾箱） | 不收到→第 8.5 节 |
| 3 | 登录 | 登录成功并跳转 | 反复跳回登录页→第 8.4 节 |
| 4 | AI 对话 | 回复**逐字流出**（不是转圈很久后整段出现） | 卡住不出字→第 8.3 节 |
| 5 | 图片搜索 | 在对话里让 AI 搜图，能返回图片 | 失败→第 8.6 节 |
| 6 | 文档上传解析 | 上传 PDF/TXT 能解析出内容 | RAG 解析为长任务，耐心等待 |
| 7 | 报告导出 | 能下载 PDF/图片报告 | 失败→检查容器日志 |
| 8 | 刷新页面 | 各页面 F5 不出现 404 | 出现 404→第 8.2 节 |

**验收通过后**：到阿里云安全组删除 8090 临时放行规则（如果是通过 IP:8090 验收的话）。

---

## 7. 日常运维

### 7.1 常用命令（宝塔终端执行，先 cd 到部署目录）

```bash
cd /www/wwwroot/lianba-ai-agent

# 查看 4 个容器状态
docker compose -f docker-compose.prod.yml ps

# 看后端实时日志（Ctrl+C 退出）
docker compose -f docker-compose.prod.yml logs -f --tail=100 backend

# 重启某一个服务（如后端）
docker compose -f docker-compose.prod.yml restart backend

# 停止全部服务
docker compose -f docker-compose.prod.yml down

# 重新启动全部服务
docker compose -f docker-compose.prod.yml up -d

# 查看内存/磁盘占用
free -h && df -h && docker stats --no-stream
```

> 所有容器都配置了 `restart: unless-stopped`，服务器重启后会自动拉起，不需要手动操作。

### 7.2 数据库备份（建议每周一次）

```bash
cd /www/wwwroot/lianba-ai-agent
mkdir -p backup
docker exec lianba-ai-agent-postgres pg_dump -U shenzhou_ruofeng lianba_ai_agent | gzip > backup/db_$(date +%Y%m%d).sql.gz
```

自动定时备份：宝塔面板 → 计划任务 → 添加 Shell 脚本任务，把上面命令写进去，选每周执行。

`tmp/` 目录（用户上传的解析产物）也需要备份：

```bash
tar -czf backup/tmp_$(date +%Y%m%d).tar.gz tmp/
```

### 7.3 更新版本（以后改了代码怎么上线）

1. 本地电脑重新执行一次构建脚本：
   ```powershell
   cd g:\IdeaProjects\lianba-ai-agent
   powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\build-images.ps1
   ```
   （`.env` 会复用，数据库密码不变，数据不丢。）
2. 只上传新的 `lianba-ai-agent-images.tar` 覆盖服务器上的旧文件。
3. 服务器执行：
   ```bash
   cd /www/wwwroot/lianba-ai-agent
   docker load -i lianba-ai-agent-images.tar
   docker compose -f docker-compose.prod.yml up -d
   ```
   镜像更新后 compose 会自动重建有变化的容器，数据库数据保留在 `pgdata` 数据卷中，不受影响。

---

## 8. 常见故障排查

### 8.1 域名打不开 / 显示无法访问

按顺序检查：

1. 域名解析是否生效：本地电脑执行 `ping 你的域名`，看返回的 IP 是否是服务器 IP。
2. 阿里云安全组是否放行 80/443。
3. 宝塔站点是否配置正确：终端执行 `nginx -t`，再执行 `curl -I http://127.0.0.1:8090` 看前端容器是否活着。
4. 容器状态：`docker compose -f docker-compose.prod.yml ps`，若 frontend 没 Up，看它的日志。

### 8.2 刷新页面 404

说明 Nginx 的 `location /` 反代没生效（多半是没删除宝塔自带的静态资源缓存块）。回到第 5.3 节重新检查配置。

### 8.3 AI 回复卡住不输出 / 整段一次性出现

SSE 流式配置被 Nginx 缓冲了。检查第 5.3 节的配置：

- `proxy_buffering off;` 是否写了
- `proxy_set_header Connection "";` 是否写了
- 修改后必须重载 Nginx

### 8.4 登录后刷新又变未登录（或反复跳登录页）

- 确认访问的是 `https://`（HTTPS），而不是 http。Cookie 在跨协议下不生效。
- 第 5.3 节里的 `proxy_set_header X-Forwarded-Proto $scheme;` 不能漏。
- 「强制 HTTPS」记得开启。

### 8.5 注册收不到验证码邮件

邮箱服务配置在镜像内的 `application-prod.yml`（163 邮箱 SMTP），部署后为真实发信。检查：

1. 邮箱的「垃圾邮件」文件夹；
2. 后端日志里搜 `Mail`/`SMTP` 报错：`docker compose -f docker-compose.prod.yml logs --tail=200 backend | grep -i mail`

### 8.6 图片搜索 / 部分工具调用失败

- 先看 MCP 容器状态：`docker compose -f docker-compose.prod.yml ps`，mcp-server 应为 `Up (healthy)`。
- 看 MCP 日志：`docker compose -f docker-compose.prod.yml logs --tail=100 mcp-server`。
- MCP 未就绪时后端启动也会失败，重启后端：`docker compose -f docker-compose.prod.yml restart backend`。

### 8.7 内存不足（服务突然不可用 / 容器反复重启）

现象：`docker compose ps` 里容器 STATUS 反复 `Restarting`，日志有 `OutOfMemory` 或进程被杀。

处理：

1. 确认 SWAP 已建：`free -h`（没有就回 1.4 节）。
2. 看谁吃内存：`docker stats --no-stream`。
3. 已内置的优化（`docker-compose.prod.yml` 里）：后端 `-Xmx512m`、MCP `-Xmx192m`、PostgreSQL `shared_buffers=96MB`。如果仍旧吃紧，把宝塔面板中不用的软件（phpMyAdmin、MySQL、PHP 等多版本环境）卸载或停掉。
4. 终极方案：升级服务器内存到 4G。

### 8.8 Docker 拉取镜像超时（换电脑/重装系统时需要）

1. 新建/编辑文件 `C:\Users\你的用户名\.docker\daemon.json`，写入：

   ```json
   {
     "registry-mirrors": [
       "https://docker.m.daocloud.io",
       "https://docker.1ms.run",
       "https://hub.rat.dev"
     ]
   }
   ```
   （若文件已有内容，把 `registry-mirrors` 段合并进去，别覆盖原有配置。）
2. Docker Desktop → 右下角齿轮 Settings → Docker Engine → 确认为上述内容 → Apply & Restart。
3. 等待引擎重启后验证：`docker info` 输出里能看到 `Registry Mirrors` 列表。

### 8.9 磁盘空间不足

```bash
df -h                                # 看占用
docker system df                     # 看 Docker 占用
docker image prune -f                # 清理无用镜像层（安全）
```

`lianba-ai-agent-images.tar` 导入后可以删除释放 1.5GB：`rm lianba-ai-agent-images.tar`（下次更新再传即可）。

---

## 9. 安全加固清单（部署完成后逐项确认）

- [ ] 阿里云安全组只放行：22、宝塔面板端口、80、443（删除临时的 8090）
- [ ] 宝塔面板修改默认端口与复杂密码，开启面板 SSL
- [ ] SSH 建议改用密钥登录，禁用密码登录（宝塔「安全」页可配置）
- [ ] 本项目数据库密码是构建时自动生成的 24 位随机密码（在 `.env` 中），请勿外传
- [ ] `.env` 文件中含数据库密码，注意不要分享给他人
- [ ] 宝塔计划任务：每周数据库备份（见 7.2）
- [ ] Linux 系统开启自动安全更新（宝塔「安全」页有一键设置）

---

## 10. 已知限制与后续升级

| 项 | 现状 | 后续升级 |
|----|------|---------|
| 语音输入 | 前端默认连接 `ws://127.0.0.1:10095`（用户本机端口），线上暂不可用；不影响其它功能 | 需要额外部署 faster-whisper 服务（需 ≥2G 额外内存），并用 wss 反代后重新构建前端 |
| 单机部署 | 全部服务跑在一台 2G 服务器上 | 用户量增长后可拆分：数据库换阿里云 RDS、接入 CDN 加速静态资源 |
| 镜像分发 | 本地构建 tar 上传 | 团队协作时可开通阿里云容器镜像服务 ACR，改为 push/pull 流程 |

---

## 附录 A：部署涉及的文件清单

| 文件 | 作用 |
|------|------|
| `docker-compose.prod.yml` | 生产环境容器编排（端口绑定 127.0.0.1、内存限制、时区、健康检查） |
| `deploy/build-images.ps1` | 本地一键构建 + 导出发布包脚本 |
| `deploy/install.sh` | 服务器端一键安装脚本（导入镜像、启动、健康检查） |
| `deploy/release/` | 构建产物目录（tar 镜像包 + compose + .env + install.sh） |
| `.env`（自动生成） | 唯一的敏感配置文件：数据库名/用户/密码 |

## 附录 B：端口对照表

| 端口 | 服务 | 暴露范围 |
|------|------|---------|
| 80 / 443 | 宝塔 Nginx（唯一公网入口） | 公网 |
| 8090 | 前端容器 | 仅 127.0.0.1 |
| 8123 | 后端容器 | 仅容器网络 |
| 8127 | MCP 容器 | 仅容器网络 |
| 5432 | PostgreSQL 容器 | 仅容器网络 |
