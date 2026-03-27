#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# setup-akka-secrets.sh
#
# Creates or updates Akka secrets for the Papercast service.
# Run this once before the first deployment, and again whenever secrets change.
#
# Usage:
#   ./scripts/setup-akka-secrets.sh [--project <project-name>]
#
# Prerequisites:
#   - akka CLI installed and authenticated  (akka auth login)
#   - .env file in the project root with the values below
#
# .env format:
#   OPENAI_API_KEY=sk-...
#   ELEVENLABS_API_KEY=...      # optional
#   NCBI_API_KEY=...            # optional
#   S2_API_KEY=...              # optional
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERR]${NC}  $*" >&2; exit 1; }

# ── Parse arguments ───────────────────────────────────────────────────────────
PROJECT=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --project) PROJECT="$2"; shift 2 ;;
    *) error "Unknown argument: $1" ;;
  esac
done

PROJECT_FLAG=""
[[ -n "$PROJECT" ]] && PROJECT_FLAG="--project $PROJECT"

# ── Load .env ─────────────────────────────────────────────────────────────────
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  error ".env file not found at $ENV_FILE\n  Copy .env.example to .env and fill in your values."
fi

info "Loading $ENV_FILE"
while IFS= read -r line; do
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ -z "$line" ]]               && continue
  if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    value="${value%\"}"   # strip surrounding quotes
    value="${value#\"}"
    export "$key=$value"
  fi
done < "$ENV_FILE"

# ── Validate required vars ────────────────────────────────────────────────────
[[ -z "${OPENAI_API_KEY:-}" ]]     && error "OPENAI_API_KEY is not set in .env"
[[ "$OPENAI_API_KEY" == your-* ]] && error "OPENAI_API_KEY still contains a placeholder value"

# ── Upsert helper ─────────────────────────────────────────────────────────────
upsert_secret() {
  local name="$1"; shift
  local literals=("$@")

  if akka secret get "$name" $PROJECT_FLAG &>/dev/null 2>&1; then
    info "Updating secret: $name"
    akka secret update generic "$name" $PROJECT_FLAG "${literals[@]}"
  else
    info "Creating secret: $name"
    akka secret create generic "$name" $PROJECT_FLAG "${literals[@]}"
  fi
  success "$name"
}

# ── papercast-secrets ─────────────────────────────────────────────────────────
info "Setting up papercast-secrets..."

literals=(
  "--literal" "openai-api-key=${OPENAI_API_KEY}"
  "--literal" "elevenlabs-api-key=${ELEVENLABS_API_KEY:-}"
  "--literal" "ncbi-api-key=${NCBI_API_KEY:-}"
  "--literal" "s2-api-key=${S2_API_KEY:-}"
)

upsert_secret "papercast-secrets" "${literals[@]}"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
success "Secrets configured."
echo ""
echo "  Next: configure the Auth0 JWT key so Akka can validate bearer tokens."
echo "  You will need the Auth0 public key PEM file for your tenant."
echo ""
echo "  1. Download the current signing certificate from:"
echo "     https://<your-tenant>.auth0.com/pem"
echo "     (save it as auth0-public.pem)"
echo ""
echo "  2. Create the asymmetric secret:"
echo "     akka secret create asymmetric auth0-jwt-key \\"
echo "       --public-key auth0-public.pem $PROJECT_FLAG"
echo ""
echo "  3. Register the key with the service (replace <your-tenant>):"
echo "     akka services jwts add papercast \\"
echo "       --key-id auth0 \\"
echo "       --algorithm RS256 \\"
echo "       --issuer https://<your-tenant>.auth0.com/ \\"
echo "       --secret auth0-jwt-key $PROJECT_FLAG"
echo ""
