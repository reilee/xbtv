#!/bin/zsh
set -euo pipefail

# Xcode Cloud discovers hooks alongside tvOS/XBTV.xcodeproj.
TVOS_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CATALOG_PACKAGE="$TVOS_ROOT/XBTV/Core"

# These deterministic host tests gate every Cloud action, including Archive.
# Do not use the live-site smoke check as a release gate.
/usr/bin/env -u SDKROOT -u PLATFORM_NAME xcrun swift test --package-path "$CATALOG_PACKAGE"
