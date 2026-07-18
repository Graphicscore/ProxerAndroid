#!/usr/bin/env bash
#
# Enforces that every Compose screen uses the shared ProxerTopAppBar wrapper
# instead of a bare Material3 TopAppBar.
#
# Exceptions:
#   - ui/compose/ProxerTopAppBar.kt   the wrapper itself
#   - anime/stream/StreamScreen.kt    fullscreen player overlay bar
#
set -euo pipefail

cd "$(dirname "$0")/.."

offenders="$(grep -rn --include='*.kt' 'TopAppBar(' src/main/kotlin \
    | grep -v 'ProxerTopAppBar(' \
    | grep -v 'ui/compose/ProxerTopAppBar.kt' \
    | grep -v 'anime/stream/StreamScreen.kt' || true)"

if [ -n "$offenders" ]; then
    echo "Bare Material3 TopAppBar found. Use ProxerTopAppBar instead:" >&2
    echo "$offenders" >&2
    exit 1
fi

echo "OK: all top bars use ProxerTopAppBar (StreamScreen overlay excepted)."
