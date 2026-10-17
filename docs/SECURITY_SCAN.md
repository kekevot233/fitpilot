# 敏感信息扫描记录

> 扫描日期：2026-10-17
> 扫描基准：`63413980b4a8862e4296b7e46d57dd107ebd02c3`

## 范围与方法

- 同步 `origin` 与标签后，使用官方 Gitleaks **8.30.1** 默认规则扫描全部可达 Git 历史，共 **17 个提交**。
- 同时扫描当前项目的 **176 个文件**；文件枚举包含隐藏文件和被 Git 忽略的本地环境文件，排除 `.git`、依赖缓存、构建产物和测试输出。此次未发现非模板的本地 `.env` 文件。
- 扫描器下载自 `gitleaks/gitleaks` 官方 v8.30.1 发布，并与其发布的 SHA-256 校验表核对。
- 启用 `--redact=100`，忽略源文件中可能存在的 `gitleaks:allow` 注释，不添加整目录白名单或关闭密钥检测规则。
- 检查是否跟踪环境配置、私钥和证书容器，并人工核实检测结果与部署配置。此次未发现 `.env` 或私钥文件被 Git 跟踪；敏感文件路径的历史检查也未发现 `.env` 或私钥提交。

## 检测结果

历史扫描与当前文件扫描各命中 **5 项**，均已人工核实为非真实服务凭据。扫描器原始退出码为 1（有命中），不能将此结果表述为“默认规则零命中”。

| 文件 | 判定 |
| --- | --- |
| `backend/src/test/java/com/fitpilot/controller/AiNutritionControllerTest.java` | `WebMvcTest` 范围内使用的固定 JWT 测试值 |
| `backend/src/test/java/com/fitpilot/controller/NutritionPlanControllerTest.java` | `WebMvcTest` 范围内使用的固定 JWT 测试值 |
| `backend/src/test/java/com/fitpilot/controller/NutritionTrendControllerTest.java` | `WebMvcTest` 范围内使用的固定 JWT 测试值 |
| `backend/src/test/java/com/fitpilot/controller/RecentFoodControllerTest.java` | `WebMvcTest` 范围内使用的固定 JWT 测试值 |
| `backend/src/main/resources/db/migration/V1__create_initial_schema.sql` | 通用密钥规则跨行匹配 `PRIMARY KEY` 后的 SQL 字段定义，属于误报 |

**本次扫描未发现真实 API 密钥、GitHub 访问令牌、数据库生产凭据或私钥泄露。** 无需因这些命中修改已经执行的 Flyway 迁移，也未重写 Git 历史。

## 配置核查

- `.env`、`.env.*` 已被 Git 忽略，只有 `.env.example` 作为模板跟踪；AI API Key 模板为空。
- 应用配置、Compose、示例和测试包含公开的本地开发默认密码及 JWT 测试/开发值。这些值不是应保密的生产凭据，**公网部署必须替换默认数据库密码和 JWT 签名值**。
- Render Blueprint 的 JWT 值由平台生成，数据库凭据来自托管数据库引用；仓库未保存这些运行时值。
- 本次是一次性仓库扫描，尚未接入每次提交的自动敏感信息检查。此记录不覆盖依赖目录、运行环境中的密钥，以及远端已删除且当前不可达的历史。

## 本地审计产物

完整脱敏报告保存在被 Git 忽略的构建目录，不随文档推送：

- `backend/target/security-history.json`
- `backend/target/security-working-tree.json`
- `backend/target/security-file-manifest.txt`

历史扫描可复现为：

```powershell
& backend/target/security-tools/gitleaks.exe git . `
  --log-opts='--all --full-history' --ignore-gitleaks-allow `
  --redact=100 --report-format=json `
  --report-path=backend/target/security-history.json
```

之后新增或改变配置、凭据和代码时，应重新扫描；若出现真实凭据，需先撤销或轮换，再处理历史中的泄露内容。
