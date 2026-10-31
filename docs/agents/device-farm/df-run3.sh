#!/usr/bin/env bash
# df-run3: старт прогона при УЖЕ залитом тест-пакете (30-31.10, инцидент провайдера:
# исходящие TLS-ответы/PUT VPS↔AWS портились — URL-ы presigned прилетали битыми,
# SignatureDoesNotMatch на любой PUT/GET артефактов; прямой путь Mac→AWS чист).
# Отличия от df-run2.sh:
#   - DF_PKG_ARN обязателен (пакет не заливается совсем);
#   - DF_SPEC_ARN (5-й аргумент): спека уже залита мостом Mac→AWS
#     (run/device-farm/df-mac-put.py) — сразу schedule-run;
#     без DF_SPEC_ARN — старый путь create-upload+PUT с VPS (если канал здоров).
# Usage: DF_PKG_ARN=<pkg-arn> [DF_SPEC_ARN=<spec-arn>] bash run/device-farm/df-run3.sh <name> <flow> [font] [mvpb]
set -uo pipefail
cd /workspace/projects/schoolquiz3.0

RUN_NAME="$1"; FLOW="$2"; FONT_SCALE="${3:-}"; INSTALL_MVPB="${4:-0}"
PROJ=arn:aws:devicefarm:us-west-2:453713320584:project:69133ff9-bd68-4307-82e3-02855263c8f8
POOL=arn:aws:devicefarm:us-west-2:453713320584:devicepool:69133ff9-bd68-4307-82e3-02855263c8f8/e85d048d-491b-4d7c-8fac-4d6a913f5e81
APP=arn:aws:devicefarm:us-west-2:453713320584:upload:69133ff9-bd68-4307-82e3-02855263c8f8/c1bc3e88-0bf3-4d71-821b-1233a46dc2e2
REGION=us-west-2
PKG_ARN="${DF_PKG_ARN:?set DF_PKG_ARN to the pre-uploaded package upload ARN}"

SPEC_RUN="/tmp/testspec3-$RUN_NAME.yaml"
sed -e "s|flows/02-request-sync.yaml|$FLOW|" docs/agents/device-farm/testspec-maestro.yaml > "$SPEC_RUN"
# ВАЖНО: паттерны в одинарных кавычках — в спеке литеральные ${INSTALL_MVPB:-0} /
# ${MAESTRO_FONT_SCALE:-}; в двойных кавычках bash разворачивает их до запуска sed
# и замена молча не срабатывает (поймано 31.10 до прогона: knobs не доезжали).
sed -i 's|export INSTALL_MVPB="${INSTALL_MVPB:-0}"|export INSTALL_MVPB="'"$INSTALL_MVPB"'"|' "$SPEC_RUN"
if [ -n "$FONT_SCALE" ]; then
  sed -i 's|export MAESTRO_FONT_SCALE="${MAESTRO_FONT_SCALE:-}"|export MAESTRO_FONT_SCALE="'"$FONT_SCALE"'"|' "$SPEC_RUN"
fi
# Составной if для mvpb DF ломает при конвертации спеки в shell — маркер занимает
# одна простая команда (adb install / echo skip).
if [ "$INSTALL_MVPB" = "1" ]; then
  sed -i 's|- echo "MVPB_INSTALL_PLACEHOLDER"|- adb install -r "$DEVICEFARM_TEST_PACKAGE_PATH/apks/android-next-mvpb.apk"|' "$SPEC_RUN"
else
  sed -i 's|- echo "MVPB_INSTALL_PLACEHOLDER"|- echo "mvpb install skipped (INSTALL_MVPB=0)"|' "$SPEC_RUN"
fi
grep -nE 'INSTALL_MVPB=|FONT_SCALE=|MAESTRO_FLOW|mvpb' "$SPEC_RUN" | head -6

if [ -n "${DF_SPEC_ARN:-}" ]; then
  ARN="$DF_SPEC_ARN"
  echo "spec upload: $ARN [pre-uploaded via Mac bridge]"
else
  ARN=$(aws devicefarm create-upload --project-arn "$PROJ" --name "$RUN_NAME-spec.yaml" --type APPIUM_NODE_TEST_SPEC --region "$REGION" --query upload.arn --output text)
  echo "spec upload: $ARN"
  URL=$(aws devicefarm get-upload --arn "$ARN" --region "$REGION" --query upload.url --output text)
  printf 'ARN=%s\nFILE=%s\nURL=%s\n' "$ARN" "$SPEC_RUN" "$URL" > /tmp/df-spec-put.env
  echo "relay hint: /tmp/df-spec-put.env (url re-fetch: aws devicefarm get-upload --arn $ARN --query upload.url)"
  code=$(curl -sS --path-as-is -o /dev/null -w "%{http_code}" -X PUT --data-binary @"$SPEC_RUN" "$URL" || true)
  echo "vps-put http=$code"
  s=""
  for i in $(seq 1 90); do
    s=$(aws devicefarm get-upload --arn "$ARN" --region "$REGION" --query upload.status --output text)
    echo "status[$i]=$s"
    [ "$s" = "SUCCEEDED" ] && break
    [ "$s" = "FAILED" ] && { echo "spec upload FAILED"; exit 1; }
    sleep 10
  done
  [ "$s" = "SUCCEEDED" ] || { echo "spec upload timeout (15 min)"; exit 1; }
fi

RUN_ARN=$(aws devicefarm schedule-run --project-arn "$PROJ" --app-arn "$APP" \
  --device-pool-arn "$POOL" --name "$RUN_NAME" \
  --execution-configuration '{"skipAppResign":false,"jobTimeoutMinutes":20,"accountsCleanup":true,"appPackagesCleanup":true,"videoCapture":true}' \
  --test "{\"type\":\"APPIUM_NODE\",\"testPackageArn\":\"$PKG_ARN\",\"testSpecArn\":\"$ARN\"}" \
  --query run.arn --output text --region "$REGION")
echo "run: $RUN_ARN"
