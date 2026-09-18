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

**Compares fairly.** Every size is normalized to one unit — ounces for weight,
fluid ounces for volume, count for countable goods — so a 5 lb bag and a 32 oz
bag can be ranked. Products carry two keys: a *normalized key* that collapses
the same SKU seen twice, and a *comparison key* built from what the item is
("chicken sausage") that groups substitutes across brands and flavours. Quality
claims are part of the comparison key, so organic pasture-raised eggs are never
priced against conventional ones. A group is ranked twice: as the items usually
cost, and as they ring up today with whatever sale is running.

**Says when something you buy goes on sale.** Costco's published savings are
matched to your log by item number and stated as before and after, with the day
they end — alongside any sale still running on a tag you photographed.

**Opens instantly.** The whole log is kept on the device and every screen reads
from that copy, refreshing it behind the scenes, so a sleeping API never stands
between you and your own prices.

**Survives a warehouse dead spot.** Photos are queued in IndexedDB and uploaded
when signal returns, so nothing is lost mid-aisle.

A store is just its chain. Prices barely move between two Costcos, so there are
no locations to name.

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
export AZURE_OPENAI_ENDPOINT=https://your-account.openai.azure.com/
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
   it to `ghcr.io/<owner>/price-log-api`. GHCR is free; an Azure Container
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

One person: whoever holds the API key. Nothing is readable without it.

Showing the app to someone does not require opening the log. A browser with no
key runs against a built-in sample dataset instead of the API -- product
comparisons across three clubs, with the pack sizes, sales and markdowns that
make the unit-price ranking worth looking at. It is labelled as sample data on
every screen, it reaches no network at all, and so it also sidesteps the cold
start a visitor would otherwise sit through. `?demo=1` forces it on for the
owner's own browser; `?demo=0` clears it.

A deployment that does want to publish its real log can set `PUBLIC_READ=true`,
which opens every GET. Even then the spending stays shut: captures, mutations,
and `force=true` on the deals endpoint all still require the key, and anonymous
reads are rate limited to a 60-request burst refilling at one per second per
address -- not to protect the model budget, which they cannot reach, but the
database, a Burstable B1ms shared with a production app behind a three
connection pool.

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
| Database | `pricelog` on an existing Postgres server | $0 |
| Tag reading | gpt-5-mini vision, ~$0.001 per photo | ~$0.50 |

No custom domain; the free `*.azurestaticapps.net` hostname is used.

### What it reuses

Three things already existed in the subscription and are shared rather than
duplicated. All three are named in `infra/deploy.env`, which is not committed --
see `deploy.env.example`.

- **An Azure OpenAI account** — a `tag-extractor` deployment (gpt-5-mini,
  GlobalStandard) was added to it.
- **A Postgres 17 Burstable B1ms.** A separate `pricelog` database was created
  on it with its own login. That role owns the `pricelog` schema and holds **no
  privileges on anything else the server hosts**; it can open a connection but
  sees zero tables. Because the server is shared with a production workload, the
  connection pool is capped at 3.
- **A key vault** — holds the database password.

Everything this app owns lives in its own `price-log` resource group, so the
bill stays readable.

The API scales to zero, so the first request after an idle period pays a cold
start: measured at about 35 seconds to a healthy `/actuator/health`, of which
roughly 10 is Spring Boot starting and the rest is pulling and scheduling the
container. Every subsequent capture takes about 6 seconds. Setting
`--min-replicas 1` removes the cold start but costs roughly $15/month, which is
the single biggest lever on the bill.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/captures?chain=` | Upload a tag photo; returns the saved, interpreted observation |
| `GET` | `/api/observations` | The whole log, newest first; what the app keeps on the device |
| `GET` | `/api/observations/pending` | Readings flagged for a second look |
| `PUT` | `/api/observations/{id}` | Correct a reading; re-derives unit price, grouping and tag meaning |
| `DELETE` | `/api/observations/{id}` | Remove a reading, and the photo it came from |
| `GET` | `/api/deals` | Costco's current savings on items in the log, before and after |
| `GET` | `/api/search?q=` | Comparison groups matching a search |
| `GET` | `/api/categories/{category}` | Comparison groups in a category |
| `GET` | `/api/groups/{comparisonKey}` | One comparison group in full |
| `GET` | `/api/products/{id}/history` | Every price logged for one product |
| `GET` | `/api/stores` | The chains prices have been logged at |
| `GET`/`PUT` | `/api/tag-rules` | Store tag conventions |

Everything except `/actuator/health` requires an `X-API-Key` header when
`APP_API_KEY` is set.

## Notes on accuracy

Extractions below 0.85 confidence, and anything with no determinable size, are
flagged `needsReview` and listed first in Entries rather than silently trusted.
Correcting a product fixes it for every observation of that item, past and
future, and re-runs the tag rules against the corrected price. Changing what an
item is compared as moves it into that group.
