# FitPilot Backend

## Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 16+（保存用户资料时需要）

## Swagger UI

Start the backend with PostgreSQL enabled:

```powershell
mvn spring-boot:run
```

Then open the interactive API documentation:

```text
http://localhost:8080/swagger-ui.html
```

Expand an API group and endpoint, click **Try it out**, fill in path parameters or a JSON body, and click **Execute**. The generated OpenAPI descriptions are also available at:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/v3/api-docs.yaml
```

先通过 `POST /api/auth/register` 或 `POST /api/auth/login` 获得 `accessToken`。测试需要登录的接口时，点击 Swagger 页面右上角的 **Authorize**，粘贴 token；Swagger 会自动添加 `Authorization: Bearer <token>` 请求头。

## Start the nutrition API without PostgreSQL

`/api/nutrition/calculate` 是纯计算接口。为了在尚未创建数据表时也能启动项目，使用 `no-db` 配置关闭数据库自动配置：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=no-db"
```

PowerShell 测试请求：

```powershell
$body = @{
  gender = "MALE"
  age = 22
  heightCm = 183
  weightKg = 76
  activityLevel = "MODERATE"
  goal = "LEAN_BULK"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/nutrition/calculate -ContentType "application/json" -Body $body
```

## Calculation rules (MVP)

- BMR: Mifflin-St Jeor（男性常数 `+5`，女性常数 `-161`）
- TDEE: BMR × 活动系数；`MODERATE` 为 1.55
- 减脂 / 维持 / 轻盈增肌：TDEE 分别 -400 / 0 / +250 kcal
- 蛋白质：2.0 g/kg；脂肪：0.9 g/kg；剩余热量给碳水

这些参数集中在 `NutritionCalculationService` 和两个枚举中，之后可以根据产品测试结果调整。

## Register and save a user profile (PostgreSQL required)

Create a database and a local application user first:

```sql
CREATE USER fitpilot WITH PASSWORD 'fitpilot';
CREATE DATABASE fitpilot OWNER fitpilot;
```

The default local connection is `jdbc:postgresql://localhost:5432/fitpilot`. Override it without editing tracked files when necessary:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/fitpilot"
$env:DB_USERNAME = "fitpilot"
$env:DB_PASSWORD = "fitpilot"
mvn spring-boot:run
```

## Database migrations

Flyway owns the PostgreSQL schema. Migration files live in `src/main/resources/db/migration` and run automatically before Hibernate starts. Hibernate uses `ddl-auto: validate`, so it reports a mismatch instead of silently changing tables.

The current migration chain is:

```text
V1__create_initial_schema.sql
V2__add_custom_food_ownership.sql
V3__add_food_log_nutrition_snapshots.sql
```

Existing MVP databases are baselined at V3 on their first Flyway-managed startup. Empty databases execute V1 through V3. Never edit a migration that has already been applied: create a new `V4__description.sql` file for the next schema change. Flyway verifies migration checksums and will reject an edited historical migration.

## Automated tests

Run the fast unit and MVC tests during normal development:

```powershell
mvn test
```

Run the complete test suite, including an isolated PostgreSQL integration test:

```powershell
mvn -Pintegration-tests verify
```

The integration profile requires Docker Desktop. Testcontainers starts temporary `postgres:17-alpine` and Ryuk containers, applies Flyway V1 through V3 to an empty database, starts the Spring context with Hibernate validation, checks the schema and default food data, and removes the containers afterward. It never connects to the local `fitpilot` database. The Failsafe profile pins Docker API `1.40` for compatibility with newer Docker Desktop engines; this setting affects tests only.

When the application starts, Flyway creates or upgrades the schema and JPA validates that the entities match it. Register with:

```powershell
$body = @{
  email = "zhengyi@example.com"
  password = "FitPilot123"
  nickname = "Zhengyi"
  gender = "MALE"
  age = 22
  heightCm = 183
  weightKg = 76
  activityLevel = "MODERATE"
  goal = "LEAN_BULK"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register -ContentType "application/json" -Body $body
```

This hashes the password with BCrypt, creates one row in `users`, calculates the nutrition target, then creates its linked row in `nutrition_targets`. Both database writes run in one transaction. The response contains a signed JWT access token and the new user profile.

`weeklyTrainingFrequency` is optional (1–7 when supplied) for compatibility with the legacy training API. Registration defaults it to 3 when omitted. Nutrition calculations use `activityLevel` and do not use training frequency.

Log in later with:

```powershell
$loginBody = @{
  email = "zhengyi@example.com"
  password = "FitPilot123"
} | ConvertTo-Json

$auth = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $loginBody
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }
```

Protected requests in the following examples must include `-Headers $headers`. The backend reads the `userId` from the signed token and rejects access to a different user's records with HTTP `403`.

## Login failure protection

Login requires Redis (`REDIS_URL`, default `redis://localhost:6379`). Compose supplies a private Redis service. The default policy allows five failed attempts per normalized email and source in a 900-second window; the fifth failure and subsequent attempts return `429` with `Retry-After` and `retryAfterSeconds`. A successful login clears that bucket. Missing accounts follow the same failure policy.

For a backend run directly with Maven, a local-only Redis instance can be started with:

```powershell
docker run --detach --rm --name fitpilot-dev-redis -p 127.0.0.1:6379:6379 redis:7.4-alpine
$env:REDIS_URL = "redis://localhost:6379"
```

Redis Lua scripts reserve capacity before checking passwords, so parallel requests across backend instances cannot bypass the failure budget. In-flight requests temporarily occupy capacity and have a 30-second lease to recover from crashed requests. Busy capacity returns a one-second retry; failure windows expire from the first failure without being extended by retries.

Only configured `LOGIN_TRUSTED_PROXIES` may supply forwarded source addresses; otherwise the TCP peer is used. Development Compose trusts only its `web` service, whose Nginx replaces forwarding headers. Direct public deployments should configure only their actual proxy chain. Redis keys contain a SHA-256 bucket digest and random reservation IDs, not raw email, IP, password or JWT values.

`LOGIN_MAX_FAILURES`, `LOGIN_WINDOW_SECONDS` and `LOGIN_ATTEMPT_LEASE_SECONDS` configure the policy. When Redis cannot enforce protection, login returns a safe `503` with a five-second retry. Existing authenticated data operations continue, and core health intentionally excludes Redis. Redis state loss resets throttles: Compose persists AOF data, while the example free Render Key Value instance is not durable across restarts.

## Read a dashboard profile

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/auth/me -Headers $headers | ConvertTo-Json -Depth 3
```

This endpoint returns the user's profile together with the latest nutrition target. The future mobile Dashboard can use this single response to display the current calorie and macro goals.

## Update body profile and recalculate targets

Update the authenticated user's planning inputs:

```powershell
$body = @{
  age = 23
  heightCm = 183
  weightKg = 75.5
  activityLevel = "MODERATE"
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri http://localhost:8080/api/me/profile `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

The profile update and the newly calculated nutrition target run in one transaction. The new target is effective today while older targets remain available for historical dashboards. Daily weight logs are intentionally independent and do not automatically change the planning weight or calorie target.

Omitting `weeklyTrainingFrequency` preserves its saved value; the nutrition profile form no longer asks for this field.

## Food and daily nutrition

Built-in foods are shared by every account. Foods created manually or from an AI estimate belong to the authenticated user and are not returned to other users. Food search, nutrition calculation, and food-log writes all enforce this rule on the backend. Therefore `GET /api/foods`, `POST /api/foods`, and `POST /api/foods/calculate` require a Bearer token.

Authenticated users can manage their own active custom foods through `GET /api/me/foods/custom`, `PUT /api/me/foods/custom/{foodId}`, and `DELETE /api/me/foods/custom/{foodId}`. Delete archives a food instead of removing its row so existing logs keep their reference. Each food log stores the food name and per-100g nutrition as a snapshot. Therefore an existing custom food can be edited safely: the change affects new logs, while historical dashboards and trends keep their original values.

### AI dish nutrition estimate

The web food-entry panel can estimate a dish from its name and serving weight, show the model's assumptions, and let the user confirm or edit the values before recording it. The API key stays on the backend and is never sent to the browser.

The default provider is DeepSeek's OpenAI-compatible Chat Completions API. Set the key before starting the backend:

```powershell
$env:AI_API_KEY = "your-api-key"
$env:AI_BASE_URL = "https://api.deepseek.com"
$env:AI_MODEL = "deepseek-chat"
mvn spring-boot:run
```

To use another provider that supports Chat Completions and JSON mode, change `AI_BASE_URL` and `AI_MODEL`. For example, OpenAI uses a base URL ending in `/v1`:

```powershell
$env:AI_BASE_URL = "https://api.openai.com/v1"
$env:AI_MODEL = "your-chat-model"
```

The protected endpoint is `POST /api/me/ai-nutrition/estimate`:

```json
{
  "dishName": "番茄炒蛋",
  "grams": 280
}
```

The backend asks the model for per-100g values, validates their ranges, calculates the serving totals itself, and returns a confidence level and assumptions. A missing key returns HTTP `503`; provider or malformed-output failures return HTTP `502`. AI estimates can vary substantially with oil, sugar, ingredients, and cooking method, so the web UI requires confirmation before saving.

Create a food whose nutrition values are specified per 100 grams:

```powershell
$body = @{
  name = "熟米饭"
  caloriesPer100g = 130
  proteinPer100g = 2.69
  carbsPer100g = 28.17
  fatPer100g = 0.28
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/foods -Headers $headers -ContentType "application/json" -Body $body
```

List all foods with `GET /api/foods`, or search by name with:

```text
GET /api/foods?keyword=米饭
```

For the food-entry shortcut menu, read the current user's recently logged foods (newest distinct food first):

```text
GET /api/me/foods/recent?limit=6
```

This endpoint requires the Bearer token. It takes the user ID from that token, so one user cannot read another user's food history.

Preview nutrition for a weight without saving a food log:

```json
POST /api/foods/calculate

{
  "foodId": 1,
  "grams": 350
}
```

For cooked rice with 130 kcal per 100g, the result is `130 × 350 ÷ 100 = 455 kcal`.
The same formula is used for protein, carbohydrates, and fat. Use a returned food ID to record a meal:

```powershell
$body = @{
  foodId = 1
  grams = 350
  mealType = "LUNCH"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/users/1/food-logs -Headers $headers -ContentType "application/json" -Body $body
```

`eatenAt` is optional and defaults to the current local time. Supported meal types are `BREAKFAST`, `LUNCH`, `DINNER`, and `SNACK`.

Read the daily target, consumed nutrition, remaining nutrition, and logs:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/users/1/dashboard?date=2026-09-13" -Headers $headers |
  ConvertTo-Json -Depth 6
```

Omit `date` to use today's date.

Historical intake is available even when the selected date predates the user's first nutrition target. The dashboard returns HTTP `200` with `target: null` and `remaining: null`, while `consumed` and `foodLogs` remain available. No historical target is created or inferred from the current plan. A missing user still returns HTTP `404`.

Read the recent nutrition trend ending on a selected date:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/me/nutrition-trend?endDate=2026-10-11&days=7" `
  -Headers $headers |
  ConvertTo-Json -Depth 5
```

The response includes every calendar date in the range, the calorie target effective on that date, consumed calories and macros, and the number of food logs. Average intake excludes dates with no logs so an unrecorded day is not incorrectly treated as a zero-calorie day. `days` accepts 7–30.

In the trend response, `targetCalories: 0` means that date has no target. `averageTargetCalories` includes only logged dates with a target. The web average difference is calculated from those same dates' intake and targets; it does not subtract averages from different sets of dates.

## Weight tracking and trends

Each user can keep one official weight entry for each date. Repeating the same request updates that day's value instead of creating a duplicate record:

```powershell
$weightBody = @{ weightKg = 75.80 } | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri http://localhost:8080/api/me/weight-logs/2026-10-10 `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $weightBody
```

Delete an incorrect entry with `DELETE /api/me/weight-logs/YYYY-MM-DD` and the Bearer token. The server deletes only the authenticated user's record on that date, returns HTTP `204` on success and `404` when that user has no matching record. Other users' records on the same date are unaffected. The web UI asks for confirmation and refreshes the trend after deletion; profile weight and nutrition targets are independent.

Read the latest 30 calendar days, including a seven-day moving average calculated from the available records in each trailing seven-day window:

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/me/weight-trend?endDate=2026-10-10&days=30" `
  -Headers $headers |
  ConvertTo-Json -Depth 5
```

The `/api/me/...` endpoints obtain the user ID from the verified JWT rather than a user ID supplied by the browser. This prevents a logged-in user from changing another user's history by editing a URL.

Update an existing food log. If `eatenAt` is omitted, its original time is kept:

```powershell
$body = @{
  foodId = 2
  grams = 200
  mealType = "DINNER"
} | ConvertTo-Json

Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/users/1/food-logs/2 -Headers $headers -ContentType "application/json" -Body $body
```

Delete a food log:

```powershell
Invoke-RestMethod -Method Delete -Uri http://localhost:8080/api/users/1/food-logs/2 -Headers $headers
```

Copy all food logs from one day into an empty target day:

```powershell
$body = @{
  sourceDate = "2026-10-11"
  targetDate = "2026-10-12"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/me/food-logs/copy-day `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

The endpoint takes the user ID from the JWT, keeps each entry's food, grams, meal type, and time, and only changes the date. The target day must be empty to protect against ordinary duplicate submissions.

### Local JWT settings

The default secret in `application.yml` is only for local development. Before deployment, set a long random secret without committing it:

```powershell
$env:JWT_SECRET = "replace-with-a-long-random-production-secret"
```

Access tokens currently expire after 24 hours. Override that duration with `JWT_EXPIRATION_MINUTES` when needed.

On startup, the application inserts or calibrates 85 foods. Besides staple foods, protein sources, vegetables, fruit, dairy, nuts, and oils, the catalog now includes Chinese home-style dishes, breakfasts, snacks, and common meals such as fish-flavored shredded pork, kung pao chicken, buns, dumplings, Lanzhou beef noodles, and Shaxian chicken-leg rice. The editable catalog is stored in `src/main/resources/data/default-foods.json` instead of being hard-coded in Java.

All values are per 100g. Ingredient values follow the USDA FoodData Central conventions; the Chinese compound-food entries are practical reference estimates compiled from public nutrition databases and common product labels. Recipe food is inherently less precise: oil, sugar, sauce, edible portion, and a restaurant's serving method can materially change its calories and macros. Use these entries to start logging, but use the package label for packaged food and create a custom food when a restaurant or recipe provides better information. Useful reference points include the [China Food Composition Database](https://nlc.chinanutri.cn/fq/), [USDA Foundation Foods documentation](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/), and [downloadable USDA datasets](https://fdc.nal.usda.gov/download-datasets/).

## Training plans

Generate and save a beginner plan using the user's `weeklyTrainingFrequency`:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/users/1/training-plans/generate -Headers $headers |
  ConvertTo-Json -Depth 6
```

Read the active plan:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/users/1/training-plans/current -Headers $headers |
  ConvertTo-Json -Depth 6
```

For 1-3 sessions per week, the MVP generates rotating full-body days. For 4-7 sessions, it rotates upper/lower days. Generating a new plan deactivates the old plan without deleting its history.

## Workout records

Record a workout and all of its sets in one transaction:

```powershell
$body = @{
  trainingPlanId = 1
  workoutDate = "2026-09-13"
  workoutType = "UPPER_A"
  sets = @(
    @{ exerciseName = "杠铃卧推"; setNumber = 1; weightKg = 70; reps = 10; rir = 2 }
    @{ exerciseName = "杠铃卧推"; setNumber = 2; weightKg = 70; reps = 9; rir = 2 }
    @{ exerciseName = "杠铃卧推"; setNumber = 3; weightKg = 70; reps = 8; rir = 1 }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/users/1/workouts -Headers $headers -ContentType "application/json" -Body $body
```

Read workout history:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/users/1/workouts -Headers $headers |
  ConvertTo-Json -Depth 6
```
