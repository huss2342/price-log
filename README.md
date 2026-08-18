# Price Log

Photograph a price tag. The app reads the item, the price, the size, and the
store's own markdown conventions, then tells you what it actually costs per unit
and where it was cheapest.

Built for warehouse-club shopping: Costco first, then Sam's Club, Aldi, Walmart.

## What it does that a spreadsheet does not

**Reads the tag, not just the price.** A photo goes to an Azure OpenAI vision
deployment and comes back as structured fields: item, brand, price, regular
price, size, pack count, item number, quality claims, and any markers printed in
the corners.

**Knows what the price ending means.** Warehouse clubs encode the state of an
item in the last two digits and in small printed markers. A Costco `.97` is a
store markdown; `.00` and `.88` are deeper manager markdowns; an asterisk in the
corner means the warehouse is not reordering it. Sam's Club uses a `C` on the
sign and a `.01` ending for final markdowns. The app applies these and answers
the actual question — buy now, or wait for it to come round again.

These conventions are community knowledge, not published policy, so they live in
a `tag_rule` table you can edit or switch off from Settings as you verify them.

**Compares fairly across stores.** Every size is normalized to one unit — ounces
for weight, fluid ounces for volume, count for countable goods — so a 5 lb bag
and a 32 oz bag can be ranked. Products carry two keys: a *normalized key* that
collapses the same SKU seen at different stores, and a *comparison key* that
groups substitutable products across brands. Quality claims are part of the
comparison key, so organic pasture-raised eggs are never priced against
conventional ones.

**Survives a warehouse dead spot.** Photos are queued in IndexedDB and uploaded
when signal returns, so nothing is lost mid-aisle.

## Layout

```
api/    Spring Boot 4.1 on Java 21 — extraction, rules, unit maths, REST API
web/    Angular 20 installable PWA — capture, browse, review, settings
infra/  One idempotent deploy script for the whole stack
```

## Running it locally

The API needs a JDK 21 (Java 25 without `javac` will not compile it):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export AZURE_OPENAI_ENDPOINT=https://prm-openai-eastus.openai.azure.com/
export AZURE_OPENAI_API_KEY=...          # az cognitiveservices account keys list
cd api && ./mvnw spring-boot:run
```

It starts on `http://localhost:8080` with a file-backed H2 database under
`api/data/`, photos under `api/data/photos/`, and no API key required.

```bash
cd web && npm start                       # http://localhost:4200
```

Tests:

```bash
cd api && ./mvnw test
```

## Deploying

One command. Nothing to export — credentials are read from the existing key
vault. The script is idempotent, so re-run it to ship an update.

```bash
./infra/deploy.sh
```

It prints the app URL, the API URL, and a generated API key. Enter the last two
in the app's Settings screen, then use the browser's *Add to Home Screen*.

### What it costs

| Piece | Choice | Monthly |
|---|---|---|
| PWA | Static Web Apps, free tier | $0 |
| API | Container Apps, scales to zero, inside the free grant | ~$0 |
| Photos | Blob Storage, cool tier | ~$0.10 |
| Database | `pricelog` on the existing `prm-db-server-dest` | $0 |
| Tag reading | gpt-5-mini vision, ~$0.001 per photo | ~$0.50 |

No custom domain; the free `*.azurestaticapps.net` hostname is used.

### What it reuses

Three things already existed in the `prm` subscription and are shared rather
than duplicated:

- **`prm-openai-eastus`** — a `tag-extractor` deployment (gpt-5-mini,
  GlobalStandard) was added to it.
- **`prm-db-server-dest`** — a Postgres 17 Burstable B1ms. A separate
  `pricelog` database was created on it with its own `pricelog_app` login.
  That role owns the `pricelog` schema and holds **no privileges on `prm_db`**;
  it can open a connection there but sees zero tables. Because the server is
  shared with prm production, the connection pool is capped at 3.
- **`prm-prod-kv-dest`** — holds `pricelog-db-password`.

Everything this app owns lives in the separate `price-log` resource group, so
the bill stays readable.

The API scales to zero, so the first photo after an idle period waits a few
seconds for a cold start. Setting `--min-replicas 1` removes that but costs
roughly $15/month, which is the single biggest lever on the bill.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/captures` | Upload a tag photo; returns the saved, interpreted observation |
| `GET` | `/api/search?q=` | Comparison groups matching a search |
| `GET` | `/api/categories/{category}` | Comparison groups in a category |
| `GET` | `/api/groups/{comparisonKey}` | One comparison group in full |
| `GET` | `/api/products/{id}/history` | Every price logged for one product |
| `GET` | `/api/observations/pending` | The review queue |
| `PUT` | `/api/observations/{id}` | Correct a reading; re-derives unit price and tag meaning |
| `GET`/`POST` | `/api/stores` | Your warehouses |
| `GET`/`PUT` | `/api/tag-rules` | Store tag conventions |

Everything except `/actuator/health` requires an `X-API-Key` header when
`APP_API_KEY` is set.

## Notes on accuracy

Extractions below 0.85 confidence, and anything with no determinable size, are
flagged `needsReview` and collected in the Review tab rather than silently
trusted. Correcting a product there fixes it for every observation of that item,
past and future, and re-runs the tag rules against the corrected price.
