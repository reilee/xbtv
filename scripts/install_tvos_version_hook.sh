#!/bin/sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
hooks_dir=$(git rev-parse --git-path hooks)
mkdir -p "$hooks_dir"
cat > "$hooks_dir/pre-commit" <<'HOOK'
#!/bin/sh
set -eu
repo_root=$(git rev-parse --show-toplevel)
"$repo_root/scripts/bump_tvos_marketing_version.sh"
HOOK
chmod +x "$hooks_dir/pre-commit"
printf 'Installed tvOS version bump hook in %s\n' "$hooks_dir/pre-commit"
