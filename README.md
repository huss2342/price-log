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

Two stages, split so that no Azure credentials ever live on GitHub and no
container registry has to be paid for:

1. **GitHub Actions builds the API image** on every push to `main` and pushes
   it to `ghcr.io/huss2342/price-log-api`. GHCR is free; an Azure Container
   Registry would cost about $5 a month, several times the rest of this app.
2. **`infra/deploy.sh` deploys**, run locally against the `az` CLI. It reads
   every credential from the existing key vault, so nothing needs exporting.

```bash
./infra/deploy.sh
```

It prints a setup link that carries the API URL and key. Open it once per
device and the app configures itself, then use *Add to Home Screen*. On iOS an
installed app gets storage separate from Safari, so open the link a second time
from inside the installed app.

### Who can use it

One person: whoever holds the API key.

The site itself is public, and is deliberately empty — a static shell with no
key in it, which can read nothing until one is supplied. It used to sit behind
a Static Web Apps GitHub sign-in, but that gate only ever protected the
delivery of the key, not the data: the API is on the public internet either
way, and the key is what guards it. The gate also broke on iOS, where Safari's
cross-site tracking prevention blocks the cookie exchange with
`identity.7.azurestaticapps.net`, leaving the app unusable on the one device it
was built for.

So the key is the whole story, and it is treated accordingly: sent only as the
`X-API-Key` header, never as a query parameter, so it stays out of browser
history and access logs. Photos are fetched as bytes and shown from an object
URL rather than pointed at with an `<img src>` carrying the secret.

To revoke every device at once, rotate the key and redeploy:

```bash
az containerapp secret set -n price-log-api -g price-log   --secrets "app-api-key=$(openssl rand -hex 24)"
```

The script pulls a GHCR token from `gh auth token` by default. For a
longer-lived credential, export `GHCR_TOKEN` with a fine-grained personal
access token that only has `read:packages`.

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

The API scales to zero, so the first request after an idle period pays a cold
start: measured at about 35 seconds to a healthy `/actuator/health`, of which
roughly 10 is Spring Boot starting and the rest is pulling and scheduling the
container. Every subsequent capture takes about 6 seconds. Setting
`--min-replicas 1` removes the cold start but costs roughly $15/month, which is
the single biggest lever on the bill.

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
| `DELETE` | `/api/observations/{id}` | Remove a reading, and the photo it came from |

Everything except `/actuator/health` requires an `X-API-Key` header when
`APP_API_KEY` is set.

## Notes on accuracy

Extractions below 0.85 confidence, and anything with no determinable size, are
flagged `needsReview` and collected in the Review tab rather than silently
trusted. Correcting a product there fixes it for every observation of that item,
past and future, and re-runs the tag rules against the corrected price.
