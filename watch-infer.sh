#!/bin/bash
# Interactive file watcher for the klein pipeline.
# Watches the rule file, the contract file and the ./klein binary, and re-runs on changes.
# Press keys to switch pipeline stage on the fly.

file=""
contract=""
release=""
pass_args=()
mode="core"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check) mode="check" ;;
        --core)  mode="core" ;;
        --run)   mode="run" ;;
        --contract)
            contract="$2"; shift ;;
        --contract=*)
            contract="${1#*=}" ;;
        --release)
            release="$2"; shift ;;
        --release=*)
            release="${1#*=}" ;;
        -*)
            pass_args+=("$1") ;;
        *)
            if [[ -z "$file" ]]; then file="$1"; else pass_args+=("$1"); fi ;;
    esac
    shift
done

usage() {
    echo "Usage: watch-infer.sh [--check|--core|--run] [--contract FILE] [--release N] [rule.klein]" >&2
    echo "Keys: c=check  o=core  r=run  q=quit" >&2
    exit 1
}

[[ -n "$file" && ! -f "$file" ]] && { echo "No such file: $file" >&2; usage; }
[[ -n "$contract" && ! -f "$contract" ]] && { echo "No such contract: $contract" >&2; usage; }
[[ -z "$file" && -z "$contract" ]] && usage
# `core` lowers a program on its own; with no rule file there is nothing to lower.
[[ -z "$file" ]] && mode="check"

run_stage() {
    clear
    local header="[${mode}]"
    [[ -n "$contract" ]] && header+="  contract=${contract##*/}"
    [[ -n "$release" ]] && header+="  release=$release"
    echo "$header  c=check  o=core  r=run  q=quit"
    echo ""

    local args=("$mode")
    # Only check/run take a contract; core would reject the flags.
    if [[ -n "$contract" && "$mode" != "core" ]]; then
        args+=(--contract "$contract")
        [[ -n "$release" ]] && args+=(--release "$release")
    elif [[ -n "$contract" ]]; then
        echo "(core ignores --contract; a rule naming a capability will not lower)"
        echo ""
    fi
    [[ -n "$file" ]] && args+=("$file")
    args+=("${pass_args[@]}")

    ./klein "${args[@]}"
}

get_mtime() { stat -c %Y "$1" 2>/dev/null; }

watched_stamp() {
    local stamp=""
    [[ -n "$file" ]] && stamp+="$(get_mtime "$file"),"
    [[ -n "$contract" ]] && stamp+="$(get_mtime "$contract"),"
    stamp+="$(get_mtime ./klein)"
    echo "$stamp"
}

run_stage
last=$(watched_stamp)

while true; do
    if read -rsn1 -t 0.5 key; then
        case "$key" in
            c) mode="check"; run_stage ;;
            o) if [[ -n "$file" ]]; then mode="core"; run_stage; fi ;;
            r) if [[ -n "$file" ]]; then mode="run"; run_stage; fi ;;
            q) echo; exit 0 ;;
        esac
    fi
    cur=$(watched_stamp)
    if [[ "$cur" != "$last" ]]; then
        last="$cur"
        run_stage
    fi
done
