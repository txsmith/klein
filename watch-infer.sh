#!/bin/bash
file="${@: -1}"
printf '%s\n' "$file" ./klein | entr -c ./klein infer "$@"
