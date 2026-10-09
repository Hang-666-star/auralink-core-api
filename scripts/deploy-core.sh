#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
exec /root/miniconda3/bin/python3 /root/autodl-tmp/auralink-core-deploy/bin/core_publication_gate.py "$@"
