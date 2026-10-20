-- Historical target lookup filters by user/date and takes the latest date/id.
CREATE INDEX idx_nutrition_targets_user_date_id
    ON nutrition_targets (user_id, effective_date DESC, id DESC);

-- The recent-food query checks whether a later record exists for the same user/food.
-- Keep the existing user/eaten_at index for daily and date-range queries.
CREATE INDEX idx_food_logs_user_food_eaten_id
    ON food_logs (user_id, food_id, eaten_at DESC, id DESC);
