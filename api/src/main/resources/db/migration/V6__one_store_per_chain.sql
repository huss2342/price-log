-- Prices barely move between locations of the same chain, so a store is now
-- just its chain: "Costco", not "Costco Tustin". Fold any duplicate locations
-- into the oldest row for their chain, then make that the rule.
UPDATE price_observation
SET store_id = (
    SELECT MIN(same_chain.id) FROM store same_chain
    WHERE same_chain.chain = (SELECT own.chain FROM store own WHERE own.id = price_observation.store_id)
);

DELETE FROM store WHERE id NOT IN (SELECT MIN(id) FROM store GROUP BY chain);

CREATE UNIQUE INDEX ux_store_chain ON store (chain);

-- What the item is, without brand or size: "chicken sausage". It used to exist
-- only inside comparison_key, so a correction rebuilt the key from the display
-- name and quietly moved the product out of its group. Older rows leave it null
-- and read it back out of the key.
ALTER TABLE product ADD COLUMN commodity VARCHAR(128);

-- A tag with a savings block was read as full price whenever its price ended
-- in .99, because the .99 rule never looked at the regular price printed beside
-- it. Correct the rows already recorded that way.
UPDATE price_observation
SET sale_signal = 'INSTANT_SAVINGS', tag_insights = NULL, advice = NULL
WHERE sale_signal = 'REGULAR'
  AND regular_price_cents IS NOT NULL
  AND regular_price_cents > price_cents;

-- The last day a published promotion runs, so a deal can say when it ends.
ALTER TABLE deal_sighting ADD COLUMN valid_until DATE;
