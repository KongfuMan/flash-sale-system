#!/usr/bin/env bash
# Requires k6: https://grafana.com/docs/k6/latest/set-up/install-k6/
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
EVENT_ID="${EVENT_ID:-1}"
PRODUCT_ID="${PRODUCT_ID:-1}"
USER_ID="${USER_ID:-1}"
ORDER_PATH_SUFFIX="${ORDER_PATH_SUFFIX-/direct}"
SUCCESS_STATUSES="${SUCCESS_STATUSES:-200,201}"
RPS_LIST="${RPS_LIST:-50 75 100 125 150 175 200}"
WARMUP_DURATION="${WARMUP_DURATION:-60s}"
DURATION="${DURATION:-5m}"
RUNS="${RUNS:-3}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-100}"
MAX_VUS="${MAX_VUS:-1000}"
MAX_P95_MS="${MAX_P95_MS:-200}"
MAX_P99_MS="${MAX_P99_MS:-500}"
MIN_SUCCESS_RATE="${MIN_SUCCESS_RATE:-99.9}"
ENFORCE_THRESHOLDS="${ENFORCE_THRESHOLDS:-false}"

if command -v k6 >/dev/null 2>&1; then
    k6_command=(k6)
elif command -v docker >/dev/null 2>&1; then
    k6_command=(docker run --rm -i -e BASE_URL -e EVENT_ID -e PRODUCT_ID -e USER_ID -e ORDER_PATH_SUFFIX -e SUCCESS_STATUSES -e RATE -e WARMUP_DURATION -e DURATION -e PRE_ALLOCATED_VUS -e MAX_VUS -e MAX_P95_MS -e MAX_P99_MS -e MIN_SUCCESS_RATE -e ENFORCE_THRESHOLDS -e RUN_ID grafana/k6:latest)
    using_docker_k6=true
    printf '%s\n' 'k6 is not installed; using the grafana/k6 Docker image.' >&2
else
    printf '%s\n' 'k6 or Docker is required. Install k6 from https://grafana.com/docs/k6/latest/set-up/install-k6/' >&2
    exit 1
fi

BASE_URL="${BASE_URL%/}"
if [[ "${using_docker_k6:-false}" == true && "$BASE_URL" == http://localhost* ]]; then
    BASE_URL="${BASE_URL/localhost/host.docker.internal}"
fi
for value in "$EVENT_ID" "$PRODUCT_ID" "$USER_ID"; do
    case "$value" in
        ''|*[!0-9]*)
            printf '%s\n' 'EVENT_ID, PRODUCT_ID, and USER_ID must be positive integer values.' >&2
            exit 1
            ;;
    esac
    if ((10#$value < 1)); then
        printf '%s\n' 'EVENT_ID, PRODUCT_ID, and USER_ID must be positive integer values.' >&2
        exit 1
    fi
done

case "$RUNS" in
    ''|*[!0-9]*)
        printf 'RUNS must be a positive integer: %s\n' "$RUNS" >&2
        exit 1
        ;;
esac
if ((10#$RUNS < 1)); then
    printf 'RUNS must be a positive integer: %s\n' "$RUNS" >&2
    exit 1
fi

for rps in ${RPS_LIST//,/ }; do
    case "$rps" in
        ''|*[!0-9]*)
            printf 'Invalid RPS value: %s\n' "$rps" >&2
            exit 1
            ;;
    esac
    if ((10#$rps < 1)); then
        printf 'Invalid RPS value: %s\n' "$rps" >&2
        exit 1
    fi

    for ((run = 1; run <= 10#$RUNS; run++)); do
        printf '\nRPS %s, run %s/%s: warmup %s, measured duration %s\n' "$rps" "$run" "$RUNS" "$WARMUP_DURATION" "$DURATION"
        BASE_URL="$BASE_URL" EVENT_ID="$EVENT_ID" PRODUCT_ID="$PRODUCT_ID" USER_ID="$USER_ID" \
        ORDER_PATH_SUFFIX="$ORDER_PATH_SUFFIX" SUCCESS_STATUSES="$SUCCESS_STATUSES" \
        RATE="$rps" WARMUP_DURATION="$WARMUP_DURATION" DURATION="$DURATION" RUN_ID="$run" \
        PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" MAX_VUS="$MAX_VUS" MAX_P95_MS="$MAX_P95_MS" \
        MAX_P99_MS="$MAX_P99_MS" MIN_SUCCESS_RATE="$MIN_SUCCESS_RATE" ENFORCE_THRESHOLDS="$ENFORCE_THRESHOLDS" \
        "${k6_command[@]}" run --summary-trend-stats='avg,min,p(50),p(90),p(95),p(99),max' - <<'K6_SCRIPT'
import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const steadyOrderLatency = new Trend('steady_order_latency', true);
const steadyOrderSuccessRate = new Rate('steady_order_success_rate');
const steadyOrderSuccessResponses = new Counter('steady_order_success_responses');
const steadyOrderReplayResponses = new Counter('steady_order_replay_responses');
const steadyUnexpectedResponses = new Counter('steady_unexpected_responses');
const enforceThresholds = __ENV.ENFORCE_THRESHOLDS === 'true';
const successStatuses = __ENV.SUCCESS_STATUSES.split(',').map((status) => Number(status));

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warmup',
      rate: Number(__ENV.RATE),
      timeUnit: '1s',
      duration: __ENV.WARMUP_DURATION,
      gracefulStop: '0s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS),
      maxVUs: Number(__ENV.MAX_VUS),
    },
    steady: {
      executor: 'constant-arrival-rate',
      exec: 'steady',
      rate: Number(__ENV.RATE),
      timeUnit: '1s',
      startTime: __ENV.WARMUP_DURATION,
      duration: __ENV.DURATION,
      gracefulStop: '0s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS),
      maxVUs: Number(__ENV.MAX_VUS),
    },
  },
  ...(enforceThresholds ? {
    thresholds: {
      steady_order_latency: [`p(95)<${__ENV.MAX_P95_MS}`, `p(99)<${__ENV.MAX_P99_MS}`],
      steady_order_success_rate: [`rate>${Number(__ENV.MIN_SUCCESS_RATE) / 100}`],
      dropped_iterations: ['count==0'],
    },
  } : {}),
};

const endpoint = `${__ENV.BASE_URL}/api/v1/flash_sale_event/${__ENV.EVENT_ID}/product/${__ENV.PRODUCT_ID}${__ENV.ORDER_PATH_SUFFIX}`;

function placeOrder(phase) {
  // A unique key keeps the consumer from short-circuiting as an idempotent replay.
  const idempotencyKey = `load-${__ENV.RUN_ID}-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2)}`;
  const response = http.post(endpoint, null, {
    headers: {
      'Idempotency-Key': idempotencyKey,
      UserId: __ENV.USER_ID,
    },
    tags: { phase },
  });

  if (phase !== 'steady') return;
  const accepted = successStatuses.includes(response.status);
  steadyOrderLatency.add(response.timings.duration);
  steadyOrderSuccessRate.add(accepted);
  if (accepted) steadyOrderSuccessResponses.add(1);
  else steadyUnexpectedResponses.add(1);
  if (response.status === 200) steadyOrderReplayResponses.add(1);
}

export function warmup() {
  placeOrder('warmup');
}

export function steady() {
  placeOrder('steady');
}
K6_SCRIPT
    done
done
