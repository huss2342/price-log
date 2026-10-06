# Price Log — Production Hardening Audit

Date: 2026-10-06. Branch: `production-hardening`.
Audited: full `api/` (Spring Boot 4.1 / Java 21, JPA + Flyway, H2 dev / Postgres prod)
and `web/` (Angular 20 PWA). Both baselines build green.

## What the app is today

Single-user prototype. Auth = one shared `X-API-Key` (blank key disables auth entirely —
fail-open). "A store is just its chain": V6 folded locations into one row per chain with a
unique index on `chain`; `StoreController` is GET-only by design; `Store.city/state` are
vestigial. The log endpoint returns the whole dataset; the frontend keeps a full copy in
localStorage. Demo mode serves bundled sample data with no key.

## Missing features (the production list)

### A. User accounts (Huss requested; biggest gap)
- No user concept anywhere in schema or code.
- Need: `app_user` (email unique, BCrypt hash, display name), register/login/me/
  change-password/delete-account endpoints, JWT (HS256, 30d), per-user data isolation.
- `user_id` FK on: `store`, `price_observation`, `tag_rule`, `user_product_watch`
  (new join table; `watched`/`targetPriceCents` move OFF the shared `product` row).
- `product` stays a shared catalog; **every product read must be scoped through the
  user's own observations** (join/filter on `price_observation.user_id`).
- `deal_sighting`/`deal_refresh` stay global (Costco's published data is the same for all).
- Legacy `X-API-Key` keeps working and maps to the first (owner) user — preserves the
  deployed setup link flow and demo mode.
- New users get a copy of the owner's tag rules at registration.

### B. Store management (Huss requested: add stores, rename stores)
- Backend: `POST /api/stores`, `PUT /api/stores/{id}`, `DELETE /api/stores/{id}`
  (409 when observations exist). Drop V6's unique-on-`chain` index; unique becomes
  `(user_id, chain, label)`. `StoreResolver.forChain` becomes per-user
  (resolve-or-create the user's default store for a chain). Capture accepts `storeId`
  to log at a named store. Race on first-capture insert: catch
  `DataIntegrityViolationException` and re-read.
- Frontend: stores list/add/rename/delete UI, store picker on capture page and item
  edit form, "Manage stores" entry point.

### C. Validation & error handling
- `@Valid` + constraints on `WatchRequest` (targetPriceCents ≥ 0), tag-rule update
  (priority ≥ 0), `ObservationUpdate` (sizeValue/packCount > 0, observedOn not future).
- Null-guard `photo.getContentType()` in capture.
- `POST /api/deals/refresh` replaces GET `?force=true` (GET-with-side-effect);
  keep GET force as deprecated alias.

### D. API surface for scale
- `GET /api/observations?page=&size=` → paged `{content,page,size,totalElements,totalPages}`;
  no params → full array (backward compatible).
- `GET /api/export` → JSON download of the user's data (stores, observations+products,
  tag rules, watchlist).
- `POST /api/tag-rules` + `DELETE /api/tag-rules/{id}` (list+update exist).
- OpenAPI via springdoc (3.1.1) + `@Tag`/`@Operation` on controllers.

### E. Tests
- Backend: auth flow tests, per-user isolation tests, store CRUD tests, web-layer
  (MockMvc) tests for new endpoints. Existing suite must stay green.
- Frontend: no spec files exist today; add a small bootstrap (pricing utils) if a
  headless browser is available, else defer.

### F. Frontend accounts & UX
- Login/register pages, token storage, auth interceptor (Bearer; falls back to
  `X-API-Key` when a legacy key is configured and no token), guards, account section
  in settings (profile, change password, export data, delete account).
- Toast/confirm UX for the new flows; service-worker update prompt; ngsw dataGroups
  for API GETs; chain filter on browse.

### G. Ops
- `JWT_SECRET` env (documented in `infra/deploy.env.example` + `deploy.sh` key-vault
  wiring); startup warning when using the insecure dev default.
- `updated_at` on `store` and `price_observation`.

## Locked API contract (backend ↔ frontend)

### Auth — `web/AuthController.java`
| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/api/auth/register` | `{email, password, displayName?}` | 201 `{id,email,displayName,token}`; 409 duplicate email; 422 weak/invalid |
| POST | `/api/auth/login` | `{email, password}` | 200 `{id,email,displayName,token}`; 401 bad credentials |
| GET | `/api/auth/me` | — | 200 `{id,email,displayName,createdAt}`; 401 |
| PUT | `/api/auth/password` | `{currentPassword, newPassword}` | 204; 401/403 wrong current; 422 weak new |
| DELETE | `/api/auth/account` | — | 204; deletes user + all their rows + their photos |

- Header: `Authorization: Bearer <jwt>`. JWT = HS256, subject = user id, 30-day expiry,
  secret from `pricelog.auth.jwt-secret` (`${JWT_SECRET:price-log-dev-secret-change-me}`).
- `security/JwtService.java` (issue/verify; jjwt 0.12.6: `jjwt-api`, `jjwt-impl`,
  `jjwt-jackson`), `security/JwtAuthFilter.java` (runs BEFORE `ApiKeyFilter`;
  on valid token sets `security/CurrentUser` (request-scoped bean: id + email);
  on invalid/expired token → 401, do NOT fall through).
- `X-API-Key` (valid) → acts as the first user (owner). No token + no key → existing
  behavior (401, or public-read GETs when enabled).
- Passwords: BCrypt via `spring-security-crypto` (`BCryptPasswordEncoder`, strength 10).
  Email normalized lowercase, max 254; password min 8 chars.
- Registration seeds the new user's tag rules by copying the owner's rules
  (`INSERT ... SELECT FROM tag_rule WHERE user_id = :ownerId`); if no owner rules
  exist, seed from `V2__seed_tag_rules.sql` content (keep a Java constant list —
  do NOT re-run V2).

### Stores — `web/StoreController.java` (extend)
- `GET /api/stores` → `[{id, chain, label, city, state, createdAt, updatedAt,
  observationCount}]` scoped to current user, ordered by chain then label.
  (New fields are additive; old clients ignore them.)
- `POST /api/stores` `{chain, label, city?, state?}` → 201; 409 duplicate
  `(chain,label)` for this user; 422 on blank label / unknown chain.
- `PUT /api/stores/{id}` `{label?, city?, state?}` → 200; 404 if not the user's;
  409 on rename collision.
- `DELETE /api/stores/{id}` → 204; 404 if not the user's; 409 if observations exist
  (`{"error":"Store has N logged prices. Move or delete them first."}` — no
  reassignment UI in v1).
- `POST /api/captures`: `storeId` param (already exists as legacy alias — verify and
  honor: must belong to current user, else 404) selects the exact store; `chain`
  param keeps current behavior via per-user `StoreResolver.forChain`.

### Tag rules — `web/TagRuleController.java` (extend)
- `POST /api/tag-rules` `{chain, matchType, pattern, signal, meaning, advice?, priority?}`
  → 201; 422 on invalid enum/blank.
- `DELETE /api/tag-rules/{id}` → 204; 404 if not the user's.

### Observations
- `GET /api/observations?page=0&size=50` → 
...[truncated 4107 chars]