# FitPilot

FitPilot 是一个面向健身新手和日常健身用户的饮食与训练规划应用。

目前第一阶段聚焦网页端的饮食记录 MVP：用户注册时根据身体数据生成营养目标，登录后按食物和克数记录实际摄入，并查看每日营养进度与体重趋势。主页支持调整饮食计划，在减脂、维持和轻盈增肌之间切换；新目标从今天生效，历史日期保留原目标。食物库没有目标菜品时，也可以通过可配置的大模型 API 按菜名和克数估算营养，确认后加入当天记录。

## Repository layout

```text
fitpilot/
├── backend/     # Spring Boot REST API
├── web/         # React + TypeScript + Vite 网页端
├── mobile/      # React Native + Expo（已暂停，后续实现）
└── docs/        # 项目设计与开发笔记
```

## One-command local environment (Docker)

安装并启动 Docker Desktop 后，在项目根目录运行：

```powershell
cd D:\code\fitapp
docker compose up --build
```

首次构建需要下载 Java、Node、Nginx 和 PostgreSQL 镜像。三个服务健康后访问：

- 网页端：`http://localhost:5173`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

默认配置可以直接用于本地体验。需要配置 AI、修改端口或替换本地开发密钥时，先把 `.env.example` 复制为 `.env`，再修改 `.env`；真实密钥不会被 Git 跟踪。

停止全部服务并释放 8080/5173：

```powershell
docker compose down
```

PostgreSQL 数据保存在 Docker 命名卷中，普通 `down` 后仍会保留，而且与电脑上原有的 PostgreSQL 数据库完全隔离。只有明确想清空容器数据库时才使用 `docker compose down --volumes`。

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

Docker Compose 正在运行时，可以执行真实 Chromium 端到端测试：

```powershell
cd D:\code\fitapp\web
npx playwright install chromium  # 每台机器首次运行一次
npm run test:e2e
```

这条测试会连接实际网页、后端与 PostgreSQL，完成登录、记录饮食和调整计划；测试饮食记录会自动清理。

启动 Docker Desktop 后，可以运行包含临时 PostgreSQL 的完整验证：

```powershell
mvn -Pintegration-tests verify
```

集成测试会从空数据库执行 Flyway 迁移并自动删除测试容器，不会修改本机的 `fitpilot` 数据库。

## Documentation

- [Project Summary](docs/PROJECT_SUMMARY.md)：项目定位、当前成果、技术亮点、限制与下一阶段。
- [开发交接文档](docs/HANDOFF.md)：运行方式、关键实现、验证结果和后续开发上下文。
- [架构说明](docs/architecture.md)：系统结构与主要数据流。
