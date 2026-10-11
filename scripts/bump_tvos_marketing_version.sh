#!/bin/sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
version_file="$repo_root/tvOS/project.yml"
project_file="$repo_root/tvOS/XBTV.xcodeproj/project.pbxproj"

current=$(sed -n 's/^[[:space:]]*MARKETING_VERSION:[[:space:]]*"\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)"[[:space:]]*$/\1/p' "$version_file")
if [ -z "$current" ]; then
    echo "Could not read MARKETING_VERSION from tvOS/project.yml" >&2
    exit 1
fi

major=${current%%.*}
remainder=${current#*.}
minor=${remainder%%.*}
patch=${remainder#*.}
next="$major.$minor.$((patch + 1))"

python3 - "$version_file" "$project_file" "$current" "$next" <<'PY'
from pathlib import Path
import re
import sys

version_path, project_path = map(Path, sys.argv[1:3])
current, next_version = sys.argv[3:5]

for path in (version_path, project_path):
    source = path.read_text()
    updated, count = re.subn(
        rf'(?m)^(\s*(?:MARKETING_VERSION\s*=\s*|MARKETING_VERSION:\s*")?){re.escape(current)}("?;?\s*)$',
        lambda match: f"{match.group(1)}{next_version}{match.group(2)}",
        source,
    )
    if count == 0:
        raise SystemExit(f"Could not find {current} in {path}")
    path.write_text(updated)
PY

# Stage only version configuration. Update the staged project from its staged
# base so unrelated generated-project edits remain unstaged.
git add -- "$version_file"
staged_project=$(mktemp)
trap 'rm -f "$staged_project"' EXIT HUP INT TERM
git show ":tvOS/XBTV.xcodeproj/project.pbxproj" > "$staged_project"
python3 - "$staged_project" "$current" "$next" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
current, next_version = sys.argv[2:4]
source = path.read_text()
updated, count = re.subn(
    rf'(?m)^(\s*MARKETING_VERSION\s*=\s*){re.escape(current)}(;\s*)$',
    lambda match: f"{match.group(1)}{next_version}{match.group(2)}",
    source,
)
if count == 0:
    raise SystemExit("Could not update staged Xcode MARKETING_VERSION")
path.write_text(updated)
PY
git hash-object -w "$staged_project" >/dev/null
git update-index --cacheinfo "100644,$(git hash-object "$staged_project"),tvOS/XBTV.xcodeproj/project.pbxproj"
printf 'tvOS marketing version: %s -> %s\n' "$current" "$next"
