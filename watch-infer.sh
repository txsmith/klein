#!/bin/bash
# Interactive file watcher for the klein pipeline.
# Watches the source file (and the ./klein binary) and re-runs on changes.
# Press keys to switch pipeline stage on the fly.

file=""
pass_args=()
mode="core"

for arg in "$@"; do
    case "$arg" in
        --check) mode="check" ;;
        --core)  mode="core" ;;
        --run)   mode="run" ;;
        -*)
            pass_args+=("$arg") ;;
        *)
            [[ -z "$file" ]] && file="$arg"
            pass_args+=("$arg") ;;
    esac
done

if [[ -z "$file" || ! -f "$file" ]]; then
    echo "Usage: watch-infer.sh [--check|--core|--run] <file>" >&2
    echo "Keys: c=check  o=core  r=run  q=quit" >&2
    exit 1
fi

run_stage() {
    clear
    echo "[${mode}]  c=check  o=core  r=run  q=quit"
    echo ""
    ./klein "${mode}" "${pass_args[@]}"
}

get_mtime() { stat -c %Y "$1" 2>/dev/null; }

run_stage
last_mod=$(get_mtime "$file")
last_bin=$(get_mtime ./klein)

while true; do
    if read -rsn1 -t 0.5 key; then
        case "$key" in
            c) mode="check"; run_stage ;;
            o) mode="core";  run_stage ;;
            r) mode="run";   run_stage ;;
            q) echo; exit 0 ;;
        esac
    fi
    cur_mod=$(get_mtime "$file")
    cur_bin=$(get_mtime ./klein)
    if [[ "$cur_mod" != "$last_mod" || "$cur_bin" != "$last_bin" ]]; then
        last_mod="$cur_mod"
        last_bin="$cur_bin"
        run_stage
    fi
done
