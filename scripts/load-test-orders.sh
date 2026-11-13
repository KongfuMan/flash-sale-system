#!/usr/bin/env bash
# Capacity test for OrderController.placeOrder, which returns 202 Accepted.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORDER_PATH_SUFFIX="" SUCCESS_STATUSES="202" exec "$script_dir/load-test-direct-orders.sh" "$@"
