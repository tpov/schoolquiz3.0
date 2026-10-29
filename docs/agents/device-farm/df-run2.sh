#!/usr/bin/env bash
# df-run2: заливка пакета/спеки + старт прогона. Переписан с нуля 29.10:
# df-run.sh стабильно ловит SignatureDoesNotMatch в своём контексте при
# рабочих ручных заливках тем же кодом (причина не найдена — это не файл,
# не имя и не флаги CLI; задокументировано в evidence).
set -uo pipefail
cd /workspace/projects/schoolquiz3.0

RUN_NAME="$1"; FLOW="$2"; FONT_SCALE="${3:-}"; INSTALL_MVPB="${4:-0}"
PROJ=arn:aws:devicefarm:us-west-2:453713320584:project:69133ff9-bd68-4307-82e3-02855263c8f8
POOL=arn:aws:devicefarm:us-west-2:453713320584:devicepool:69133ff9-bd68-4307-82e3-02855263c8f8/e85d048d-491b-4d7c-8fac-4d6a913f5e81
APP=arn:aws:devicefarm:us-west-2:453713320584:upload:69133ff9-bd68-4307-82e3-02855263c8f8/c1bc3e88-0bf3-4d71-821b-1233a46dc2e2
REGION=us-west-2

SPEC_RUN="/tmp/testspec2-$RUN_NAME.yaml"
sed -e "s|flows/02-request-sync.yaml|$FLOW|" docs/agents/device-farm/testspec-maestro.yaml > "$SPEC_RUN"
sed -i "s|export INSTALL_MVPB=\"${INSTALL_MVPB:-0}\"|export INSTALL_MVPB=\"$INSTALL_MVPB\"|" "$SPEC_RUN"
if [ -n "$FONT_SCALE" ]; then
  sed -i "s|export MAESTRO_FONT_SCALE=\"${MAESTRO_FONT_SCALE:-}\"|export MAESTRO_FONT_SCALE=\"$FONT_SCALE\"|" "$SPEC_RUN"
fi

put() {
  python3 -c "import sys,urllib.request; d=open(sys.argv[2],'rb').read(); r=urllib.request.Request(sys.argv[1],data=d,method='PUT'); print('PUT', urllib.request.urlopen(r, timeout=300).status)" "$1" "$2"
}

up() {  # $1=type $2=name $3=file -> ARN; до 8 попыток
  local t="$1" n="$2" f="$3" i j arn u s st
  for i in 1 2 3 4 5 6 7 8; do
    arn=$(aws devicefarm create-upload --project-arn "$PROJ" --name "$n" --type "$t" --query upload.arn --output text --region "$REGION")
    u=$(aws devicefarm get-upload --arn "$arn" --query upload.url --output text --region "$REGION")
    st="PUT_FAIL"
    put "$u" "$f" && st="PUT_OK"
    echo "upload $n try $i: $st"
    if [ "$st" = "PUT_OK" ]; then
      for j in $(seq 1 60); do
        s=$(aws devicefarm get-upload --arn "$arn" --query upload.status --output text --region "$REGION")
        [ "$s" = "SUCCEEDED" ] && { echo "$arn"; return 0; }
        [ "$s" = "FAILED" ] && break
        sleep 3
      done
    fi
    sleep 30
  done
  return 1
}

PKG_ARN=$(up APPIUM_NODE_TEST_PACKAGE devicefarm-test-package.zip run/device-farm/pkg/devicefarm-test-package.zip) || { echo "package upload FAILED"; exit 1; }
echo "pkg: $PKG_ARN [SUCCEEDED]"
SPEC_ARN=$(up APPIUM_NODE_TEST_SPEC testspec-maestro.yaml "$SPEC_RUN") || { echo "spec upload FAILED"; exit 1; }
echo "spec: $SPEC_ARN [SUCCEEDED]"

RUN_ARN=$(aws devicefarm schedule-run --project-arn "$PROJ" --app-arn "$APP" \
  --device-pool-arn "$POOL" --name "$RUN_NAME" \
  --execution-configuration '{"skipAppResigning":false,"jobTimeoutMinutes":20,"accountsCleanup":true,"appPackagesCleanup":true,"videoCapture":true}' \
  --test "{\"type\":\"APPIUM_NODE\",\"testPackageArn\":\"$PKG_ARN\",\"testSpecArn\":\"$SPEC_ARN\"}" \
  --query run.arn --output text --region "$REGION")
echo "run: $RUN_ARN"
