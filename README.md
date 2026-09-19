# 恋吧 AI 智能体（lianba-ai-agent）

> 我是若风。lianba-ai-agent 是我从 0 到 1 开发并持续迭代的全栈 AI 应用——一个面向情感陪伴场景的 **AI 智能体平台**。
> 它以 Spring AI 为内核，把「多轮对话 + RAG 知识库 + 工具调用 + MCP 服务 + 自主规划智能体」串成了一条完整的产品链路，并配有独立的 Vue 3 前端、图片搜索 MCP 服务与本地语音识别服务。
> 这份 README 会带你把这个项目完整跑起来。

---

## 一、项目简介

**恋吧 AI 智能体（lianba-ai-agent）** 是一套基于 **Spring Boot 3 + Spring AI** 的全栈 AI 智能体应用，核心业务场景是「情感陪伴」：

- 用户可以与 **AI 恋爱大师** 进行多轮对话，获得情感咨询与约会建议；
- 可以开启 **恋爱匹配模式**，由 AI 扮演匹配对象进行模拟交流；
- 可以一键生成 **恋爱报告**，回顾与总结情感经历；
- 可以记录 **情感日记**，由 AI 每日生成专属建议；
- 可以浏览 **社区** 中的帖子与评论，共享情感话题。

在业务之外，项目完整实践了 AI 应用的主流工程化能力：**SSE 流式输出、RAG 混合检索（向量 + 全文）、多模态理解、工具调用、MCP 服务集成、自主规划智能体（Manus）、语音输入**，是一套可直接部署上线的 AI 应用模板。

## 二、核心功能特性

| 功能模块 | 说明 | 核心实现 |
| --- | --- | --- |
| 恋爱大师对话 | 多轮流式对话、对话记忆持久化、支持 RAG 知识库增强、工具调用、多模态（图片）输入 | `LoveApp`、`AiController` 系列 SSE 接口 |
| 恋爱匹配模式 | 切换为「匹配对象」人设进行模拟聊天，流式输出 | `/ai/love_app/match/sse` |
| 恋爱报告 | AI 生成结构化恋爱报告，支持历史列表、详情查看与导出 | `LoveReportService`、`LoveReportExportService` |
| 情感日记 | 日记增删查、单篇 AI 分析、每日建议定时生成 | `DiaryController`、`DailyAdviceTask` |
| 社区互动 | 帖子发布与评论互动 | `CommunityController` |
| RAG 知识库 | 文档导入 → 解析确认 → 入库；向量语义检索 + 关键词全文检索的混合召回（RRF 融合，可选启用 PostgreSQL 全文检索增强） | `rag/`、`rag/hybrid/` |
| 智能体 Manus | 自主规划智能体：多轮「思考-行动」循环、任务状态入库、支持人工介入（AskHuman）与手动停止 | `agent/`、`/ai/manus/*` |
| 工具调用 | 联网搜索、网页抓取、文件操作、资源下载、终端操作、PDF 生成、图片生成、邮件发送、数据库操作、时间获取、人机问答、终止 | `tools/` 共 12 个工具 |
| MCP 服务 | 独立部署的图片搜索 MCP 服务（Pexels），支持 SSE / stdio 两种接入 | `image-search-mcp-server/` |
| 语音输入 | 前端录音 → 本地语音识别 WebSocket 服务 → 文本回填输入框 | `useVoiceInput.js`、`faster-whisper-server/` |
| 文档 / 图片上传 | 文档解析（RAG 导入链路）、图片上传与解析；接入阿里云 OSS，未配置时自动降级为本地存储 | `ImageUploadController`、`RagImportController`、`OssManager` |
| 用户体系 | 注册（邮件验证码，未配置 SMTP 时降级）、登录、个人信息、关系时间线 | `UserController`、`LoginAuthInterceptor` |
| 会员与用量 | 会员信息、AI 用量统计记录 | `MembershipController`、`UsageStatisticsService` |

## 三、技术栈

### 后端（主服务）

| 技术 | 版本 / 说明 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 3.4.4 |
| Spring AI | 1.0.0（Spring AI Alibaba BOM 1.0.0.2） |
| 大模型接入 | 阿里云百炼 DashScope（`spring-ai-alibaba-starter-dashscope`）、DeepSeek（OpenAI 兼容 API，多模态理解 + 深度思考）、Ollama（本地模型） |
| LangChain4j | `langchain4j-community-dashscope` 1.0.0-beta2 |
| 数据库 | PostgreSQL + pgvector（向量存储 `vector_store` 表） |
| ORM / 迁移 | MyBatis-Plus 3.5.9、Flyway（`db/migration/V1~V6`） |
| 流式通信 | SSE（`Flux<String>` / `SseEmitter`） |
| 工具库 | Kryo 5.6.2（对话记忆序列化）、Jsoup 1.19.1、Apache POI 5.4.0、iText 9.1.0、Hutool 5.8.37、Guava Retrying 2.0.0、阿里云 OSS SDK 3.18.1 |
| 接口文档 | Knife4j 4.4.0（`/api/doc.html`） |

### 前端（lianba-ai-agent-frontend）

| 技术 | 版本 / 说明 |
| --- | --- |
| Vue | 3.2.47（组合式 API） |
| 构建工具 | Vite 4.3.9（开发端口 3000） |
| 路由 / 请求 | vue-router 4.1.6、Axios ^1.3.6（携带 Cookie，Session 登录态） |
| SEO | @vueuse/head |
| 页面 | 首页、登录、恋爱大师、报告历史、情感日记、社区、导出、个人中心 |

### 配套服务

| 服务 | 技术 | 说明 |
| --- | --- | --- |
| image-search-mcp-server | Spring Boot 3.4.5 + Spring AI MCP Server（`spring-ai-starter-mcp-server-webmvc`） | 图片搜索 MCP 服务，默认 SSE 模式，端口 8127 |
| faster-whisper-server | Python + faster-whisper + WebSocket | 本地语音识别服务，端口 10095，协议兼容前端 FunASR 2pass 简化版 |

### 部署

Docker / Docker Compose 多阶段构建（JDK 21 运行镜像）+ Nginx + 宝塔面板（详见「九、部署说明」）。

## 四、工程结构

```text
lianba-ai-agent/
├── src/
│   ├── main/
│   │   ├── java/com/lianba/aiagent/
│   │   │   ├── AiAgentApplication.java    # Spring Boot 启动类
│   │   │   ├── advisor/                   # 自定义 Advisor（调用日志、重读增强）
│   │   │   ├── agent/                     # 自主规划智能体
│   │   │   │   ├── BaseAgent.java         #   智能体基类（思考-行动循环、步骤回调）
│   │   │   │   ├── ReActAgent.java        #   ReAct 模式抽象
│   │   │   │   ├── ToolCallAgent.java     #   工具调用型智能体
│   │   │   │   ├── Manus.java             #   超级智能体（全能工具集）
│   │   │   │   ├── AgentRunListener.java  #   运行事件监听
│   │   │   │   ├── interaction/           #   人机交互（AskHuman 回复注册表）
│   │   │   │   └── model/                 #   智能体状态、任务与任务状态模型
│   │   │   ├── app/                       # 恋爱大师应用核心（LoveApp、恋爱报告模型）
│   │   │   ├── chatmemory/                # 对话记忆持久化（文件 Kryo / 数据库两种实现）
│   │   │   ├── common/                    # 统一响应 BaseResponse、重试工具
│   │   │   ├── config/                    # 全局配置（CORS、登录拦截、MyBatis-Plus、OSS、异步、定时任务）
│   │   │   ├── constant/                  # 常量定义
│   │   │   ├── controller/                # 接口层（REST + SSE，共 11 个 Controller）
│   │   │   ├── demo/                      # 示例代码（大模型多种接入方式、RAG demo）
│   │   │   ├── exception/                 # 业务异常、错误码与全局异常处理
│   │   │   ├── manager/                   # OSS 文件管理器
│   │   │   ├── mapper/                    # MyBatis-Plus Mapper（13 个）
│   │   │   ├── model/                     # entity（实体）/ dto / vo
│   │   │   ├── rag/                       # RAG：文档加载、向量库、查询增强
│   │   │   │   └── hybrid/                #   向量 + 全文混合检索（RRF 融合、元信息过滤）
│   │   │   ├── service/                   # 业务服务层（11 个 Service）
│   │   │   ├── task/                      # 定时任务（每日情感建议生成）
│   │   │   └── tools/                     # 供 AI 调用的工具集（12 个工具 + 注册器）
│   │   └── resources/
│   │       ├── application.yml            # 主配置（本地开发默认值）
│   │       ├── application-prod.yml       # 生产配置（含真实密钥，不入库，需自行创建）
│   │       ├── mcp-servers.json           # MCP stdio 配置（不入库，需自行创建）
│   │       ├── db/migration/              # Flyway 迁移脚本 V1~V6（用户/会话/任务/日记/会员/社区等表）
│   │       └── document/                  # RAG 知识库原始 Markdown 文档（恋爱问答语料）
│   └── test/java/com/lianba/aiagent/      # 单元测试（智能体、RAG、OSS 等）
├── lianba-ai-agent-frontend/               # Vue 3 前端工程
│   └── src/
│       ├── api/index.js                   # Axios 封装、SSE 连接封装、登录态拦截
│       ├── components/                    # 通用组件（头尾、对话房间、会话侧边栏等）
│       ├── composables/                   # useAuth / useSessionStore / useVoiceInput
│       ├── router/index.js                # 路由与登录守卫
│       └── views/                         # 页面（Home / LoveMaster / Diary / Community / ...）
├── image-search-mcp-server/                # 图片搜索 MCP 服务（Spring AI MCP Server）
│   └── src/main/resources/
│       ├── application.yml.example        #   配置模板（复制为 application.yml 并填 Pexels Key）
│       ├── application-sse.yml            #   SSE 模式配置
│       └── application-stdio.yml          #   stdio 模式配置
├── faster-whisper-server/                  # 本地语音识别 WebSocket 服务（Python）
│   ├── server.py                          #   服务实现（默认 ws://127.0.0.1:10095）
│   ├── run-whisper.ps1                    #   Windows 启动脚本
│   └── test_client.py                     #   协议链路测试客户端
├── deploy/                                 # 部署产物与文档
│   ├── 部署说明.md                         #   宝塔面板手动部署说明
│   ├── 阿里云宝塔Docker部署手册.md          #   阿里云 + 宝塔 + Docker 全流程部署手册
│   ├── build-images.ps1                   #   Windows 本地构建镜像脚本
│   ├── install.sh                         #   服务器端一键安装脚本
│   └── nginx-baota.conf                   #   宝塔 Nginx 反向代理配置
├── docs/                                   # 设计文档
├── docker-compose.yml                      # 开发/演示环境编排（4 容器一键启动）
├── docker-compose.prod.yml                 # 生产环境编排（仅绑定回环地址 + 内存优化）
├── Dockerfile                              # 后端镜像（maven 构建 + amazoncorretto:21 运行）
├── start-all-complete.ps1                  # 一键启动全部四服务（MCP + 语音 + 后端 + 前端）
├── start-all-services.ps1                  # 启动三服务（语音 + 后端 + 前端，MCP 需先手动启动）
├── start-mcp-server.ps1                    # 单独启动 MCP 图片搜索服务
├── stop-all-services.ps1                   # 停止全部服务
├── pom.xml / mvnw / mvnw.cmd               # Maven 构建配置与包装器
├── settings-override.xml                   # Maven 镜像设置（可选）
└── .env.example                            # 部署环境变量模板
```

> 提示：`start-*.ps1` 脚本内置了作者本机的 JDK 与项目绝对路径，换机器使用前请先修改脚本中的路径。

## 五、快速开始

### 5.1 环境要求

| 依赖 | 版本要求 | 用途 |
| --- | --- | --- |
| JDK | **21**（强要求） | 后端与 MCP 服务编译、运行 |
| Maven | 3.8+（或使用项目自带 `mvnw`） | 后端构建 |
| Node.js | 18+ | 前端开发与构建 |
| Python | 3.10+ | 语音识别服务 |
| PostgreSQL | 15+（必须启用 pgvector 扩展） | 业务数据库 + 向量存储 |
| Docker（可选） | 20+ / Compose V2 | 容器化一键启动与部署 |

### 5.2 第一步：准备数据库

启动一个带 pgvector 扩展的 PostgreSQL（推荐直接用官方镜像）：

```bash
docker run -d --name lianba-postgres \
  -p 5432:5432 \
  -e POSTGRES_DB=lianba_ai_agent \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=your-db-password \
  pgvector/pgvector:pg16
```

- 若使用已有 PostgreSQL，需先安装 pgvector 插件（`vector_store` 向量表与扩展由应用启动时自动创建）；
- **业务表由 Flyway 自动迁移**：应用启动时会执行 `db/migration/` 下的 `V1~V6` 脚本，无需手动建表；
- 本地开发默认连接 `jdbc:postgresql://localhost:5432/lianba_ai_agent`（用户名、密码在 `src/main/resources/application.yml` 中配置）。

### 5.3 第二步：创建必要的配置文件

仓库中**不包含任何真实密钥**，以下文件被 `.gitignore` 排除，需要你自行创建：

| 文件 | 是否必须 | 创建方式 |
| --- | --- | --- |
| `image-search-mcp-server/src/main/resources/application.yml` | **必须**（否则 MCP 服务缺少端口与 Key 配置） | 复制同目录 `application.yml.example`，填入自己的 Pexels API Key（[申请地址](https://www.pexels.com/api/)） |
| `src/main/resources/application-prod.yml` | 本地开发可选；生产/Docker 建议创建 | 参考「六、配置说明」逐项填写；本地开发也可直接用环境变量注入密钥 |
| `.env` | Docker 部署时使用 | 复制 `.env.example`，填写数据库密码等 |
| `src/main/resources/mcp-servers.json` | 选填（启用 MCP stdio 接入时需要） | 按 Spring AI MCP stdio 规范自行编写 |

### 5.4 方式一：本地开发运行

推荐使用项目提供的 PowerShell 一键脚本（Windows）：

```powershell
# 一键启动全部四服务（MCP → 语音识别 → 后端 → 前端）
.\start-all-complete.ps1
```

也可以按依赖顺序手动分步启动：

```powershell
# 1. 启动 MCP 图片搜索服务（必须先于后端启动，端口 8127）
.\start-mcp-server.ps1
#    （若 jar 未打包，可进入目录用 Maven 直接运行：cd image-search-mcp-server; .\mvnw.cmd spring-boot:run）

# 2. 启动语音识别服务（端口 10095）
.\faster-whisper-server\run-whisper.ps1

# 3. 启动后端（默认 local profile，端口 8123）
.\mvnw.cmd spring-boot:run

# 4. 启动前端（端口 3000）
cd lianba-ai-agent-frontend
npm install
npm run dev
```

启动完成后访问：**http://localhost:3000**

停止全部服务：

```powershell
.\stop-all-services.ps1
```

### 5.5 方式二：Docker Compose 一键启动

适合快速体验与服务器部署，四个容器（PostgreSQL + MCP + 后端 + 前端）编排在 `docker-compose.yml` 中：

```bash
# 1. 准备环境变量（数据库密码等）
cp .env.example .env
#    编辑 .env，至少填写 DB_PASSWORD

# 2. 构建并启动全部服务
docker compose up -d --build
```

启动完成后访问：**http://localhost**（前端容器映射 80 端口）。

> 注意事项：
> - 构建 MCP 镜像前，请确认本地已按 5.3 节创建 `image-search-mcp-server/src/main/resources/application.yml`（该文件会被打包进镜像）；
> - 构建后端镜像时如未创建 `application-prod.yml`，需在 `docker-compose.yml` 的 `backend.environment` 中自行注入 AI 密钥（`DASHSCOPE_API_KEY` 等）；
> - 生产部署请使用 `docker-compose.prod.yml`，详见「九、部署说明」。

## 六、配置说明

### 6.1 后端环境变量与配置项

| 配置项 / 环境变量 | 用途 | 是否必填 | 获取方式 |
| --- | --- | --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼（DashScope）密钥：对话模型（qwen-plus）、向量化、万相图片生成共用 | AI 功能必填 | [阿里云百炼控制台](https://bailian.console.aliyun.com/)创建 API Key |
| `DEEPSEEK_API_KEY` | DeepSeek 密钥：图文多模态理解 + 深度思考推理 | 使用相关功能时必填 | [DeepSeek 开放平台](https://platform.deepseek.com/)创建 |
| `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | DeepSeek 接口地址与模型名（默认 `https://api.deepseek.com/v1` / `deepseek-flash`） | 否 | 按需覆盖 |
| `SEARCH_API_KEY` | SearchAPI 联网搜索工具 | 使用联网搜索时必填 | [searchapi.io](https://www.searchapi.io/)申请 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 生产环境数据库连接（prod profile 使用） | 生产必填 | 你的数据库实例信息 |
| `MCP_SERVER1_URL` | 后端 MCP 客户端对接的图片搜索服务地址 | 启用 MCP 接入时配置 | Docker 编排默认 `http://mcp-server:8127`；本地开发用 `http://localhost:8127` |
| `OSS_ENDPOINT` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET` / `OSS_HOST` | 阿里云 OSS 对象存储（文件/图片/导出产物） | 否，不配置时自动降级为仅本地存储 | [阿里云 RAM 控制台](https://ram.console.aliyun.com/)创建 AccessKey |
| `spring.mail.*`（对应 `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` 等环境变量） | 注册验证码邮件发送 | 否，未配置时自动降级为接口直接返回验证码（便于本地开发） | 邮箱服务商 SMTP 授权码 |
| `SPRING_PROFILES_ACTIVE` | 运行环境 profile：默认 `local`，生产用 `prod` | 否 | — |

> 密钥的注入方式二选一：
> 1. **环境变量**：启动前设置 `DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`、`SEARCH_API_KEY` 等（`application.yml` 中已用 `${变量名:}` 占位引用）；
> 2. **`application-prod.yml`**：仅在需要把密钥随镜像打包时使用（该文件已被 `.gitignore` 排除，**切勿提交到仓库**）。

### 6.2 前端环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `VITE_FUNASR_WS_URL` | 语音识别 WebSocket 地址（构建时生效） | `ws://127.0.0.1:10095` |

前端接口地址无需配置：开发环境自动请求 `http://localhost:8123/api`，生产构建后使用相对路径 `/api`（适配前后端同域部署）。

### 6.3 语音识别服务环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `WHISPER_MODEL` | 模型大小：`base` 更快 / `small` 均衡 / `medium` 更准 / `large-v3` 最佳 | `small` |
| `WHISPER_HOST` | 监听地址，服务器部署改为 `0.0.0.0` | `127.0.0.1` |
| `WHISPER_PORT` | 监听端口 | `10095` |
| `HF_ENDPOINT` | HuggingFace 镜像（国内建议 `https://hf-mirror.com`） | — |
| `HF_HUB_DISABLE_XET` | 镜像下载报 401 时设为 `1` | — |

> 完整说明见 [faster-whisper-server/README.md](faster-whisper-server/README.md)。

## 七、各模块启动与联调

### 7.1 端口一览

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| Spring Boot 后端 | 8123 | 上下文路径 `/api`，接口文档 `/api/doc.html` |
| Vite 前端（开发） | 3000 | `npm run dev` |
| 前端容器（Compose） | 80 | 生产编排中为 `127.0.0.1:8090`（由宝塔 Nginx 反向代理对外） |
| MCP 图片搜索服务 | 8127 | SSE 模式，`/sse` 为 MCP 连接端点 |
| 语音识别服务 | 10095 | WebSocket |
| PostgreSQL | 5432 | pgvector 版 |

### 7.2 独立启动命令

**后端**

```powershell
# 本地开发（默认 local profile）
.\mvnw.cmd spring-boot:run

# 以 prod profile 运行（需准备 application-prod.yml）
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

**前端**

```bash
cd lianba-ai-agent-frontend
npm install     # 首次
npm run dev     # 开发服务器（3000）
npm run build   # 生产构建，产物在 dist/
```

**MCP 图片搜索服务**

```powershell
cd image-search-mcp-server
.\mvnw.cmd spring-boot:run        # 默认激活 sse profile，端口 8127
```

> 运行前必须先创建 `src/main/resources/application.yml`（从 `application.yml.example` 复制并填入 Pexels Key）；如需 stdio 模式，切换 profile 为 `stdio`（`application-stdio.yml`）。

**语音识别服务**

```powershell
cd faster-whisper-server
pip install -r requirements.txt
python server.py        # 首次运行会自动下载模型（约 500MB）
```

### 7.3 联调关系

```text
浏览器（Vue 前端 :3000）
   ├─ HTTP / SSE（/api/...）────────▶ Spring Boot 后端 :8123 ────▶ PostgreSQL :5432（pgvector）
   └─ WebSocket（语音）─────────────▶ faster-whisper 语音服务 :10095

Spring Boot 后端 ── MCP（SSE 客户端）──▶ MCP 图片搜索服务 :8127 ──▶ Pexels API
Spring Boot 后端 ── HTTPS ──▶ 百炼 DashScope / DeepSeek / SearchAPI / 阿里云 OSS
```

- MCP 服务**必须先于后端启动**（后端依赖其提供图片搜索能力）；
- 后端与 MCP 的接入方式：Spring AI MCP Client（SSE 连接 `MCP_SERVER1_URL`；`application.yml` 中提供了注释掉的完整配置示例，本地调试时可取消注释启用）。

## 八、核心接口速览

后端统一前缀 `/api`（`server.servlet.context-path`）。

| 模块 | 接口 | 说明 |
| --- | --- | --- |
| 登录态 | `POST /user/register`、`POST /user/login`、`POST /user/logout`、`GET /user/current` | 注册 / 登录 / 登出 / 当前用户 |
| 会话管理 | `POST /session/create`、`GET /session/list`、`GET /session/messages` | 会话创建、列表、消息查询 |
| 恋爱大师 | `GET /ai/love_app/chat/sse` | SSE 流式对话 |
| | `GET /ai/love_app/chat/rag_sse`、`GET /ai/love_app/chat/hybrid_rag_sse` | 带 RAG / 混合检索增强的流式对话 |
| | `GET|POST /ai/love_app/chat/tools_sse` | 带工具调用的流式对话（支持图片输入） |
| | `GET /ai/love_app/match/sse` | 恋爱匹配模式流式对话 |
| 恋爱报告 | `GET /ai/love_app/chat/report`、`GET /ai/love_app/report/list`、`GET /ai/love_app/report/detail`、`POST /ai/love_app/report/export` | 生成 / 列表 / 详情 / 导出 |
| 智能体 Manus | `GET /ai/manus/chat`（SSE）、`POST /ai/manus/stop`、`GET /ai/manus/task`、`GET /ai/manus/task/list`、`POST /ai/manus/human_reply`、`POST /ai/manus/chat/vision` | 流式执行 / 停止 / 任务查询 / 人工介入 / 多模态 |
| 日记与建议 | `GET /diary/list`、`GET /diary/{id}`、`POST /diary/{id}/analyze`、`GET /advice/today`、`POST /advice/generate` | 日记列表、详情、AI 分析、今日建议、手动生成 |
| 社区 | `GET /community/posts`、`POST /community/post`、`POST /community/post/{id}/comment`、`POST /community/post/{id}/like`、`POST /community/post/{id}/delete` | 帖子列表、发布、评论、点赞、删除 |
| 文件与图片 | `POST /images/upload`、`POST /images/parse`、`GET /files/download/pdf/{fileName}` 等 | 图片上传、图片解析、文件下载 |
| 会员 | `GET /membership/status` | 会员状态查询 |
| RAG 导入 | `POST /rag/parse`、`POST /rag/confirm`、`POST /rag/import`、`POST /rag/discard` | 文档解析、确认、入库、丢弃 |
| 健康检查 | `GET /health` | 服务健康探活（返回 `{"data":"ok"}`） |

## 九、部署说明

项目提供两套完整的部署资料，均在 `deploy/` 目录：

| 文档 / 脚本 | 适用场景 |
| --- | --- |
| [deploy/阿里云宝塔Docker部署手册.md](deploy/阿里云宝塔Docker部署手册.md) | **推荐**：阿里云服务器 + 宝塔面板 + Docker 全流程（镜像构建 → 上传 → 一键部署 → HTTPS → 运维 → 排障） |
| [deploy/部署说明.md](deploy/部署说明.md) | 宝塔面板传统方式部署（手动启动 PostgreSQL / MCP / 后端 jar / 前端静态站） |
| `deploy/build-images.ps1` | 本地 Windows 构建并导出生产镜像 |
| `deploy/install.sh` | 服务器端一键安装（加载镜像并启动 Compose） |
| `deploy/nginx-baota.conf` | 宝塔 Nginx 反向代理配置参考 |

生产编排（`docker-compose.prod.yml`）与开发编排的关键差异：

- 所有服务**仅绑定 `127.0.0.1`**，不直接暴露公网，由宝塔 Nginx 统一对外（前端映射 `127.0.0.1:8090`）；
- 数据库密码**强制从 `.env` 注入**（不使用默认弱密码）；
- 面向 2G 内存服务器的内存限制（JVM 参数、PostgreSQL 参数均已调优）；
- 统一时区 `Asia/Shanghai`。

## 十、常见问题（FAQ）

**Q1：一定要 JDK 21 吗？**
是。后端与 MCP 服务编译目标均为 Java 21；单独运行 MCP 服务的 jar 包时同样要求 JDK 21 环境，低版本 JDK 会直接报类版本错误。

**Q2：启动时报数据库连接失败 / 表不存在？**
依次检查：PostgreSQL 是否已启动（默认 `localhost:5432`）→ 数据库 `lianba_ai_agent` 是否已创建 → pgvector 扩展是否可用（建议直接使用 `pgvector/pgvector:pg16` 镜像）。Flyway 会在后端启动时自动完成建表。

**Q3：Spring Profile 怎么理解？缺 `application-prod.yml` 会怎样？**
主配置默认 `profile=local`，适合本地开发（密钥走环境变量或为空）；`prod` profile 用于生产/Docker，真实密钥建议通过 `application-prod.yml` 或环境变量提供。缺少密钥时应用可以启动，但相关 AI 功能不可用。

**Q4：后端重启后用户登录态丢失？**
登录态基于服务端 `HttpSession` 内存存储，后端重启会清空会话，用户需要重新登录，这是当前实现的预期行为。

**Q5：MCP 相关功能报错 / 后端调用不到 MCP 服务？**
确认 MCP 服务已启动并监听 8127 端口，并且**先于后端启动**；Docker 编排中依赖健康检查自动保证顺序，本地手动启动时请注意顺序。

**Q6：语音识别首次启动很慢或报 401？**
首次运行需下载模型（约 500MB）。国内网络建议设置 `HF_ENDPOINT=https://hf-mirror.com`；若下载报 401，再追加 `HF_HUB_DISABLE_XET=1`（`run-whisper.ps1` 已默认设置镜像）。

**Q7：前端页面能打开但接口全部 401 / 请求错地址？**
开发环境前端固定请求 `http://localhost:8123/api`，确认后端已启动且端口一致；生产构建使用相对路径 `/api`，需要 Nginx 将 `/api` 反向代理到后端 8123 端口。

**Q8：某端口被占用怎么办？**
对照「7.1 端口一览」逐一排查（8123 / 3000 / 8127 / 10095 / 5432），关闭占用进程或调整对应配置。

## 十一、安全说明

- 本仓库**不包含任何真实密钥**：数据库密码、大模型 API Key、OSS AccessKey、SMTP 授权码等一律通过环境变量或本地私有配置文件提供，敏感配置文件已被 `.gitignore` 排除；
- 需要真实密钥的文件（`application-prod.yml`、MCP 的 `application.yml`、`.env`、`mcp-servers.json` 等）请仅在本地/服务器维护，**不要提交、不要随压缩包分享**；
- 注意：以 prod 配置打包出的 jar 内嵌密钥，**分发 jar 或镜像前请先移除密钥**；
- 若发现密钥意外泄露，请立即到对应平台重置密钥。

---

*README 由「若风」维护，如与代码不一致，以代码为准。*
