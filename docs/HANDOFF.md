# FitPilot 项目交接文档

> 工作目录：`D:\code\fitapp`  
> 当前阶段：网页端饮食记录 MVP；后端 Spring Boot + PostgreSQL；前端 React + Vite。  
> 最后更新：2026-10-19

## 文档维护规则

项目同时维护两份互补文档：

- `docs/PROJECT_SUMMARY.md`：面向新读者、GitHub 和简历准备，概括产品定位、已实现能力、技术亮点、质量基线、限制与下一阶段。
- `docs/HANDOFF.md`：面向后续开发，记录具体实现、关键文件、API、运行方式、验证结果、排错信息和接手建议。

每完成一个可交付阶段，必须同步检查并更新这两份文档的“最后更新”、功能状态、测试数量、已知限制和下一步计划。运行方式、环境变量或项目入口变化时，还要同步更新根目录 `README.md`。Project Summary 不记录冗长的调试流水，Handoff 则要保留足够让新对话直接继续工作的技术上下文。

## 给新对话的任务背景

用户是有少量 Java 基础、正在学习 Spring Boot 的学生。目标是把 FitPilot 做成可部署、可放进 GitHub 和简历的 Java / Agent 开发实习作品。

协作偏好：可以以“vibe coding”推进项目，但每一阶段要解释：

1. 先说思路；
2. 再说明为什么这样设计；
3. 然后实施；
4. 完成后说明改了什么、怎样验证、用户接下来怎样使用。

第一阶段不要过度设计：不引入微服务、消息队列或 AI。中文交流。

## 技术栈与目录

```text
D:\code\fitapp
├── backend/   # Java 21、Maven、Spring Boot、JPA、PostgreSQL
├── web/       # React、TypeScript、Vite
├── docs/      # Project Summary、交接文档与架构说明
├── README.md
└── mobile/    # React Native + Expo（当前暂停）
```

后端：Java 21、Spring Boot 3.4.5、Spring Web、Validation、Spring Data JPA、PostgreSQL、Spring Security + JWT、springdoc Swagger。  
前端：React、TypeScript、Vite，开发地址默认是 `http://localhost:5173/`。  
后端地址默认是 `http://localhost:8080/`。

## 已完成能力

### 后端

- Mifflin–St Jeor BMR / TDEE / 宏量营养素计算：`POST /api/nutrition/calculate`
- PostgreSQL 用户、营养目标、食物、饮食记录、体重记录、训练计划与训练记录的实体及 API。
- JWT 注册、登录、鉴权；受保护接口从 token 取当前用户，不信任浏览器传来的其他用户 ID。
- 食物库、按克数计算营养、创建/修改/删除饮食记录、每日 Dashboard。
- 体重记录与 7 日移动平均趋势。
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 食物初始化目录：`backend/src/main/resources/data/default-foods.json`
  - 现有 **85** 个内置食物。
  - 包含原料和中国常见复合食物：鱼香肉丝、宫保鸡丁、麻婆豆腐、包子、饺子、油条、兰州牛肉面、沙县鸡腿饭等。
  - 初始化器在每次后端启动时执行，新增的内置食物需要重启后端才会写入数据库。

### 网页端

- 注册 / 登录页面。
- 今日营养 Dashboard：总热量、蛋白质、碳水、脂肪、剩余目标。
- 食物搜索、按克数预览、保存饮食记录、编辑 / 删除记录。
- 食物搜索框默认收起；点击或聚焦时显示悬浮下拉菜单。
- 空搜索框优先显示当前用户最近记录的食物；选择后自动带入上次克数和餐次，首次使用时提示搜索食物库。
- 七日热量趋势：实际摄入柱状图、当天目标线、平均摄入和有效记录天数。
- 自定义食物创建。
- 主界面调整饮食计划（减脂 / 维持 / 轻盈增肌），从今天生效并保留历史目标。
- AI 菜品营养估算：输入菜名和克数，查看估算依据并确认或修改后记入当天饮食。
- 体重录入和趋势图。
- 没有生效营养目标的历史日期也可补记饮食，显示实际摄入并明确提示当天无目标。

## 关键设计与文件定位

| 目的 | 关键文件 |
| --- | --- |
| 计算 BMR/TDEE/宏量目标 | `backend/src/main/java/com/fitpilot/service/NutritionCalculationService.java` |
| 用户资料、保存营养目标 | `backend/src/main/java/com/fitpilot/service/UserProfileService.java` |
| `users` 实体 | `backend/src/main/java/com/fitpilot/domain/User.java` |
| `nutrition_targets` 实体 | `backend/src/main/java/com/fitpilot/domain/NutritionTarget.java` |
| 每日饮食 Dashboard | `backend/src/main/java/com/fitpilot/service/FoodLogService.java` |
| 最近记录的食物 | `backend/src/main/java/com/fitpilot/controller/RecentFoodController.java` |
| 七日饮食趋势聚合 | `backend/src/main/java/com/fitpilot/service/NutritionTrendService.java` |
| 默认食物初始化 | `backend/src/main/java/com/fitpilot/config/DefaultFoodDataInitializer.java` |
| 食物 JSON 数据库 | `backend/src/main/resources/data/default-foods.json` |
| 前端 API 封装 | `web/src/api.ts` |
| 前端类型 | `web/src/types.ts` |
| 前端主页数据流 | `web/src/App.tsx` |
| 食物下拉及录入 UI | `web/src/components/FoodEntryForm.tsx` |
| 网页样式 | `web/src/styles.css` |

## 营养目标的历史逻辑

`nutrition_targets` 有 `effective_date`（生效日期）。Dashboard 查询某一天时，会读取该日期当天或更早的最新一条目标：

```java
findTopByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(...)
```

这样旧日期的饮食记录能继续对应旧目标；今天开始切换减脂计划时，应该从今天开始使用新目标，不能覆盖历史日期的目标。

当前宏量规则：

- 蛋白质：`2.0 g/kg`
- 脂肪：`0.9 g/kg`
- 剩余热量分配给碳水
- 目标热量：减脂 `TDEE - 400`，维持 `TDEE`，轻盈增肌 `TDEE + 250`

这些常量在 `NutritionCalculationService` 和 `Goal` 枚举中，均为 MVP 规则，而非医疗建议。

## 饮食计划调整（已实现）

主界面营养进度卡片下方新增“当前饮食计划”面板，显示当前计划、每日热量和三大营养素目标。

使用方式：点击“调整饮食计划” → 选择减脂 / 维持 / 轻盈增肌 → 点击“保存并重新计算”。成功后返回今天并刷新 Dashboard，显示保存成功和生效日期。保存期间禁用重复操作；保存失败保留选项并可重试；未改变目标时不提交。

### 后端

- 接口：`PUT /api/me/nutrition-plan`，请求示例：`{ "goal": "FAT_LOSS" }`。
- `NutritionPlanController` 从 JWT 获取当前用户 ID，使用 `@Valid` 校验必填目标，不接受客户端指定用户。
- `UserProfileService.updateNutritionPlan` 在事务内更新 `users.goal`，按用户已保存的身体资料和活动水平调用现有计算服务，新增今天生效的 `NutritionTarget`，返回最新 `UserProfileResponse`。
- 历史目标保留，同一天多次调整按已有日期 + ID 排序取最后一条。
- 无需数据库结构变更。体重趋势记录与用户资料体重仍为独立数据；本功能重算使用 `users.weightKg`，不自动采用最新体重日志。

### 前端

- `web/src/components/NutritionPlanPanel.tsx`：计划展示、展开选项、保存、错误和成功反馈。
- `web/src/api.ts`：`updateNutritionPlan(goal)`。
- `web/src/App.tsx`：更新用户状态、返回今天、刷新进度，并忽略切换计划/日期前发出的旧数据请求。
- `web/src/styles.css`：计划面板及响应式选项布局。
- 查看历史日期时明确区分历史进度和当前计划；修改始终从今天生效。

### 验证

- 前端 `npm run build` 通过。
- 后端 `mvn -o test` 共 21 项通过。

## 身体资料更新与营养目标重算（已实现）

主页新增默认收起的“身体资料”面板。用户可修改年龄、身高、计划体重基准和活动水平；保存后继续沿用当前减脂 / 维持 / 轻盈增肌目标，并重新计算 BMR、TDEE、热量与三大营养素。

- 接口：`PUT /api/me/profile`，用户 ID 只从 JWT 获取。
- `UserProfileService.updateProfile` 在一个事务中更新 `users` 并新增今天生效的 `nutrition_targets` 记录，避免资料与目标只成功一半。
- 不覆盖旧营养目标，因此历史 Dashboard 仍能读取当时生效的目标；同日多次修改按 ID 取最后一条。
- 每日 `weight_logs` 只负责趋势，不会自动改变计划体重基准。这样可以避免水分波动导致每日热量目标跟着抖动；用户确认保存身体资料时才会重算。
- 前端组件：`web/src/components/BodyProfilePanel.tsx`。
- 验证：前端 `npm run build` 成功；后端 `mvn -o test` 共 49 项通过。

## Redis 内置食物搜索缓存（2026-10-19）

- `BuiltInFoodSearchCache` 缓存 `GET /api/foods` 的内置食物搜索结果，命中时不再查询内置数据；`FoodRepository.findCustomByKeyword` 每次按当前用户读取自定义食物，再按名称、ID 合并排序。创建、编辑、归档自定义食物后立即生效，不存在私人缓存失效或跨用户共享问题；营养试算与饮食快照继续使用数据库。
- 使用一个版本化 Redis Hash：`fitpilot:foods:search:v1`。字段为 trim 后关键词 SHA-256，值为 `FoodResponse` JSON 数组（保留 BigDecimal，空结果也缓存）。查询匹配仍交给 PostgreSQL，因此保留大小写及 LIKE 通配符语义；不擅自把大小写不同的关键词合并到同一桶。
- `redis/food-search-put.lua` 原子限制最多 128 个字段，并设置整个 Hash 的固定 TTL。首次写入起最长 300 秒，后续写入/命中不续期；满额后新关键词仍从数据库返回，但不再缓存。长于 128 字符的关键词或大于 262144 字符的 JSON 不写缓存，避免无限增长。并发未命中可能重复读取数据库，暂不引入分布式锁。
- `FOOD_CACHE_ENABLED` 默认 true；`FOOD_CACHE_TTL_SECONDS` 默认 300、校验范围 1–3600，两套 Compose 与 `.env.example` 均支持。未新增依赖、数据库迁移、容器或宿主机端口。
- Redis 读取失败直接回退数据库并跳过本请求的缓存写入，避免重复等待；写入失败、JSON 损坏或字段不完整同样不阻断食物搜索。此处是性能缓存，与 Redis 故障时必须暂停的登录保护分开处理。
- 内置食物库更新最多延迟一个 TTL 反映在搜索；需即时刷新可执行 `docker compose exec redis redis-cli DEL fitpilot:foods:search:v1`。使用 `HLEN` / `TTL` 检查容量和过期；不要使用 FLUSHDB / FLUSHALL，以免清除登录限流。
- 新增 5 个快速测试覆盖不可用读取、失败写入、损坏 JSON、缓存关闭和长关键词；3 个真实 Redis 集成测试验证跨实例命中、空结果缓存、BigDecimal、TTL、并发容量和不续期。现有 PostgreSQL 集成套件增加真实用户隔离、编辑/归档后立即可见，以及缓存内容不含私人食物的验证。
- 本机 `mvn -Pintegration-tests verify`：104 个快速测试、12 个容器集成测试（PostgreSQL 4、独立 Redis 8）通过；真实开发 Compose 的桌面/手机 Chromium 8 项通过，Redis 中实际生成带 TTL 的搜索缓存，四服务 healthy。
- 远端 [GitHub Actions 39191545974](https://github.com/kekevot233/fitpilot/actions/runs/39191545974)（提交 `f2a1746`）三个任务全部成功：104 个快速测试、12 个容器集成测试、14 个 Vitest、生产构建，以及生产镜像连接 PostgreSQL / Redis 上包含 Firefox 的 12 个 Playwright 执行项。三类报告已上传，临时容器和数据卷已清理。

## Redis 登录失败限流（2026-10-19）

- 登录按规范化邮箱与来源地址的 SHA-256 摘要分桶，不在 Redis 键中保存明文邮箱、地址、密码或 JWT。默认 15 分钟窗口内最多 5 次失败：前 4 次错误凭据返回 401，第 5 次返回 429；窗口内继续尝试（包括正确密码）仍为 429，成功登录会清除该桶失败计数。未知账号采用相同规则和错误文案。
- `LoginRateLimiter` 用两个 Lua 脚本原子预留/完成请求。失败计数窗口从第一次失败开始，不会因反复重试延长。正在校验的请求也暂占额度，避免多个实例并发绕过；额度忙时返回 1 秒重试，预留默认 30 秒后失效，进程异常不会永久占用。脚本用 Redis 时钟；两个键共享哈希标签，支持跨实例共享。
- 配置：`REDIS_URL`、`LOGIN_MAX_FAILURES`（5）、`LOGIN_WINDOW_SECONDS`（900）、`LOGIN_ATTEMPT_LEASE_SECONDS`（30）、`LOGIN_TRUSTED_PROXIES`（默认空）。非正常业务错误只释放预留，不累计密码失败。
- `LoginClientAddress` 默认使用真实连接地址，忽略用户提供的转发头；只有已配置的可信代理可提供 `X-Forwarded-For`，从右向左解析到首个不可信来源。支持明确代理 IP/CIDR 或受控 Docker 服务名；本地 Compose 只信任 `web`，Nginx 覆盖转发来源并移除 `Forwarded`。后端禁用框架自动转发地址重写。生产代理须按实际链路配置，不使用任意来源通配信任。
- 429 同时返回 `Retry-After` 秒数和 Problem Detail 的 `retryAfterSeconds`。Redis 失败或返回异常空值时不绕过限流，登录返回不含连接详情的 503 + 5 秒重试；已登录数据 API 不依赖 Redis，核心健康检查不因 Redis 失效而下线。
- 网页按服务端重试时间显示倒计时并禁用该账号重复提交，到期恢复；其他账号及注册不受本账号倒计时影响。页面重新加载后仍由后端强制限流。
- 两套 Compose 都增加私网 Redis 7.4、健康依赖、AOF 数据卷与 `noeviction` 策略；无需迁移 PostgreSQL。Render Blueprint 增加私网 Key Value 并自动注入 `REDIS_URL`，free 示例重启可能丢计数，正式部署需评估持久化套餐。Redis 不发布宿主机端口；Maven 直跑需另外提供可达的 Redis URL。
- 测试覆盖原子并发预留、跨客户端共享、桶隔离、成功清理、失败窗口与崩溃预留过期、可信代理/伪造头、429/503 契约、前端倒计时。Playwright 新增真实失败 5 次触发限流及伪造转发头不能绕过的场景。
- 本地验证：99 个后端单元/MVC 测试、8 个 Testcontainers 测试（PostgreSQL 3、Redis 5）、14 个 Vitest 及生产构建通过；桌面/手机 Chromium 共 8 项通过。开发 Compose 四服务均 healthy，保留原 PostgreSQL 数据卷。
- 远端验证：[GitHub Actions 39187897581](https://github.com/kekevot233/fitpilot/actions/runs/39187897581)（提交 `df861d7`）三个任务全部成功，生产镜像连接 PostgreSQL / Redis 完成包含 Firefox 的 12 个 Playwright 执行项；三类测试报告均上传，临时容器及数据卷已清理。

## 体重明细与误录删除（2026-10-17）

- 体重卡片新增默认收起的“体重记录明细”，复用当前 30 天趋势的 `points`，按日期倒序显示并注明区间。点击编辑会选择该日期并带入原体重；“回到今天”恢复今天的录入入口，不改变饮食日期。
- 新接口：`DELETE /api/me/weight-logs/{recordedDate}`，成功返回 204。用户 ID 从 JWT 读取；Service 在事务中按 `userId + recordedDate` 查询再删除，不能通过客户端参数删除其他用户记录；找不到自己的记录返回 404。无需数据库迁移。
- 删除需在页面再次确认，可取消。请求期间禁用体重表单和明细操作；失败保留原记录、错误信息与重试入口，成功后重新查询趋势，更新原始曲线、七日均值与统计。最后一条删除后显示空状态；不会修改用户资料中的计划体重基准或营养目标。
- `App.tsx` 对当前体重查询保存用户/日期标识；切换日期或退出登录后，旧保存/删除的回调不能重新加载旧日期覆盖当前趋势。
- 新增 Controller/Service 测试覆盖 JWT 用户隔离、未登录拒绝、日期格式、找不到记录；PostgreSQL 集成测试验证双用户同日期互不影响及删除后均值重算。网页测试覆盖倒序明细、编辑、取消、失败重试、删除为空和请求未返回时切换日期；Playwright 注册场景扩展编辑/取消/删除及刷新后的持久化验证。
- 本地验证：85 个快速测试、3 个 PostgreSQL Testcontainers 集成测试、11 个 Vitest 测试及生产构建通过；开发 Compose 前后端已更新，桌面/手机 Chromium 共 6 项通过，含展开明细时手机页面宽度检查。
- 远端验证：[GitHub Actions 39019935648](https://github.com/kekevot233/fitpilot/actions/runs/39019935648)（提交 `7f54779`）全部成功：85 个快速测试、3 个 PostgreSQL 集成测试、11 个 Vitest、生产构建，以及真实生产镜像上包含 Firefox 的 9 个 Playwright 执行项（25.8 秒）。测试报告已上传，临时数据库卷已清理。

## 无目标历史日期饮食补记（2026-10-17）

- `GET /api/users/{userId}/dashboard?date=YYYY-MM-DD` 对已有用户始终返回当日摄入与饮食列表。日期早于首条营养目标时返回 200，`target`、`remaining` 为 `null`；用户不存在仍返回 404。
- `FoodLogService` 不再因为历史目标缺失而中断日志查询，不创建、不回填目标，也不会用当前计划覆盖过去的含义。已有目标日期仍按生效日期计算剩余量。
- `DailyDashboardResponse` 的可空字段在 OpenAPI 中注明；网页 `DailyDashboard` 类型同步改为可空。
- `NutritionProgress` 无目标时显示热量和三大营养素实际摄入，隐藏剩余量、目标比例与完成环；页面提示可继续补记饮食和体重。食物表单在历史日期显示“加入当日饮食”。
- 七日趋势继续将缺失目标的 `targetCalories=0` 视为无目标，不画目标线，日期说明显示“当天无目标”。平均摄入涵盖所有有记录日期；平均目标和平均相差仅比较同时有记录和目标的日期，避免两个不同样本平均数相减。
- 回归覆盖空白/已有摄入的无目标日期、用户不存在、目标历史、真实 PostgreSQL 中不生成目标、前端历史食物时间戳与返回今天后的日期隔离，以及真实浏览器刷新后记录仍可读取。
- 本地验证：79 个快速单元/MVC 测试、2 个 PostgreSQL 集成测试、8 个 Vitest 测试及生产构建通过；开发 Compose 前后端已更新并健康运行，保留原数据库数据卷。
- 远端验证：[GitHub Actions 39000605044](https://github.com/kekevot233/fitpilot/actions/runs/39000605044)（提交 `932fff7`）三个任务全部成功：79 个快速测试、2 个 PostgreSQL 集成测试、8 个 Vitest、生产构建，以及真实生产镜像上的 9 个 Playwright 执行项（含 Firefox，23.9 秒）。三类报告已上传，CI 临时数据库卷已清理。
- GitHub 仓库已迁移至 `https://github.com/kekevot233/fitpilot`；本地 `origin` 已同步更新，旧地址的历史链接仍由 GitHub 重定向。

## 简化营养资料与历史体重补录（2026-10-15）

- 注册表单、身体资料编辑和资料摘要移除“每周训练次数/频率”；营养计算继续以活动水平计算 TDEE。
- `RegisterRequest` 与 `UpdateUserProfileRequest` 的 `weeklyTrainingFrequency` 改为可选，显式传入时仍限制 1–7，兼容旧调用方。注册省略时保存旧训练 API 的兼容默认值 3；更新资料省略时保留原值。该字段不参与营养计算，也不从活动水平推断；未来训练网页应独立收集训练安排。
- 问题根因：注册时首条营养目标从当天生效，查询更早日期的 Dashboard 返回 404；`App.tsx` 原来把体重表单放在 `dashboard` 成功分支下，导致本来支持历史日期的体重 API 无法从网页使用。
- 体重展示与饮食 Dashboard 解耦，体重和饮食趋势各自显示错误与重试入口。缺少历史目标时显示中文原因，不再误报后端断线；不创建或回填虚构的历史营养目标。
- 体重卡片新增独立日期控件，默认今天、只允许今天及以前日期。顶部饮食日期/趋势导航会同步体重日期，但在体重卡片单独选日期不会改变饮食日期。查询仍为所选日期截止的 30 天体重趋势。
- 保存沿用 `PUT /api/me/weight-logs/{recordedDate}`；已有日期自动带入原体重并提供“更新体重”，同日修改不会新增重复记录。保存期间禁用体重和日期输入，成功反馈在趋势刷新后保留。
- 本轮本地验证：75 个后端快速测试、2 个真实 PostgreSQL Testcontainers 集成测试、6 个 Vitest 流程测试与生产构建通过。Playwright 桌面/手机 Chromium 共 6 项通过，包含注册后补录昨天体重；Firefox 的 3 项在浏览器启动阶段报 `spawn UNKNOWN`，未执行业务断言。本机开发 Compose 已重建前后端且三服务 healthy，PostgreSQL 原数据卷保留。

## 一键复制前一天饮食（已实现）

当所选日期没有饮食记录时，饮食日记会显示“复制前一天记录”。系统复制食物、克数、餐次和用餐时间，仅把日期换为当前所选日期，并自动刷新 Dashboard 与七日趋势。

- 接口：`POST /api/me/food-logs/copy-day`，请求体包含 `sourceDate` 和 `targetDate`。
- 用户 ID 从 JWT 获取，不能通过请求体复制其他用户的数据。
- 后端在一个事务中完成整天记录的批量复制；来源为空时返回 404，目标日期已有记录时返回 409，避免普通重复提交造成整天热量叠加。
- 两个日期均使用 `@PastOrPresent` 校验，网页端只提供“前一天 → 当前日期”的快捷入口。
- 前端组件：`web/src/components/FoodLogList.tsx`。
- 验证：前端 `npm run build` 成功；后端 `mvn -o test` 共 55 项通过。

## 自定义食物多用户隔离（已实现）

`foods.owner_id` 为空表示共享的内置食物；有值表示某个用户创建的自定义食物。搜索食物、按克数试算、创建饮食记录和修改饮食记录都会在后端校验可见范围，不能仅靠隐藏前端选项。

- `GET /api/foods`、`POST /api/foods` 和 `POST /api/foods/calculate` 现在都需要 JWT。
- 两个用户可以分别创建同名自定义食物；同一用户不能重复创建同名食物；自定义食物也不能与内置食物同名。
- 数据库使用两个不区分大小写的唯一索引：内置食物按名称唯一，自定义食物按 `owner_id + 名称` 唯一。
- Flyway `V2__add_custom_food_ownership.sql` 在全新数据库中加入归属字段，并把全局食物名称约束替换为上述作用域索引。现有本地数据库在接入 Flyway 前已经完成相同结构升级。
- 网页创建自定义食物时明确提示“仅自己可见”。
- 验证：前端 `npm run build` 成功；后端 `mvn -o test` 共 61 项通过。

## 按餐次分组与营养小计（已实现）

饮食日记不再把所有记录平铺在一起，而是固定按早餐、午餐、晚餐、加餐的顺序分组。每个分组显示记录数量、热量小计，以及蛋白质、碳水和脂肪小计；修改一条记录的餐次后，它会自动进入新分组。

- 小计直接由 Dashboard 已返回的 `FoodLog.nutrition` 在 React 中派生，不新增数据库字段或请求，避免存储可以计算得到的重复数据。
- 使用 `useMemo` 仅在 `logs` 变化时重新分组汇总。
- 空餐次不显示，页面保持紧凑；手机窄屏继续使用响应式布局。
- 关键文件：`web/src/components/FoodLogList.tsx`、`web/src/styles.css`。
- 验证：前端 TypeScript 检查与 `npm run build` 成功。

## 自定义食物管理与安全归档（已实现）

主页新增默认收起的“我的自定义食物”面板，可查看私人食物的每 100g 营养、修改食物模板，以及归档不再使用的食物。

- 接口：`GET /api/me/foods/custom`、`PUT /api/me/foods/custom/{foodId}`、`DELETE /api/me/foods/custom/{foodId}`。
- Controller 从 JWT 取得用户 ID，Repository 同时按食物 ID 与 `owner_id` 查询，不能管理其他用户的食物。
- 已用于 `food_logs` 的食物也可以修改；历史记录从自己的营养快照计算，因此只会影响之后新增的记录。
- 删除采用软删除：`foods.active = false`。历史日志仍引用原食物并正常显示，但食物搜索和最近使用列表不再返回它。
- 唯一索引只约束未归档的自定义食物，因此归档后可以用相同名称创建新版本。
- `active` 允许为空以兼容早期内置数据，领域逻辑把旧数据的 `null` 视为启用；Flyway V2 在新环境中保持相同行为。
- 前端组件：`web/src/components/CustomFoodManager.tsx`。
- 验证：前端生产构建成功；真实 PostgreSQL 列、部分唯一索引和 OpenAPI 路由验证成功。

## 饮食记录营养快照（已实现）

`foods` 现在是用于以后录入的食物模板，`food_logs` 则保存已经发生的一餐。每条记录在创建时会复制食物名称及热量、蛋白质、碳水、脂肪的每 100g 数值，Dashboard 和七日趋势均从快照计算。

- 新字段：`food_name_snapshot`、`calories_per_100g_snapshot`、`protein_per_100g_snapshot`、`carbs_per_100g_snapshot`、`fat_per_100g_snapshot`。
- `food_id` 仍然保留，用于关联食物和查询最近使用，但不再决定历史营养结果。
- 修改记录克数、餐次或时间且 `foodId` 不变时保留原快照；改选另一种食物时才刷新快照。
- 复制前一天饮食时复制原记录的快照，而不是读取食物模板的最新值。
- Flyway `V3__add_food_log_nutrition_snapshots.sql` 为已有行从 `foods` 回填快照，再把五个字段设为 `NOT NULL`。
- 前端自定义食物管理已明确提示：修改只影响以后新增记录，历史记录不受影响。
- 验证：新增 4 个领域行为测试；后端共 72 个测试全部通过；前端生产构建通过；真实 PostgreSQL 中 2 条旧记录全部回填，5 个快照字段均为非空约束；OpenAPI 返回 200。

## Flyway 数据库版本管理（已实现）

数据库结构不再由 Hibernate 自动更新。Flyway 在 JPA 启动前执行版本化 SQL，Hibernate 的 `ddl-auto` 改为 `validate`，只负责检查实体与数据库是否一致。

- 依赖：`flyway-core`、`flyway-database-postgresql`。
- 迁移目录：`backend/src/main/resources/db/migration`。
- `V1__create_initial_schema.sql`：创建 9 张业务表、外键、唯一约束和查询索引。
- `V2__add_custom_food_ownership.sql`：加入自定义食物归属、归档字段及作用域唯一索引。
- `V3__add_food_log_nutrition_snapshots.sql`：加入并回填历史营养快照。
- 现有 `public` schema 首次启动时使用 `baseline-on-migrate` 登记为 V3，不重复执行已有结构；数据验证前后均为 2 个用户、85 个食物、2 条饮食记录和 3 条营养目标。
- 在隔离的空 schema 中验证了 V1 → V2 → V3：三次迁移全部成功，Hibernate 校验通过，创建 9 张业务表并初始化 85 个食物。验证 schema 随后已删除，不涉及正式数据。
- 已删除两个临时 Java `ApplicationRunner` 迁移器，后续数据库结构变化只能新增 V4、V5 等 SQL 文件。
- 已经执行过的迁移文件不得修改；Flyway 会校验历史文件 checksum。需要更正时必须新增下一版本迁移。
- 当前 Flyway 10.20.1 在 PostgreSQL 18.6 上验证成功，但启动日志提示其官方测试范围到 PostgreSQL 17；后续升级 Spring Boot/Flyway 时应重新验证。

## Testcontainers PostgreSQL 集成测试（已实现）

空数据库迁移验证已经固化为自动化测试，不再依赖人工创建临时 schema。

- 测试文件：`backend/src/test/java/com/fitpilot/integration/DatabaseMigrationIT.java`。
- `mvn test` 继续只运行 104 个快速单元 / MVC 测试，不要求 Docker 或 Redis。
- `mvn -Pintegration-tests verify` 先运行快速测试，再由 Maven Failsafe 执行 `*IT` 集成测试。
- Testcontainers 启动临时 `postgres:17-alpine`，Spring Boot 的 `@ServiceConnection` 自动注入 JDBC 连接；测试结束后 Ryuk 自动清理容器。
- 集成测试关闭 Flyway 自动基线，从真正的空库执行 V1 → V2 → V3，并让 Hibernate `validate` 检查实体映射。
- 断言内容：迁移版本必须恰好为 1、2、3；必须存在 9 张业务表；`food_logs` 五个营养快照字段必须为 `NOT NULL`；默认食物必须初始化为 85 个；最近食物查询必须对同一种食物只返回最新一条份量和餐次。
- Failsafe 测试 JVM 固定 `api.version=1.40`，用于兼容 Docker Desktop 29 的最低 API 限制，只影响集成测试。
- 集成套件现有 4 个 PostgreSQL 测试（搜索隔离场景同时连接临时 Redis），以及 `LoginRateLimiterIT` 的 5 个、`FoodSearchCacheIT` 的 3 个独立 Redis 测试。容器各自清理，不连接或修改本地业务数据库、缓存与限流状态。

## 最近使用食物（已实现）

食物搜索框为空且被点击时，网页会请求 `GET /api/me/foods/recent?limit=6`，展示当前登录用户最近记录过的不同食物；按最新一次记录时间倒序。每个选项显示上次克数和餐次，点击后自动填入这两个字段，用户确认营养后再保存，减少重复输入且避免误触直接记账。

- `RecentFoodController` 从 JWT 获取用户 ID，`limit` 限制为 1–12；接口受 Bearer Token 保护。
- 返回模型为 `RecentFoodResponse`：保留原有食物营养字段，并增加 `lastGrams`、`lastMealType`、`lastEatenAt`，属于兼容性的 JSON 字段扩展。
- `FoodLogRepository.findRecentlyLoggedFoods` 使用反连接查询，为每种启用食物保留最新一条记录；相同用餐时间再按记录 ID 决定最新项，并继续隔离当前用户。
- 前端在成功写入饮食记录后刷新最近列表；没有历史数据时显示友好引导，而非展示完整食物库。
- 普通关键词搜索仍只选择食物，不会覆盖用户已经输入的份量或餐次；只有“最近记录”选项带入历史值。
- 验证：后端 72 个快速测试通过；前端 2 个 Vitest 流程测试和 `npm run build` 通过；GitHub Actions 上 2 个真实 PostgreSQL Testcontainers 测试以及生产镜像的 9 个 Playwright 执行项全部通过。

## 七日饮食趋势（已实现）

主页通过 `GET /api/me/nutrition-trend?endDate=YYYY-MM-DD&days=7` 展示截至当前选择日期的七日热量趋势。柱子代表实际摄入，横线代表每一天当时生效的目标；切换减脂或增肌计划后，历史日期仍匹配历史目标。

- 后端用一次范围查询读取饮食记录、一次查询读取目标历史，再在 Java 中按日期聚合，避免循环请求七次 Dashboard。
- 未记录日期保留为空柱，但不以 0 kcal 计入平均，防止误导用户。
- 每根趋势柱都是日期按钮；点击有记录的日期可查看当天明细，点击空白日期可直接进入当天补记。选择后复用 `App` 的 `selectedDate` 数据流，同时刷新 Dashboard、饮食趋势和体重趋势。
- 当前日期使用 `aria-current="date"` 和选中样式标记；每个按钮向读屏器说明日期、实际摄入和目标，也支持键盘聚焦与操作。
- 接口从 JWT 读取用户 ID，`days` 限制为 7–30。
- 前端组件：`web/src/components/NutritionTrendPanel.tsx`。
- 验证：前端 3 个 Vitest 流程测试和 `npm run build` 通过；[GitHub Actions 运行 38712121774](https://github.com/zhengyi123452/fitpilot/actions/runs/38712121774) 进一步确认 72 个后端快速测试、2 个 PostgreSQL 集成测试和生产镜像上的 9 个 Playwright 执行项全部通过。
- 新增 `UserProfileServiceTest`：三种目标热量与宏量重算、历史目标保留、同日多次切换和用户不存在。
- 新增 `NutritionPlanControllerTest`：JWT 用户隔离、未登录拒绝、缺失/null/非法目标拒绝。
- 七日趋势的数值逻辑目前主要由后端服务 / MVC 测试覆盖；现有 Playwright 核心流程会加载该面板，但还没有逐项断言图表数值。

## AI 菜品营养估算（已实现）

- 前端入口在食物录入卡片：输入菜名和克数后点击“用 AI 按菜名和克数估算”。
- 页面显示本次热量、三大营养素、可信度和模型采用的做法假设。用户可修改每 100g 数值，或确认后创建食物并直接写入当天饮食记录。
- 后端接口：`POST /api/me/ai-nutrition/estimate`，受 JWT 保护。
- 后端实现采用 OpenAI-compatible Chat Completions + JSON mode；默认配置为 DeepSeek，也可通过环境变量切换兼容供应商。
- 配置：`AI_API_KEY`（必需）、`AI_BASE_URL`（默认 `https://api.deepseek.com`）、`AI_MODEL`（默认 `deepseek-chat`）、`AI_TIMEOUT_SECONDS`（默认 25）。密钥只保留在后端。
- 后端把菜名序列化为数据，提示模型忽略菜名中的指令；模型只估算每 100g 数值，本次克数换算由后端执行。返回值会检查非负、最大值和宏量营养素总量。
- 没有配置密钥时返回 503；供应商失败或模型 JSON 无效时返回 502。复合菜估算有误差，前端保存前必须确认。
- 相关文件：`AiNutritionController`、`AiNutritionEstimateService`、`OpenAiCompatibleChatClient`、`FoodEntryForm.tsx`。
- 当前没有真实 API Key，因此已验证请求构造、解析、换算、异常和鉴权，但尚未调用真实供应商。

## 网页核心流程测试（已实现）

网页端已经接入 Vitest、Testing Library、user-event、jest-dom 和 jsdom，现有十四条测试覆盖跨组件流程、趋势比较、体重记录管理及登录重试。

- 测试文件：`web/src/App.core-flow.test.tsx`。
- 测试环境初始化：`web/src/test/setup.ts`。
- 配置：`web/vite.config.ts` 的 `test` 区块；命令来自 `web/package.json` 的 `test` 和 `test:watch`。
- 覆盖流程：从未登录页面填写邮箱和密码 → 保存 access token → 加载 Dashboard → 搜索并选择熟米饭 → 输入 200g 和午餐 → 预览营养 → 保存饮食记录 → 切换为减脂计划。
- 第二条流程从已有饮食记录生成“最近记录”响应，验证页面显示“上次 280g · 晚餐”，选择后克数和餐次均自动带入。
- 第三条流程点击七日趋势中的昨天，验证日期控件同步更新、请求携带目标日期，并在刷新后标记当前选中柱。
- 其余流程覆盖缺少历史目标时补录/修改体重、体重日期与饮食日期独立操作，以及注册/资料编辑不再提交训练次数。
- 2026-10-17 增加无目标历史日期的饮食补记与返回今天的数据隔离，以及有/无目标日期混合时的趋势差值测试。
- 测试通过全局 `fetch` stub 保留真实 `api.ts` 的调用路径，验证登录请求、饮食记录请求和计划更新请求的 HTTP 方法与 JSON；同时验证登录后的请求包含 `Authorization: Bearer test-access-token`。
- 测试后端是内存中的确定性响应，不连接 8080 或 PostgreSQL。它验证 React 页面协作和前端 API 契约；真实 SQL/Flyway 由后端 Testcontainers 测试负责。
- 这些测试使用内存响应，适合快速、稳定地定位 React 组件协作和 API 契约问题；真实部署链路由下一节的 Playwright 测试补足。
- 新增依赖后发现的 `source-map-js 1.2.1` 审计问题已升级到 1.2.2；最近一次 npm 审计为 0 个已知漏洞。
- 最近验证：`npm test` 为 14 个测试通过；`npm run build` 通过。

## Playwright 真实浏览器端到端测试（已实现）

- 测试文件：`web/e2e/nutrition-core-flow.spec.ts`、`web/e2e/auth-flow.spec.ts`、`web/e2e/login-limit.spec.ts`；配置：`web/playwright.config.ts`。
- 连接正在运行的 `http://127.0.0.1:5173`，不使用 fetch stub。本地开发 Compose 经过 Nginx、Spring Boot、PostgreSQL 与 Redis；CI 则连接把 React 静态文件打入 Spring Boot JAR 的生产镜像、PostgreSQL 与 Redis。
- 覆盖四类场景：错误密码返回 401 且不保存 token；从完整注册表单创建账户并生成轻盈增肌目标；登录后搜索熟米饭、记录 137g 午餐并切换饮食计划；连续失败触发 429 和倒计时，伪造转发头不能绕过限流。
- 测试通过 API 幂等准备专用账户；`finally` 中删除本轮饮食记录，失败时也尽量避免污染后续运行。计划在减脂与维持之间交替，不额外执行“恢复”写入，因为每次计划更新都会形成一条具有业务意义的历史目标版本。
- Playwright 与 Vitest 分工明确：`vite.config.ts` 只扫描 `src/**/*.test.ts(x)`，避免把 `e2e/*.spec.ts` 误交给 Vitest。
- 配置包含桌面 Chromium、桌面 Firefox 和 Pixel 5 手机尺寸 Chromium；三个项目共执行 12 项测试。保持单 worker 串行，避免共享测试账户并发写入。首次运行需执行 `npx playwright install chromium firefox`。
- `playwright.config.ts` 在 CI 环境额外生成 JUnit 与 HTML 报告；本地仍使用简洁列表输出。`playwright-report/` 与 `test-results/` 已被 Git 忽略。
- 2026-10-15 使用隔离的 `fitpilot-e2e-expanded` Compose 项目复验：三个服务均 healthy，桌面与手机 Chromium 共 6 项通过；当前 Windows/Codex 环境启动 Firefox 时被系统以 `spawn UNKNOWN` 拒绝。[https://github.com/zhengyi123452/fitpilot/actions/runs/38669167886](https://github.com/zhengyi123452/fitpilot/actions/runs/38669167886)随后在 Ubuntu runner 验证桌面 Chromium、桌面 Firefox 和手机 Chromium 共 9 项全部通过（浏览器执行 18.4 秒）。临时环境验证后已连同数据卷删除，原 `fitpilot_postgres-data` 未被使用。

## GitHub Actions CI（已接入）

- 工作流：`.github/workflows/ci.yml`；所有分支的 push、pull request 和手动 `workflow_dispatch` 均可触发，没有按路径跳过检查。
- 工作流包含三个 `ubuntu-24.04` 任务：后端与前端先并行，二者通过后再运行端到端任务：
  - `Backend tests and PostgreSQL/Redis integration`：Temurin Java 21，先用 `docker info` 检查 Docker，再运行 `mvn --batch-mode --no-transfer-progress -Pintegration-tests verify`。Surefire 执行 104 个快速测试，Failsafe 执行 12 个 `*IT` 测试（PostgreSQL 4、独立 Redis 8）并在 verify 阶段报告失败；同一次 Maven 生命周期完成测试和后端打包。
  - `Frontend Vitest and production build`：Node.js 22（与前端 Dockerfile 主版本一致），依次执行 `npm ci`、`npm test -- --reporter=default --reporter=junit --outputFile.junit=test-results/vitest.xml`、`npm run build`，覆盖 Vitest、TypeScript 检查和 Vite 生产构建。依赖安装成功时，即使 Vitest 失败仍继续检查构建，但任务仍会失败。
  - `Playwright end-to-end`：安装 Chromium、Firefox 与系统依赖，执行 `docker compose -f compose.deploy.yaml up --build --detach --wait`，再运行 `npm run test:e2e`。它验证与 Render 相同的单服务生产镜像、PostgreSQL 与 Redis；无论成功失败都用相同 Compose 文件删除容器和临时数据卷。
- 后端任务直接使用 runner 的 Docker，由 Testcontainers 创建及清理临时 `postgres:17-alpine` 和 `redis:7.4-alpine`；E2E 任务另起生产镜像、PostgreSQL 与 Redis Compose 环境，使用专用数据库密码和 JWT secret。两者都不访问开发者数据库，也不需要仓库 secrets。
- Maven 缓存按 `backend/pom.xml`、npm 缓存按 `web/package-lock.json` 配置；`npm ci` 严格使用锁文件，不缓存 `node_modules`。
- 工作流只授予 `contents: read`，checkout 不保留凭据；官方 Actions 固定到完整 commit SHA。后端超时 20 分钟、前端超时 10 分钟、E2E 超时 25 分钟；同一分支或 PR 的新运行会取消旧运行。
- 未取消的运行会上传 `backend-test-reports`（Surefire/Failsafe）、`frontend-test-reports`（Vitest JUnit）和 `playwright-report`（JUnit、HTML、截图/trace）。E2E 失败时还会收集 Compose 状态和日志；报告保留 14 天。
- CI 现在覆盖完整应用服务的注册、错误登录和核心饮食 Playwright E2E，并包含桌面 Chromium、桌面 Firefox 与手机尺寸。它构建并验证可直接部署的生产镜像，但不会自动创建或更新线上资源；后续可增加 Render 部署阶段、接口故障重试、WebKit 和无障碍检查，并按团队流程把三个任务设为分支保护的必需检查。
- 配置通过 actionlint 1.7.12 静态检查；2026-10-15 [首次 GitHub CI](https://github.com/zhengyi123452/fitpilot/actions/runs/38661508064)（提交 `e7d0ffc`）两个任务均成功。日志确认 72 个快速测试、1 个 PostgreSQL 集成测试、1 个 Vitest 测试和生产构建全部通过，两个测试报告 artifact 均已上传。
- 2026-10-15 [跨浏览器 GitHub CI](https://github.com/zhengyi123452/fitpilot/actions/runs/38669167886)（提交 `cdfef0c`）三个任务全部通过：72 个快速测试、1 个 PostgreSQL 集成测试、1 个 Vitest、生产构建和 9 个 Playwright 执行项；`backend-test-reports`、`frontend-test-reports`、`playwright-report` 均已上传。
- 2026-10-15 [生产镜像 GitHub CI](https://github.com/zhengyi123452/fitpilot/actions/runs/38691020518)（提交 `d3d1cf3`）三个任务全部通过：72 个快速测试、1 个 Testcontainers 集成测试、1 个 Vitest、前端构建，以及生产单服务镜像上的 9 个 Playwright 执行项（19.3 秒）；三类报告均已上传，临时数据库卷已清理。
- 2026-10-15 [最近份量复用 GitHub CI](https://github.com/zhengyi123452/fitpilot/actions/runs/38709632256)（提交 `d03b3b7`）三个任务全部通过：72 个快速测试、2 个 Testcontainers 集成测试、2 个 Vitest、前端生产构建，以及生产单服务镜像上的 9 个 Playwright 执行项；新增查询在真实 PostgreSQL 17 中验证通过。
- 2026-10-15 [趋势日期导航 GitHub CI](https://github.com/zhengyi123452/fitpilot/actions/runs/38712121774)（提交 `1b29fc6`）三个任务全部通过：72 个快速测试、2 个 Testcontainers 集成测试、3 个 Vitest、前端生产构建，以及生产单服务镜像上的 9 个 Playwright 执行项。

## 生产部署准备（已实现，尚未上线）

- 根目录 `Dockerfile` 使用三阶段构建：Node 22 构建 React，Maven / Java 21 构建包含静态资源的 Spring Boot JAR，最终由非 root `fitpilot` 用户运行 JRE 镜像。
- 浏览器继续访问同源 `/api`。生产环境不需要 Nginx 或 CORS 配置，一个 Web Service 即可同时提供网页和 API。
- Spring Boot Actuator 仅公开 `/actuator/health`，Docker 与 Render 都使用该路径判断数据库和应用是否就绪。首页、构建后的 `/assets/**` 和 Swagger 可公开访问，其余业务 API 仍要求 JWT。
- `application.yml` 同时支持托管平台的 `PORT` 和本地 `SERVER_PORT`；`JWT_ISSUER` 可通过环境变量覆盖。
- `application-production.yml` 关闭 `baseline-on-migrate`。新的生产数据库必须从 V1 开始执行全部迁移，不能把意外存在的 schema 静默登记为 V3；本地早期数据库仍保留兼容基线。
- Render 官方 Blueprint 只允许 PostgreSQL 引用 `connectionString`、`user`、`password`、`database` 等属性。根目录 `render.yaml` 使用这些受支持字段，生成 JWT 密钥，并把数据库限制为私网访问。
- `deploy/entrypoint.sh` 把 Render 的 `postgresql://user:password@host:port/database` 转换为 JDBC URL；用户名和密码仍由独立环境变量传入，不把秘密拼进 JDBC URL。
- `compose.deploy.yaml` 用一个应用容器加 PostgreSQL 在本机复现 Render 拓扑，默认映射到 5173，也可用 `WEB_PORT` 改成独立端口。它使用独立项目名和数据卷，不会接触日常 `fitpilot_postgres-data`。
- `.dockerignore` 排除 Git、Maven 缓存、`node_modules`、构建产物和文档，避免 Windows 依赖覆盖容器内依赖并缩小构建上下文。
- 2026-10-15 本机使用端口 25174 和隔离项目 `fitpilot-deploy-check` 验证：production profile、Render connectionString 转换、Flyway V1 → V3、85 个内置食物、健康端点、首页、JS 资源和 OpenAPI 均正常；未登录业务 API 返回 401；桌面与手机 Chromium 6 项 Playwright 全部通过。
- 线上资源尚未创建。下一步是在 Render Dashboard 从 `render.yaml` 创建 Blueprint，验证自动 HTTPS，再确定真实用户数据的备份与恢复策略；可选 `AI_API_KEY` 应在部署后单独配置，不能提交到 Git。

## 本地运行

### 推荐：Docker Compose 一键运行

根目录新增了可复现环境：

- `compose.yaml`：编排 `postgres`、`redis`、`backend`、`web` 四个服务；
- `backend/Dockerfile`：先用 Maven + Java 21 构建，再用非 root 用户运行 JRE 镜像；
- `web/Dockerfile`：先用 Node 构建，再由 Nginx 提供静态文件；
- `web/nginx.conf`：SPA 路由回退，并把 `/api/**` 代理到 `backend:8080`；
- `.env.example`：本地端口、JWT、数据库和可选 AI 配置模板。

运行：

```powershell
cd D:\code\fitapp
docker compose up --build
```

网页为 `http://localhost:5173`，Swagger 为 `http://localhost:8080/swagger-ui.html`。停止并释放端口：

```powershell
docker compose down
```

容器 PostgreSQL 不发布宿主机端口，后端通过 Docker 私有网络中的服务名 `postgres` 连接它，因此不会和已安装在电脑上的 5432 冲突。数据保存在 `fitpilot_postgres-data` 命名卷中；普通 `down` 保留数据，`down --volumes` 才会删除容器数据库。

网页 API 地址已经改为同源相对路径。开发模式由 Vite 代理 `/api` 到 `localhost:8080`，容器模式由 Nginx 代理到 `backend:8080`。这样部署时无需把访问者电脑上的 `localhost` 写入生产包，也减少了跨域配置。

后端镜像构建使用 BuildKit 的 Maven 缓存挂载（`--mount=type=cache,target=/root/.m2`）。这会复用已经下载的依赖，同时只执行项目实际需要的 Maven 生命周期；不要改回 `dependency:go-offline`，它会预取大量当前构建不会使用的 reporting/Doxia 等插件，首次构建明显更慢。

2026-10-14 已完成三容器联合实跑：

- `postgres:17-alpine`、Spring Boot 后端和 Nginx/React 网页均通过 Docker healthcheck；
- 空命名卷中 Flyway 依次执行 V1、V2、V3，Hibernate 校验通过，并初始化 85 个内置食物；
- `http://localhost:5173/` 与 `http://localhost:8080/v3/api-docs` 均返回 200；
- 通过网页同源 `/api` 代理完成登录、搜索“熟米饭”、记录 200g 午餐、读取 Dashboard 和切换减脂计划；Dashboard 得到 260 kcal 已摄入，目标由 2788/3038 体系切换为 2388 kcal，剩余 2128 kcal；
- 容器数据库包含隔离的烟雾 / E2E 测试账户、85 个食物；烟雾测试保留 1 条饮食记录用于落盘验证，Playwright 创建的记录会在测试结束后删除；
- 网页容器健康检查必须访问 `127.0.0.1`，不能使用 `localhost`。Alpine 可能将后者解析为 `::1`，而当前 Nginx 只监听 IPv4，会造成网页可访问但容器被误标记为 unhealthy。

本次验证使用的是容器专用测试账户与命名卷，不涉及本机 PostgreSQL。这是 2026-10-14 的运行记录；2026-10-15 Docker Desktop 启动遇到 Secrets Engine socket 错误，不能据此认为当前容器仍在运行。恢复 Docker 后可重新用 `docker compose up -d` 启动。

注意：容器数据库和本机 PostgreSQL 是两套独立数据源，所以第一次打开容器版网页需要重新注册。这是环境隔离，不是数据丢失。

### 手动开发模式

### 1. PostgreSQL

确保 PostgreSQL 正在运行，数据库连接默认来自 `backend/src/main/resources/application.yml` 或环境变量：

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/fitpilot"
$env:DB_USERNAME = "fitpilot"
$env:DB_PASSWORD = "<你的本地密码>"
```

### 2. 启动后端

```powershell
cd D:\code\fitapp\backend
mvn spring-boot:run
```

看到 `Started FitPilotApplication` 后，打开 `http://localhost:8080/swagger-ui.html`。

如果提示 `Port 8080 was already in use`，说明旧后端仍在运行。先查找：

```powershell
netstat -ano | Select-String ':8080'
Get-Process -Id <PID>
```

确认它确实是旧的 FitPilot Java 进程后再停止：

```powershell
Stop-Process -Id <PID>
```

此前占用 8080 的 PID 为 `78312`，已在 2026-10-10 停止；以后 PID 会变化，不能照抄。

### 3. 启动前端

另开一个 PowerShell：

```powershell
cd D:\code\fitapp\web
npm run dev
```

浏览器打开 `http://localhost:5173/`。

## 验证命令

前端构建：

```powershell
cd D:\code\fitapp\web
npm run build
```

前端核心流程测试：

```powershell
cd D:\code\fitapp\web
npm test
```

真实浏览器端到端测试（先保持 `docker compose up` 正在运行）：

```powershell
cd D:\code\fitapp\web
npx playwright install chromium  # 每台机器首次运行一次
npm run test:e2e
```

后端测试：

```powershell
cd D:\code\fitapp\backend
$env:MAVEN_OPTS = '-Dmaven.repo.local=D:\code\fitapp\.m2'
mvn -o test
```

真实 PostgreSQL / Redis 集成测试（需要先启动 Docker Desktop）：

```powershell
cd D:\code\fitapp\backend
$env:MAVEN_OPTS = '-Dmaven.repo.local=D:\code\fitapp\.m2'
mvn -o -Pintegration-tests verify
```

当前验证（2026-10-19）：后端 `104` 个快速测试和 `12` 个 Testcontainers 集成测试（PostgreSQL 4、独立 Redis 8）通过；开发 Compose 后端已重建且四服务 healthy，桌面/手机 Chromium `8` 项通过。前端 `14` 个 Vitest、生产构建和包含 Firefox 的 `12` 项浏览器测试在本轮 [GitHub Actions 39191545974](https://github.com/kekevot233/fitpilot/actions/runs/39191545974)（提交 `f2a1746`）全部通过；本机 Firefox 曾报启动 `spawn UNKNOWN`，跨浏览器验收以 Ubuntu CI 为准。

2026-10-13 再次运行后端快速测试时，Maven 在覆盖 `target/classes/application-no-db.yml` 时遇到 Windows `Access denied`；随后尝试离线 `clean`，但工作区 `.m2` 尚未缓存 `maven-clean-plugin:3.4.1`。这不代表 Java 测试失败，本轮也没有修改 Java 业务代码。下一次联网或使用已缓存完整插件的 Maven 仓库执行 `mvn clean test` 即可重新生成该构建目录。

在某些 Windows 权限/文件锁情况下，Maven 可能报 `target\\classes\\application-no-db.yml` 拒绝访问；先确认没有另一个 Maven/IDE 构建占用文件，再重试。不要删除项目目录或数据库来解决这个问题。

## 2026-10-15 本机 Docker Desktop 启动故障与恢复

- 启动报错：`initializing Secrets Engine`，`engine.sock` 重命名为 `.stale` 时出现 `The file cannot be accessed by the system`。这发生在 Docker daemon 就绪之前，不是 Flyway 或业务测试失败。
- 检查时 Docker 进程已经退出；`%LOCALAPPDATA%/docker-secrets-engine` 中仅有一个 0 字节、带 `ReparsePoint` 属性的 `engine.sock`。
- [Docker 上游同类报告](https://github.com/docker/for-win/issues/15064)提供了保留并重命名整个 socket 父目录、让 Docker 重建的临时解决方法。本轮使用 PowerShell `Rename-Item` 尝试时收到 `Access denied`，未改动该目录或 Docker 数据。
- 用户随后选择 Docker Desktop 恢复出厂设置，Docker Engine 29.8.2 一度恢复运行；三个旧 FitPilot 容器仍在停止列表中，`fitpilot_postgres-data` 数据卷也仍存在（创建于 2026-10-14），源码和 Git 工作区未受影响。该操作本次没有清除 FitPilot 数据，但不是保留数据的首选排错方式；以后应先退出 Docker、备份数据并尝试上游的父目录改名方案。
- 当天下午 Docker 又显示 `Wsl/CallMsi/Install/ERROR_NOT_FOUND`。实际检查发现 WSL 3.0.3、Ubuntu、`docker-desktop` 注册和 WSL2 内核均正常，问题是多组早上遗留的 Docker Desktop / backend 进程与新错误对话框并存。
- 使用 `docker desktop stop --timeout 30` 有序停止后，旧 Docker 与挂起的 `wsl.exe` 进程全部退出；随后 `docker desktop start` 恢复 Server 29.8.2。没有注销发行版、删除数据卷或再次 Reset。
- 恢复后 `fitpilot_postgres-data` 仍存在，三个原项目容器重新启动并全部 healthy，`/v3/api-docs` 与网页返回 200。以后遇到相同截图应先检查 `wsl --version`、`wsl -l -v` 和 `docker desktop status`，优先使用 Docker CLI 有序停止 / 启动。

## 文档与数据注意事项

- 复合菜的每 100g 数据只能是参考估算；餐厅用油、糖、酱汁和份量不同，实际值会明显变化。
- 包装食品优先按包装营养标签创建或更新自定义食物。
- 参考来源包含：[中国食物成分查询平台](https://nlc.chinanutri.cn/fq/)、[USDA FoodData Central](https://fdc.nal.usda.gov/)。
- 用户不希望为每条食物数据额外维护“数据来源”字段，因此目前没有 `source` 列。
- 不要在未经用户明确授权时接入 OpenAI、食物拍照识别或复杂 Agent 架构；这些属于后续阶段。

