#!/usr/bin/env bash
set -x

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

APIURL=${APIURL:-https://conduit.productionready.io/api}
USERNAME=${USERNAME:-u`date +%s`}
EMAIL=${EMAIL:-$USERNAME@mail.com}
PASSWORD=${PASSWORD:-password}
WAIT_FOR_API_ATTEMPTS=${WAIT_FOR_API_ATTEMPTS:-30}
WAIT_FOR_API_DELAY_SECONDS=${WAIT_FOR_API_DELAY_SECONDS:-2}

for attempt in $(seq 1 "$WAIT_FOR_API_ATTEMPTS"); do
  if curl --silent --fail "$APIURL/tags" >/dev/null; then
    break
  fi

  if [ "$attempt" -eq "$WAIT_FOR_API_ATTEMPTS" ]; then
    echo "API did not become ready at $APIURL within the expected time."
    exit 1
  fi

  sleep "$WAIT_FOR_API_DELAY_SECONDS"
done

npx newman run $SCRIPTDIR/Conduit.postman_collection.json \
  --delay-request 500 \
  --global-var "APIURL=$APIURL" \
  --global-var "USERNAME=$USERNAME" \
  --global-var "EMAIL=$EMAIL" \
  --global-var "PASSWORD=$PASSWORD"
