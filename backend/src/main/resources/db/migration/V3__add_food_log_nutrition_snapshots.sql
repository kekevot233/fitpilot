ALTER TABLE food_logs
    ADD COLUMN food_name_snapshot varchar(100),
    ADD COLUMN calories_per_100g_snapshot numeric(8, 2),
    ADD COLUMN protein_per_100g_snapshot numeric(6, 2),
    ADD COLUMN carbs_per_100g_snapshot numeric(6, 2),
    ADD COLUMN fat_per_100g_snapshot numeric(6, 2);

UPDATE food_logs log
SET food_name_snapshot = food.name,
    calories_per_100g_snapshot = food.calories_per100g,
    protein_per_100g_snapshot = food.protein_per100g,
    carbs_per_100g_snapshot = food.carbs_per100g,
    fat_per_100g_snapshot = food.fat_per100g
FROM foods food
WHERE log.food_id = food.id;

ALTER TABLE food_logs
    ALTER COLUMN food_name_snapshot SET NOT NULL,
    ALTER COLUMN calories_per_100g_snapshot SET NOT NULL,
    ALTER COLUMN protein_per_100g_snapshot SET NOT NULL,
    ALTER COLUMN carbs_per_100g_snapshot SET NOT NULL,
    ALTER COLUMN fat_per_100g_snapshot SET NOT NULL;
