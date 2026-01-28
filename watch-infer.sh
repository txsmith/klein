#!/bin/bash
printf '%s\n' /tmp/test.klein ./klein | entr -c ./klein infer "$@" /tmp/test.klein
