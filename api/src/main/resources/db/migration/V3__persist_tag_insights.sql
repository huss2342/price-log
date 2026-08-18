-- The tag reading is the most useful thing the app produces, so it is stored
-- alongside the price instead of being recomputed only at capture time. It is
-- also a record of what the rules said on the day the tag was photographed.

ALTER TABLE price_observation ADD COLUMN tag_insights TEXT;
ALTER TABLE price_observation ADD COLUMN advice VARCHAR(255);
