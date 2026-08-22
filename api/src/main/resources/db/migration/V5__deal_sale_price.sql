-- Costco prints the promotional price and the amount taken off as two separate
-- things ("$23.99" then "After $6 OFF"). Storing only one of them made a $6
-- saving look like a $23 one, so both are kept.
ALTER TABLE deal_sighting ADD COLUMN sale_price_cents INT;
