-- Seed tag semantics. These are community-known heuristics, not published by
-- the retailers, so they are seeded as editable data. Correct them in the app
-- as you verify them at the register.

INSERT INTO tag_rule (chain, match_type, pattern, signal, meaning, advice, priority) VALUES
-- Costco -------------------------------------------------------------------
('COSTCO', 'MARKER', 'ASTERISK', 'DISCONTINUED',
 'Asterisk in the top-right corner: last run of this item, warehouse is not reordering.',
 'Buy now if you want it. It disappears when the stock is gone, not on a sale cycle.', 10),

('COSTCO', 'PRICE_ENDING', '.00', 'MANAGER_MARKDOWN',
 'Price ending in .00: manager markdown, usually the deepest cut on the item.',
 'Lowest it will go. Often a display or short-dated unit, so check the item.', 20),

('COSTCO', 'PRICE_ENDING', '.88', 'MANAGER_MARKDOWN',
 'Price ending in .88: manager markdown, often damaged, returned, or display stock.',
 'Deep cut, inspect before buying.', 20),

('COSTCO', 'PRICE_ENDING', '.97', 'CLEARANCE',
 'Price ending in .97: store-level clearance markdown.',
 'Genuine markdown, but it may drop again to .00 before it sells out.', 30),

('COSTCO', 'PRICE_ENDING', '.49', 'INSTANT_SAVINGS',
 'Price ending in .49 or .79: manufacturer promotion, part of a scheduled sale cycle.',
 'Recurs. If you can wait, this price usually comes back within a few months.', 40),

('COSTCO', 'PRICE_ENDING', '.79', 'INSTANT_SAVINGS',
 'Price ending in .49 or .79: manufacturer promotion, part of a scheduled sale cycle.',
 'Recurs. If you can wait, this price usually comes back within a few months.', 40),

('COSTCO', 'TEXT_CONTAINS', 'OFF', 'INSTANT_SAVINGS',
 'Instant savings block with a date range: scheduled manufacturer promotion.',
 'Runs on a cycle, typically monthly. Expect it again.', 50),

('COSTCO', 'PRICE_ENDING', '.99', 'REGULAR',
 'Price ending in .99: standard everyday price, no discount applied.',
 'Full price. Worth waiting if the item cycles onto instant savings.', 90),

-- Sam''s Club ---------------------------------------------------------------
('SAMS_CLUB', 'MARKER', 'C_MARKER', 'CLEARANCE',
 'A "C" printed on the sign: clearance item.',
 'Being cleared out. Stock will not be replenished at this price.', 10),

('SAMS_CLUB', 'PRICE_ENDING', '.01', 'CLEARANCE',
 'Price ending in .01: final markdown, last stage before the item is pulled.',
 'Cheapest it gets. Buy now or lose it.', 15),

('SAMS_CLUB', 'PRICE_ENDING', '.00', 'MANAGER_MARKDOWN',
 'Price ending in .00: manager markdown.',
 'Real markdown, but a further cut to .01 is still possible.', 25),

('SAMS_CLUB', 'TEXT_CONTAINS', 'INSTANT SAVINGS', 'INSTANT_SAVINGS',
 'Instant Savings promotion with a date range.',
 'Scheduled promotion, recurs on a cycle.', 50),

('SAMS_CLUB', 'PRICE_ENDING', '.98', 'REGULAR',
 'Price ending in .98: standard everyday price.',
 'Full price.', 90),

-- Walmart ------------------------------------------------------------------
('WALMART', 'PRICE_ENDING', '.00', 'CLEARANCE',
 'Price ending in .00 on a yellow tag: clearance.',
 'Clearance, will not be restocked at this price.', 20),

('WALMART', 'TEXT_CONTAINS', 'ROLLBACK', 'INSTANT_SAVINGS',
 'Rollback: temporary price reduction, typically 90 days.',
 'Temporary. Price returns to the regular level when the rollback ends.', 40),

('WALMART', 'TEXT_CONTAINS', 'CLEARANCE', 'CLEARANCE',
 'Explicit clearance tag.',
 'Being cleared out.', 20),

-- Aldi ---------------------------------------------------------------------
('ALDI', 'TEXT_CONTAINS', 'ALDI FIND', 'DISCONTINUED',
 'Aldi Find (Special Buy): one-time limited stock, not part of the regular range.',
 'Will not come back. Buy now if you want it.', 10),

('ALDI', 'TEXT_CONTAINS', 'RED TAG', 'CLEARANCE',
 'Red tag markdown: item being cleared.',
 'Clearance, stock will not return.', 20);
