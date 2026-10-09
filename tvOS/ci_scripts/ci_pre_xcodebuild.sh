#!/bin/zsh
set -euo pipefail

TVOS_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${CI_BUILD_NUMBER:-}" ]]; then
    print 'Local invocation: CI_BUILD_NUMBER is absent; keeping local version metadata.'
    exit 0
fi
if [[ ! "$CI_BUILD_NUMBER" =~ ^[1-9][0-9]*$ ]]; then
    print -u2 'CI_BUILD_NUMBER must be a positive integer.'
    exit 1
fi
BUILD_COMMIT="${CI_COMMIT:-$(git -C "$TVOS_ROOT" rev-parse HEAD)}"
if [[ ! "$BUILD_COMMIT" =~ ^[[:xdigit:]]{40}$ && ! "$BUILD_COMMIT" =~ ^[[:xdigit:]]{64}$ ]]; then
    print -u2 'CI_COMMIT must be a full Git commit hash.'
    exit 1
fi

cd "$TVOS_ROOT"
xcrun agvtool new-version -all "$CI_BUILD_NUMBER"
INFO_PLIST="$TVOS_ROOT/XBTV/Resources/Info.plist"
if /usr/libexec/PlistBuddy -c 'Print :GitCommit' "$INFO_PLIST" >/dev/null 2>&1; then
    /usr/libexec/PlistBuddy -c "Set :GitCommit $BUILD_COMMIT" "$INFO_PLIST"
else
    /usr/libexec/PlistBuddy -c "Add :GitCommit string $BUILD_COMMIT" "$INFO_PLIST"
fi
print "Prepared Cloud build $CI_BUILD_NUMBER."
