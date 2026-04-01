#!/bin/bash
# Interactive file watcher for klein type inference.
# Watches the source file and re-runs on changes.
# Press keys to switch type format on the fly.

file=""
pass_args=()
format=""

for arg in "$@"; do
    case "$arg" in
        --ir-bounds|--ir-compact|--canonical)
            format="$arg" ;;
        -*)
            pass_args+=("$arg") ;;
        *)
            [[ -z "$file" ]] && file="$arg"
            pass_args+=("$arg") ;;
    esac
done

if [[ -z "$file" || ! -f "$file" ]]; then
    echo "Usage: watch-infer.sh [options] <file>" >&2
    echo "Keys: b=bounds  c=compact  n=canonical  q=quit" >&2
    exit 1
fi

run_infer() {
    clear
    echo "[${format:---canonical}]  b=bounds  c=compact  n=canonical  q=quit"
    echo ""
    ./klein infer ${format} "${pass_args[@]}"
}

get_mtime() { stat -c %Y "$1" 2>/dev/null; }

run_infer
last_mod=$(get_mtime "$file")
last_bin=$(get_mtime ./klein)

while true; do
    if read -rsn1 -t 0.5 key; then
        case "$key" in
            b) format="--ir-bounds";  run_infer ;;
            c) format="--ir-compact"; run_infer ;;
            n) format="--canonical";  run_infer ;;
            "") format="";            run_infer ;;  # Enter = default
            q) echo; exit 0 ;;
        esac
    fi
    cur_mod=$(get_mtime "$file")
    cur_bin=$(get_mtime ./klein)
    if [[ "$cur_mod" != "$last_mod" || "$cur_bin" != "$last_bin" ]]; then
        last_mod="$cur_mod"
        last_bin="$cur_bin"
        run_infer
    fi
done
