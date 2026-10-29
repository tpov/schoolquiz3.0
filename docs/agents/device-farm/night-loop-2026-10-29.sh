#!/usr/bin/env bash
# Ночная смена 29/30.10: ждать открытия канала больших заливок, залить
# пакет ОДИН раз (uploads переиспользуются между прогонами), затем
# прогнать джорни по приоритету 9.9 → 4.26 → 4.20 → 5.13 с watch-скриптами.
set -uo pipefail
cd /workspace/projects/schoolquiz3.0
PROJ=arn:aws:devicefarm:us-west-2:453713320584:project:69133ff9-bd68-4307-82e3-02855263c8f8
REGION=us-west-2
PKG=run/device-farm/pkg/devicefarm-test-package.zip

put_urllib() { python3 -c "import sys,urllib.request; d=open(sys.argv[2],'rb').read(); r=urllib.request.Request(sys.argv[1],data=d,method='PUT'); print('urllib PUT', urllib.request.urlopen(r, timeout=900).status)" "$1" "$2" 2>&1 | tail -1; }
put_curl() { curl -s -o /dev/null -w "curl PUT %{http_code}\n" -X PUT --data-binary @"$2" -H "Content-Type: application/octet-stream" "$1"; }

echo "$(date -u +%H:%M:%S) PHASE 1: жду канал больших заливок (до 240 попыток по ~75 с ≈ 5 ч)"
PKG_ARN=""
for i in $(seq 1 240); do
  TAG="df-nightall-$i-$RANDOM.zip"
  ARN=$(aws devicefarm create-upload --project-arn "$PROJ" --name "$TAG" --type APPIUM_NODE_TEST_PACKAGE --query upload.arn --output text --region "$REGION")
  U=$(aws devicefarm get-upload --arn "$ARN" --query upload.url --output text --region "$REGION")
  if [ $((i % 2)) -eq 1 ]; then OUT=$(put_urllib "$U" "$PKG"); else OUT=$(put_curl "$U" "$PKG"); fi
  case "$OUT" in
    *200*)
      ST=""
      for j in $(seq 1 100); do
        ST=$(aws devicefarm get-upload --arn "$ARN" --query upload.status --output text --region "$REGION")
        [ "$ST" = "SUCCEEDED" ] && break
        [ "$ST" = "FAILED" ] && break
        sleep 4
      done
      if [ "$ST" = "SUCCEEDED" ]; then
        PKG_ARN="$ARN"; echo "$(date -u +%H:%M:%S) КАНАЛ ОТКРЫЛСЯ на попытке $i: $ARN"; break
      fi
      echo "$(date -u +%H:%M:%S) try $i: PUT 200 но статус $ST"
      ;;
    *) echo "$(date -u +%H:%M:%S) try $i: $OUT";;
  esac
  sleep 60
done
[ -n "$PKG_ARN" ] || { echo "$(date -u +%H:%M:%S) КАНАЛ ТАК И НЕ ОТКРЫЛСЯ — смена закончена"; exit 1; }

journey() {  # $1=run-name $2=flow $3=font $4=mvpb $5=watcher(отн. путь или пусто)
  local RN="$1" FLOW="$2" FONT="$3" MVPB="$4" WATCH="$5" WPID="" ST ARN i j
  echo "$(date -u +%H:%M:%S) === ДЖОРНИ $RN ==="
  if [ -n "$WATCH" ]; then
    bash "run/device-farm/$WATCH" > "/tmp/watch-$RN.log" 2>&1 & WPID=$!
    echo "watcher: $WATCH (pid $WPID)"
  fi
  SPEC_RUN="/tmp/night-spec-$RN.yaml"
  sed -e "s|flows/02-request-sync.yaml|$FLOW|" docs/agents/device-farm/testspec-maestro.yaml > "$SPEC_RUN"
  sed -i "s|export INSTALL_MVPB=\"\${INSTALL_MVPB:-0}\"|export INSTALL_MVPB=\"$MVPB\"|" "$SPEC_RUN"
  [ -n "$FONT" ] && sed -i "s|export MAESTRO_FONT_SCALE=\"\${MAESTRO_FONT_SCALE:-}\"|export MAESTRO_FONT_SCALE=\"$FONT\"|" "$SPEC_RUN"
  SPEC_ARN=""
  for i in 1 2 3 4 5 6 7 8 9 10; do
    ARN=$(aws devicefarm create-upload --project-arn "$PROJ" --name "night-spec-$RN-$i.yaml" --type APPIUM_NODE_TEST_SPEC --query upload.arn --output text --region "$REGION")
    U=$(aws devicefarm get-upload --arn "$ARN" --query upload.url --output text --region "$REGION")
    OUT=$(put_urllib "$U" "$SPEC_RUN")
    case "$OUT" in
      *200*)
        ST=""
        for j in $(seq 1 60); do
          ST=$(aws devicefarm get-upload --arn "$ARN" --query upload.status --output text --region "$REGION")
          [ "$ST" = "SUCCEEDED" ] && { SPEC_ARN="$ARN"; break; }
          [ "$ST" = "FAILED" ] && break
          sleep 3
        done
        [ -n "$SPEC_ARN" ] && break
        ;;
    esac
    echo "$(date -u +%H:%M:%S) spec try $i: $OUT"; sleep 20
  done
  if [ -z "$SPEC_ARN" ]; then echo "$(date -u +%H:%M:%S) СПЕКА НЕ ЗАЛИЛАСЬ — джорни $RN пропущен"; [ -n "$WPID" ] && kill "$WPID" 2>/dev/null; return 1; fi
  RUN_ARN=$(aws devicefarm schedule-run --project-arn "$PROJ" \
    --app-arn arn:aws:devicefarm:us-west-2:453713320584:upload:69133ff9-bd68-4307-82e3-02855263c8f8/c1bc3e88-0bf3-4d71-821b-1233a46dc2e2 \
    --device-pool-arn arn:aws:devicefarm:us-west-2:453713320584:devicepool:69133ff9-bd68-4307-82e3-02855263c8f8/e85d048d-491b-4d7c-8fac-4d6a913f5e81 \
    --name "$RN" \
    --execution-configuration '{"skipAppResigning":false,"jobTimeoutMinutes":20,"accountsCleanup":true,"appPackagesCleanup":true,"videoCapture":true}' \
    --test "{\"type\":\"APPIUM_NODE\",\"testPackageArn\":\"$PKG_ARN\",\"testSpecArn\":\"$SPEC_ARN\"}" \
    --query run.arn --output text --region "$REGION")
  echo "$(date -u +%H:%M:%S) run: $RUN_ARN"
  for i in $(seq 1 60); do
    ST=$(aws devicefarm get-run --arn "$RUN_ARN" --query run.status --output text --region "$REGION")
    echo "$(date -u +%H:%M:%S) run: $ST"
    [ "$ST" = "COMPLETED" ] && break
    [ "$ST" = "LER" ] && break
    sleep 30
  done
  sleep 25
  aws devicefarm list-jobs --arn "$RUN_ARN" --region "$REGION" --query "jobs[0].{result:result,status:status,passed:counters.passed,failed:counters.failed,total:counters.total}" --output json
  bash run/device-farm/df-collect.sh "$RUN_ARN" "$RN" 2>&1 | tail -3
  if [ -n "$WPID" ]; then
    for i in $(seq 1 30); do kill -0 "$WPID" 2>/dev/null || break; sleep 10; done
    kill "$WPID" 2>/dev/null
    echo "=== watcher $RN log tail ==="; tail -10 "/tmp/watch-$RN.log" 2>/dev/null
  fi
  echo "$(date -u +%H:%M:%S) === ДЖОРНИ $RN ЗАВЕРШЁН ==="
}

journey 99-accessibility flows/99-accessibility-key-screens.yaml 1.3 0 ""
journey 426-proposals   flows/426-proposals-three-entries.yaml  "" 1 watch-426/watch.sh
journey 420-review      flows/420-author-submit-review.yaml     "" 0 watch-420/watch.sh
journey 513-tournament  flows/513-tournament-organizer.yaml     "" 0 watch-513/watch.sh
echo "$(date -u +%H:%M:%S) НОЧНАЯ СМЕНА ЗАВЕРШЕНА"
