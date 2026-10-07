-- Stores are now per-user and user-managed: the V6 rule of one row per chain
-- becomes one row per (user, chain, label), so "Costco" and "Costco Tustin"
-- can coexist for the same person without colliding with anyone else's.

DROP INDEX IF EXISTS ux_store_chain;
DROP INDEX IF EXISTS ux_store_chain_label;

CREATE UNIQUE INDEX ux_store_user_chain_label ON store (user_id, chain, label);
