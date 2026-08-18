#!/usr/bin/env bash
#
# Deploys Price Log to Azure. Idempotent: safe to re-run to push an update.
#
# Costs, at one person's shopping volume:
#   Container Apps   scale-to-zero, inside the monthly free grant   ~$0
#   Static Web Apps  free tier                                      $0
#   Blob Storage     cool tier, a few thousand photos              ~$0.10/mo
#   Azure OpenAI     ~$0.001 per photo read                        ~$0.50/mo
#   PostgreSQL       the `pricelog` database on the existing
#                    my-postgres-server, already paid for           $0
#
# Credentials are read from the existing key vault, so nothing needs to be
# exported before running this.
#
set -euo pipefail

RG="${RG:-price-log}"
LOCATION="${LOCATION:-eastus}"
APP="${APP:-price-log-api}"
ENVIRONMENT="${ENVIRONMENT:-price-log-env}"
SWA="${SWA:-price-log-web}"
# Static Web Apps is only offered in a handful of regions, and eastus is not one
# of them. The region only decides where the control plane record lives; the
# site itself is served from the global edge either way.
SWA_LOCATION="${SWA_LOCATION:-eastus2}"
# Storage account names must be globally unique and lowercase alphanumeric.
STORAGE="${STORAGE:-pricelogphotos$(az account show --query id -o tsv | tr -d '-' | cut -c1-8)}"
STORAGE_CONTAINER="${STORAGE_CONTAINER:-tag-photos}"

# Shared resources are reused from the existing production group rather than
# duplicated: the model quota, the database server, and the key vault all
# already exist and are already paid for.
SHARED_RG="${SHARED_RG:-my-shared-rg}"
OPENAI_ACCOUNT="${OPENAI_ACCOUNT:-my-openai-account}"
OPENAI_DEPLOYMENT="${OPENAI_DEPLOYMENT:-tag-extractor}"
KEYVAULT="${KEYVAULT:-my-key-vault}"

# The `pricelog` database and the `pricelog_app` login live on the shared
# server but are isolated from the-shared-database: that role holds no privileges there.
DB_SERVER="${DB_SERVER:-my-postgres-server}"
DB_NAME="${DB_NAME:-pricelog}"
DB_USER="${DB_USER:-pricelog_app}"
DB_SECRET="${DB_SECRET:-pricelog-db-password}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Database credentials"
DB_HOST=$(az postgres flexible-server show -n "$DB_SERVER" -g "$SHARED_RG" \
  --query fullyQualifiedDomainName -o tsv)
DATABASE_URL="jdbc:postgresql://$DB_HOST:5432/$DB_NAME?sslmode=require"
DATABASE_PASSWORD=$(az keyvault secret show --vault-name "$KEYVAULT" -n "$DB_SECRET" \
  --query value -o tsv)

if [[ -z "$DATABASE_PASSWORD" ]]; then
  echo "ERROR: secret $DB_SECRET not found in key vault $KEYVAULT." >&2
  exit 1
fi

echo "==> Resource group"
az group create -n "$RG" -l "$LOCATION" -o none

echo "==> Storage for tag photos"
az storage account create -n "$STORAGE" -g "$RG" -l "$LOCATION" \
  --sku Standard_LRS --kind StorageV2 --access-tier Cool \
  --allow-blob-public-access false -o none
STORAGE_CONNECTION=$(az storage account show-connection-string -n "$STORAGE" -g "$RG" -o tsv)

echo "==> Azure OpenAI credentials"
OPENAI_ENDPOINT=$(az cognitiveservices account show -n "$OPENAI_ACCOUNT" -g "$SHARED_RG" \
  --query properties.endpoint -o tsv)
OPENAI_KEY=$(az cognitiveservices account keys list -n "$OPENAI_ACCOUNT" -g "$SHARED_RG" \
  --query key1 -o tsv)

# One shared secret guards the whole API. Generated once, then reused on
# subsequent deploys so the phone does not have to be reconfigured.
if EXISTING_KEY=$(az containerapp secret show -n "$APP" -g "$RG" --secret-name app-api-key \
     --query value -o tsv 2>/dev/null); then
  API_KEY="$EXISTING_KEY"
  echo "==> Reusing the existing API key"
else
  API_KEY=$(openssl rand -hex 24)
  echo "==> Generated a new API key"
fi

echo "==> Container Apps environment"
az containerapp env create -n "$ENVIRONMENT" -g "$RG" -l "$LOCATION" -o none 2>/dev/null || true

echo "==> Building and deploying the API (remote build, no local Docker needed)"
az containerapp up \
  --name "$APP" \
  --resource-group "$RG" \
  --environment "$ENVIRONMENT" \
  --location "$LOCATION" \
  --source "$ROOT/api" \
  --ingress external \
  --target-port 8080 \
  -o none

API_FQDN=$(az containerapp show -n "$APP" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv)
API_URL="https://$API_FQDN"

echo "==> Secrets and configuration"
az containerapp secret set -n "$APP" -g "$RG" --secrets \
  "db-password=$DATABASE_PASSWORD" \
  "openai-key=$OPENAI_KEY" \
  "storage-connection=$STORAGE_CONNECTION" \
  "app-api-key=$API_KEY" -o none

# The front end origin is not known until the Static Web App exists, so CORS is
# set after it is created, below.
az containerapp update -n "$APP" -g "$RG" \
  --min-replicas 0 --max-replicas 1 \
  --cpu 0.5 --memory 1.0Gi \
  --set-env-vars \
    "SPRING_PROFILES_ACTIVE=prod" \
    "DATABASE_URL=$DATABASE_URL" \
    "DATABASE_USER=$DB_USER" \
    "DATABASE_PASSWORD=secretref:db-password" \
    "AZURE_OPENAI_ENDPOINT=$OPENAI_ENDPOINT" \
    "AZURE_OPENAI_API_KEY=secretref:openai-key" \
    "AZURE_OPENAI_DEPLOYMENT=$OPENAI_DEPLOYMENT" \
    "PHOTO_MODE=blob" \
    "STORAGE_CONNECTION_STRING=secretref:storage-connection" \
    "STORAGE_CONTAINER=$STORAGE_CONTAINER" \
    "APP_API_KEY=secretref:app-api-key" \
  -o none

echo "==> Static Web App for the PWA"
az staticwebapp create -n "$SWA" -g "$RG" -l "$SWA_LOCATION" --sku Free -o none 2>/dev/null || true
SWA_TOKEN=$(az staticwebapp secrets list -n "$SWA" -g "$RG" --query properties.apiKey -o tsv)
SWA_HOST=$(az staticwebapp show -n "$SWA" -g "$RG" --query defaultHostname -o tsv)

echo "==> Building the PWA"
(cd "$ROOT/web" && npm ci && npx ng build --configuration production)

echo "==> Uploading the PWA"
npx --yes @azure/static-web-apps-cli@latest deploy \
  "$ROOT/web/dist/web/browser" \
  --deployment-token "$SWA_TOKEN" \
  --env production

echo "==> Allowing the PWA origin through CORS"
az containerapp update -n "$APP" -g "$RG" \
  --set-env-vars "ALLOWED_ORIGINS=https://$SWA_HOST" -o none

cat <<SUMMARY

Deployed.

  App       https://$SWA_HOST
  API       $API_URL
  API key   $API_KEY

Open the app on your phone, go to Settings, and enter the API address and key
above. Then use the browser's "Add to Home Screen" to install it.

The API scales to zero when unused, so the first photo after an idle period
takes a few extra seconds while it starts.
SUMMARY
