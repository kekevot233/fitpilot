# FitPilot Web

FitPilot 的网页端饮食记录 MVP，技术栈为 React、TypeScript 和 Vite。

## Development

先启动 `backend`，然后运行：

```powershell
npm install
npm run dev
```

打开 `http://localhost:5173`。

默认 API 地址是 `http://localhost:8080`。如需临时连接其他地址：

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8085"
npm run dev
```

## Testing

运行 Vitest + Testing Library 网页测试：

```powershell
npm test
```

持续监听文件变化：

```powershell
npm run test:watch
```

核心流程测试从登录页面开始，模拟后端 `fetch` 响应，完成搜索食物、按克数计算、保存饮食记录和切换减脂计划，并核对 JWT 请求头及请求体。它不会连接或修改本地 PostgreSQL。

## Current scope

- 邮箱密码注册与登录
- 注册时录入身体信息并自动生成营养目标
- JWT 登录状态与用户数据隔离
- 今日目标与已摄入营养进度
- 每日体重记录与近 30 天趋势、7 日移动平均
- 按名称搜索食物
- 输入克数并预览热量和三大营养素
- 保存当天饮食记录
- 切换日期并补录历史饮食
- 修改记录的克数和餐次
- 查看和删除当天记录
- 按早餐、午餐、晚餐和加餐分组，并查看每餐热量与三大营养素小计
- 食物不存在时按营养标签创建自定义食物
- 查看、修改未使用的自定义食物，并安全归档已使用的食物

登录令牌保存在浏览器的 `localStorage` 中。它适合当前学习型 MVP；上线前还需要补充 HTTPS、令牌刷新/注销策略和更严格的生产环境密钥管理。
