#!/bin/sh
set -e
BASE="http://chroma:8000"

# Tenant may already exist on restart — ignore failure
curl -s -X POST "$BASE/api/v2/tenants" \
     -H "Content-Type: application/json" \
     -d '{"name":"SpringAiTenant"}' || true

# Database must be created; Spring AI won't work without it
curl -sf -X POST "$BASE/api/v2/tenants/SpringAiTenant/databases" \
     -H "Content-Type: application/json" \
     -d '{"name":"SpringAiDatabase"}' || true

echo "Chroma init complete"
