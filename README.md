# FitPilot

FitPilot 是一个面向健身新手和日常健身用户的饮食与训练规划应用。

目前第一阶段聚焦网页端的饮食记录 MVP：用户注册时根据身体数据生成营养目标，登录后按食物和克数记录实际摄入，并查看每日营养进度与体重趋势。主页支持调整饮食计划，在减脂、维持和轻盈增肌之间切换；新目标从今天生效，历史日期保留原目标。食物库没有目标菜品时，也可以通过可配置的大模型 API 按菜名和克数估算营养，确认后加入当天记录。

支持补录历史饮食与体重。尚未建立营养目标的日期也能记录实际摄入，页面会明确显示“当天无目标”；体重卡片可单独选择日期，通过明细编辑已有记录或确认删除误录，趋势随之更新。注册与身体资料编辑只需选择活动水平，无需重复填写训练次数。

登录使用 Redis 保护：同一账号与来源默认 15 分钟内连续失败 5 次进入冷却，页面显示重试倒计时。Redis 暂时不可用时新登录返回可重试提示，已登录的饮食和体重操作仍可使用。

食物搜索会将内置食物结果缓存到 Redis，最长保留 5 分钟；自定义食物仍实时按用户从 PostgreSQL 查询，修改和归档立即生效。缓存故障会回退数据库，不阻断搜索。可用 `FOOD_CACHE_ENABLED=false` 关闭缓存，或通过 `FOOD_CACHE_TTL_SECONDS`（1–3600，默认 300）调整期限。

缓存连接故障后，食物搜索会暂时跳过 Redis，默认 5 秒后由一个请求尝试恢复，避免每次查询重复等待超时；可用 `FOOD_CACHE_BACKOFF_SECONDS`（1–60）调整。已加入缓存命中、回源耗时和故障的内部监控指标；登录限流仍保持独立保护。数据库启动时会自动应用 Flyway V4，为历史营养目标和最近食物查询补充复合索引，保留原有数据。

后端常见参数与权限错误统一返回 Problem Detail JSON，包含可展示的错误说明和可识别的字段提示。食物和饮食记录 ID 校验正数，整数参数不再静默截断小数；未登录、越权和非法输入分别保留 401、403、400 状态。详见 [后端接口说明](backend/README.md#api-error-responses)。

## Repository layout

```text
fitpilot/
├── backend/            # Spring Boot REST API
├── web/                # React + TypeScript + Vite 网页端
├── deploy/             # 生产容器启动脚本
├── mobile/             # React Native + Expo（已暂停，后续实现）
├── docs/               # 项目设计与开发笔记
├── Dockerfile          # React + Spring Boot 单服务生产镜像
└── render.yaml         # Render Web Service + PostgreSQL Blueprint
```

## One-command local environment (Docker)

安装并启动 Docker Desktop 后，在项目根目录运行：

```powershell
cd D:\code\fitapp
docker compose up --build
```

首次构建需要下载 Java、Node、Nginx、PostgreSQL 和 Redis 镜像。四个服务健康后访问：

- 网页端：`http://localhost:5173`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

默认配置可以直接用于本地体验。需要配置 AI、修改端口或替换本地开发密钥时，先把 `.env.example` 复制为 `.env`，再修改 `.env`；真实密钥不会被 Git 跟踪。

启用 AI 营养估算时，在本地 `.env` 中填写自己的 `AI_API_KEY`，使用 `AI_BASE_URL=https://api.deepseek.com` 与 `AI_MODEL=deepseek-flash`，可将 `AI_TIMEOUT_SECONDS` 设置为 60。执行 `docker compose up -d --build --wait backend` 加载配置，再执行 `docker compose restart web` 刷新连接。登录网页后输入菜名和克数，点击“用 AI 按菜名和克数估算”，确认结果后保存；模型 API 需要可用额度。2026-10-19 已用真实 DeepSeek 调用验证估算、确认保存及刷新后持久化。

停止全部服务并释放 8080/5173：

```powershell
docker compose down
```

PostgreSQL 数据保存在 Docker 命名卷中，普通 `down` 后仍会保留，而且与电脑上原有的 PostgreSQL 数据库完全隔离。只有明确想清空容器数据库时才使用 `docker compose down --volumes`。

Redis 也使用独立数据卷保存限流状态，不开放宿主机端口。`LOGIN_MAX_FAILURES` 与 `LOGIN_WINDOW_SECONDS` 可调整规则。直接用 Maven 启动后端时需配置可达的 `REDIS_URL`；通过反向代理部署时，仅将实际代理加入 `LOGIN_TRUSTED_PROXIES`。Render Blueprint 已包含私网 Key Value，免费实例的计数可能在重启后丢失。

## Production deployment topology

公开演示环境使用根目录 `Dockerfile`：先构建 React，再把静态文件打进 Spring Boot JAR，由一个非 root Java 容器同时提供网页和同源 `/api`。这减少了托管环境中的服务数量，也不需要为浏览器配置跨域地址。`/actuator/health` 是公开健康端点，其余业务 API 继续要求 JWT。

本地可以用独立端口和独立数据卷复现生产拓扑：

```powershell
$env:WEB_PORT = "5174"
docker compose -f compose.deploy.yaml up --build --wait
```

访问 `http://localhost:5174`，停止并删除这套临时环境可运行 `docker compose -f compose.deploy.yaml down --volumes`。它与上面的日常四容器开发环境互不共用 PostgreSQL 和 Redis 数据卷。

根目录 `render.yaml` 可在 Render 中创建一个 Docker Web Service 和一个仅允许私网连接的 PostgreSQL 17 数据库。Blueprint 会生成 JWT 密钥，并通过 Render 的 `connectionString` 注入数据库连接；可选的 `AI_API_KEY` 需要部署后在 Dashboard 中另行配置。当前仓库已经具备部署配置，但尚未创建公开线上实例；正式保存真实用户数据前仍需确定数据库备份与恢复策略。配置格式依据 [Render Blueprint 官方规范](https://render.com/docs/blueprint-spec)。

## Manual development mode

手动模式更适合日常开发和热更新。

### Run the backend

1. 安装 Java 21 和 Maven。
2. 进入 `backend` 目录并运行：

   ```bash
   mvn spring-boot:run
   ```

3. 服务默认运行在 `http://localhost:8080`。

详见 [backend/README.md](backend/README.md)。

### Run the web app

保持后端运行，再打开一个 PowerShell 窗口：

```powershell
cd D:\code\fitapp\web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 会把同源 `/api` 请求代理到 `http://localhost:8080`；首次使用请选择“创建账户”，注册成功后会自动进入饮食 Dashboard。

## Verify the backend

日常开发运行快速测试：

```powershell
cd D:\code\fitapp\backend
mvn test
```

网页端核心流程测试：

```powershell
cd D:\code\fitapp\web
npm test
```

Docker Compose 正在运行时，可以执行真实跨浏览器端到端测试：

```powershell
cd D:\code\fitapp\web
npx playwright install chromium firefox  # 每台机器首次运行一次
npm run test:e2e
```

这条测试会连接实际网页、后端与 PostgreSQL，完成登录、记录饮食和调整计划；测试饮食记录会自动清理。

启动 Docker Desktop 后，可以运行包含临时 PostgreSQL 和 Redis 的完整验证：

```powershell
mvn -Pintegration-tests verify
```

集成测试会从空数据库执行 Flyway 迁移，并验证登录限流、搜索缓存命中/过期和自定义食物隔离；自动删除测试容器，不修改本机业务数据库或 Redis 状态。

## Continuous integration

[GitHub Actions CI](https://github.com/kekevot233/fitpilot/actions/workflows/ci.yml) 按需运行：普通小改动不执行测试，较大功能更新、架构调整或较大范围修改后再验证，也可按用户要求运行。push 和 pull request 不自动触发；在 Actions 页面选择 **CI → Run workflow**，或执行 `gh workflow run ci.yml --ref main`：

- 后端：Java 21 + Maven 快速测试、Testcontainers PostgreSQL / Redis 集成测试和打包。
- 前端：Node.js 22 + `npm ci`、Vitest、TypeScript 检查和 Vite 生产构建。
- 端到端：前两项通过后，用 Docker Compose 启动与 Render 相同的单服务生产镜像、PostgreSQL 与 Redis，再由桌面 Chromium、桌面 Firefox 和手机尺寸 Chromium 验证注册、登录失败及核心饮食流程。

后端、前端和 Playwright 报告在运行页面的 Artifacts 中保留 14 天；E2E 失败时还会保存容器状态和日志。CI 使用临时数据库卷，不需要配置应用密钥或外部数据库，并在结束时自动清理。配置和排错说明见[开发交接文档](docs/HANDOFF.md#github-actions-ci已接入)。

## Documentation

- [Project Summary](docs/PROJECT_SUMMARY.md)：项目定位、当前成果、技术亮点、限制与下一阶段。
- [开发交接文档](docs/HANDOFF.md)：运行方式、关键实现、验证结果和后续开发上下文。
- [架构说明](docs/architecture.md)：系统结构与主要数据流。
