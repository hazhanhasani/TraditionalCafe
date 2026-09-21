#!/usr/bin/env bash
set -euo pipefail

export API_BASE="http://127.0.0.1:8787"
export CAFE_SETUP_KEY="traditionalcafe-local-e2e-key"

echo "Preparing isolated local D1..."
rm -rf .wrangler/state

npx --yes wrangler@latest d1 migrations apply traditionalcafe-db --local

echo "Starting isolated local Worker..."
nohup npx --yes wrangler@latest dev   --local   --ip 127.0.0.1   --port 8787   --var SETUP_KEY:"$CAFE_SETUP_KEY"   > "$RUNNER_TEMP/traditionalcafe-wrangler.log" 2>&1 &

echo $! > "$RUNNER_TEMP/traditionalcafe-wrangler.pid"

for attempt in $(seq 1 60); do
  if curl --fail --silent "$API_BASE/api/health" > "$RUNNER_TEMP/local-health.json" 2>/dev/null; then
    grep -q '"ok":true' "$RUNNER_TEMP/local-health.json"
    grep -q '"database":"ready"' "$RUNNER_TEMP/local-health.json"
    echo "Local E2E Worker is ready."
    exit 0
  fi
  sleep 1
done

echo "Local Worker did not become ready."
cat "$RUNNER_TEMP/traditionalcafe-wrangler.log" || true
exit 1
