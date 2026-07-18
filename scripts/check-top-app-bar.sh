#!/usr/bin/env bash
#
# Enforces that Compose screens use the shared accent-colored wrappers rather than the bare
# Material3 components, whose defaults are keyed to `surface` and clash with the app bar.
#
#   TopAppBar        -> ProxerTopAppBar
#   *TabRow          -> ProxerTabRow / ProxerScrollableTabRow
#
# Exceptions:
#   - ui/compose/ProxerTopAppBar.kt   the bar wrapper itself
#   - ui/compose/ProxerTabRow.kt      the tab wrappers themselves
#   - anime/stream/StreamScreen.kt    fullscreen player overlay bar, deliberately translucent
#
set -euo pipefail

cd "$(dirname "$0")/.."

readonly SRC=src/main/kotlin

# Path exclusions are anchored to the start of the grep output ("path:line:") so that a source line
# whose *content* happens to mention an excepted file cannot mask a genuine offender elsewhere.
exempt() {
    grep -v "^${SRC}/me/proxer/app/ui/compose/ProxerTopAppBar\.kt:" \
        | grep -v "^${SRC}/me/proxer/app/ui/compose/ProxerTabRow\.kt:" \
        | grep -v "^${SRC}/me/proxer/app/anime/stream/StreamScreen\.kt:"
}

# The leading boundary keeps `ProxerTopAppBar(` / `ProxerTabRow(` from matching, while still
# catching `CenterAlignedTopAppBar(`, `LargeTopAppBar(`, `PrimaryTabRow(` and friends.
offenders="$(grep -rnE --include='*.kt' '(^|[^A-Za-z])([A-Za-z]*TopAppBar|[A-Za-z]*TabRow)\(' "$SRC" \
    | grep -vE '(^|[^A-Za-z])Proxer(TopAppBar|ScrollableTabRow|TabRow|TopAppBarSearchField)\(' \
    | exempt || true)"

if [ -n "$offenders" ]; then
    echo "Bare Material3 top bar or tab row found. Use the Proxer* wrappers in ui/compose:" >&2
    echo "$offenders" >&2
    exit 1
fi

echo "OK: all top bars and tab rows use the Proxer* wrappers (StreamScreen overlay excepted)."
