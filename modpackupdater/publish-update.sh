#!/bin/sh
set -eu
updater_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python3 "$updater_dir/tools/publish_update.py" "$@"
