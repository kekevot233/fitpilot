# FitPilot MVP Architecture

第一阶段采用 Spring Boot 单体 REST API。它足够简单，适合学习和快速交付，也为后续训练模块、移动端和 AI 能力保留空间。

```text
React + TypeScript web app
    |
 REST / JSON
    |
Controller -> Service -> Repository -> PostgreSQL
```

营养试算接口只走 `Controller -> Service`，因为它是纯计算；账户、营养目标、饮食日志和体重数据则通过 Repository 持久化到 PostgreSQL。除注册和登录外，网页请求携带 JWT，后端从 token 获取当前用户身份。

## First persistence flow

```text
POST /api/auth/register
    -> AuthService / UserProfileService (transaction)
        -> save user row
        -> calculate nutrition target
        -> save nutrition_targets row linked by user_id
```

`nutrition_targets` stores a snapshot rather than recalculating historical targets when a user later changes weight or goals. Flyway applies versioned SQL migrations before JPA starts; Hibernate uses `ddl-auto: validate` and only checks that the resulting schema matches the entity model.

The database path also has an isolated integration test. The Maven `integration-tests` profile uses Testcontainers to start PostgreSQL 17, applies the full Flyway chain to an empty database, loads the Spring context, and verifies the resulting schema and seed data. Regular `mvn test` remains Docker-free for fast feedback.

The web path has a separate component-integration test built with Vitest, Testing Library, and jsdom. It drives the main login, food logging, and nutrition-plan flow through rendered UI while stubbing `fetch`, so the real frontend API adapter and JWT request contract are exercised without requiring a running backend.

Dashboard 通过 `GET /api/me/dashboard?date=YYYY-MM-DD` 读取指定日期数据。服务按日期选择当时有效的营养目标，并聚合当天饮食记录。这使历史日期不会被用户今天修改的新目标覆盖。

## Food logging flow

```text
foods (可修改的食物模板，每 100g 营养)
  -> food_logs (food_id + 名称/营养快照 + grams + meal + time)
  -> FoodLogService (从快照计算并汇总)
  -> GET /api/me/dashboard
       target + consumed + remaining + today's logs
```

Nutrition decimals use `BigDecimal` so repeated additions do not accumulate binary floating-point errors. The `(user_id, eaten_at)` database index supports the main query: all food logs for one user on one date.

Built-in foods are shared rows with a null `owner_id`. A custom food stores its creator in `owner_id`; every search, calculation, and food-log write checks that the food is either built-in or owned by the authenticated user. PostgreSQL unique indexes enforce one built-in name globally and one custom name per user.

Food logs copy the selected food's name and per-100g nutrition when created. The foreign key remains useful for recent-food queries, but historical calories no longer depend on the current `foods` row. This lets users correct a custom food template without rewriting previous days.

## Training flow

```text
weeklyTrainingFrequency
  -> generate training_plans + training_plan_exercises
  -> record workouts + workout_sets
  -> read workout history
```

Generating a new plan marks previous plans inactive rather than deleting them. `Workout` may reference the plan used for that session, while every actual set keeps its exercise name, weight, reps, and RIR. This preserves history and leaves room for double-progression suggestions later.

## Package convention

- `controller`: HTTP 请求与响应
- `service`: 业务规则与计算
- `dto`: API 输入输出对象
- `domain`: 枚举和 JPA 实体
- `exception`: 统一参数校验错误响应
