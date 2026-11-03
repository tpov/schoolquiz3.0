#!/usr/bin/env bash
# Sandbox-покупка premium_30d и gold_pack_small лицензионным тестером.
#
# ЧЕЛОВЕК-В-ЦИКЛЕ: интерактивные места (лист Google Play, вход в аккаунт,
# возврат в Play Console) владелец проходит руками — скрипт ведёт по шагам,
# до и после снимает доказательства (скриншот, текст экрана, серверный
# срез через sandbox-verify.js) и складывает их в каталог прогона.
#
# Где запускать: на машине с подключённым телефоном, у которой ЕСТЬ
# Google Play (физическое устройство; риг redroid без GMS не годится —
# см. docs/agents/evidence/2026-10-12-sandbox-purchase-rig).
#
#   sandbox-purchase.sh <serial> [preflight|premium|gold|refund|verify|all]
#
#   APK=/путь/android-next-debug.apk   — поставить, если приложения нет
#   ADMIN_SDK_JSON=/путь/firebase-adminsdk-*.json   — для серверных срезов
#   REFUND_WAIT_MIN=15                 — сколько ждать RTDN-клобэк
set -u

SERIAL="${1:?нужен serial устройства (adb devices)}"
PHASE="${2:-all}"
HERE="$(cd "$(dirname "$0")" && pwd)"
UI="$HERE/ui.sh"
UID_SH="$HERE/uid.sh"
ADB="${ADB:-/opt/homebrew/bin/adb}"
PKG="${PKG:-com.tpov.schoolquiz}"
TESTER="${TESTER:-oleg12794@gmail.com}"
OUT="${SANDBOX_OUT:-sandbox-run-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT/shots"

log()  { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/sandbox.log"; }
shot() { "$UI" "$SERIAL" shot "$OUT/shots/$1" | tee -a "$OUT/sandbox.log"; }
text() { "$UI" "$SERIAL" text 2>/dev/null; }
pause(){ echo; echo "  👉 $1"; read -r -p "  …нажми Enter, когда сделаешь" x; echo | tee -a "$OUT/sandbox.log"; }

verify() {
  log "серверный срез для $1:"
  ADMIN_SDK_JSON="${ADMIN_SDK_JSON:-}" node "$HERE/sandbox-verify.js" "$1" 2>&1 | tee -a "$OUT/sandbox.log"
}

app_uid() {
  # uid.sh печатает "<serial> <uid> <provider> <email>"; берём строку нашего serial
  ADB="$ADB" bash "$UID_SH" 2>/dev/null | awk -v s="$SERIAL" '$1==s {print $2}'
}

open_shop() {
  $ADB -s "$SERIAL" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 6
  "$UI" "$SERIAL" tap "SHOP" >/dev/null 2>&1
  sleep 5
}

gold_on_screen() {
  text | grep -iE "^gold$|золот" >/dev/null && echo yes || echo no
}

PREFLIGHT() {
  log "== PREFLIGHT =="
  $ADB -s "$SERIAL" get-state >/dev/null 2>&1 || { log "НЕТ устройства $SERIAL"; exit 1; }
  log "устройство: $($ADB -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r'), Android $($ADB -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"

  for p in com.android.vending com.google.android.gms; do
    if $ADB -s "$SERIAL" shell pm list packages "$p" | tr -d '\r' | grep -q "$p"; then
      log "ok: $p установлен"
    else
      log "BAD: $p отсутствует — покупки невозможны (нужен девайс с Play)"
      exit 1
    fi
  done

  if $ADB -s "$SERIAL" shell dumpsys account 2>/dev/null | tr -d '\r' | grep -qi "$TESTER"; then
    log "ok: на устройстве есть аккаунт лицензионного тестера $TESTER"
  else
    log "ВНИМАНИЕ: аккаунт $TESTER на устройстве не найден (dumpsys account)."
    log "  Войди в Play Store под тестером, иначе покупка будет не sandbox."
    pause "войти в Play Store под $TESTER"
  fi

  if ! $ADB -s "$SERIAL" shell pm path "$PKG" >/dev/null 2>&1; then
    if [ -n "${APK:-}" ]; then
      log "приложения нет — ставлю $APK"
      $ADB -s "$SERIAL" install -r -t "$APK" | tee -a "$OUT/sandbox.log"
    else
      log "BAD: $PKG не установлен; задай APK=…/android-next-debug.apk или поставь из внутреннего трека"
      exit 1
    fi
  else
    log "ok: $PKG установлен ($($ADB -s "$SERIAL" shell dumpsys package "$PKG" | grep -m1 versionName | tr -d '\r'))"
  fi

  U=$(app_uid); log "uid приложения: ${U:-(не расшифрован — на релизной сборке это норма)}"
  echo "$U" > "$OUT/uid.txt"
  log "сборка ДОЛЖНА быть debug (run-as) либо внутренней сборкой с тем же package id"
  shot 00-preflight-home.png
}

PREMIUM() {
  log "== PREMIUM_30D + триал trial-3d =="
  open_shop
  shot 10-shop-before-premium.png
  text > "$OUT/shop-before-premium.txt"
  if grep -qi "unreachable\|недоступен" "$OUT/shop-before-premium.txt"; then
    log "ВНИМАНИЕ: полка пишет 'Google Play is unreachable' — BillingClient не подключился"
    log "  (Play Store не залогинен/обновляется). Покупка скорее всего не пройдёт."
  else
    log "ok: полка отвечает — цены из Play видны"
  fi

  pause "в приложении: Premium → оформи подписку (триал 3 дня) в листе Play под $TESTER"
  sleep 3; shot 11-after-premium-buy.png
  text > "$OUT/shop-after-premium.txt"
  log "экран после покупки: $(grep -icE "premium|премиум" "$OUT/shop-after-premium.txt") упоминаний premium"
  grep -E "premium|премиум" "$OUT/shop-after-premium.txt" | head -5 | tee -a "$OUT/sandbox.log"

  U=$(cat "$OUT/uid.txt" 2>/dev/null); [ -n "$U" ] && verify "$U"
  log "ожидание в срезе: premiumUntilMs ≈ сейчас+3д, premiumGrantId, settlement premium_30d SETTLED, чек"
  log "RTDN: на НОВУЮ подписку с триалом Google может НЕ прислать событие; канал проверяется"
  log "  кнопкой Send test notification (Play Console → Monetization setup → Test notification),"
  log "  запись появится в purchase_notifications с kind=testNotification."
}

GOLD() {
  log "== GOLD_PACK_SMALL (+10 золота) =="
  open_shop
  shot 20-shop-before-gold.png
  text > "$OUT/shop-before-gold.txt"
  U=$(cat "$OUT/uid.txt" 2>/dev/null); [ -n "$U" ] && verify "$U"

  pause "в приложении: Small gold pack → купи в листе Play"
  sleep 3; shot 21-after-gold-buy.png
  text > "$OUT/shop-after-gold.txt"
  [ -n "$U" ] && verify "$U"
  log "ожидание в срезе: users/{uid}.gold = было+10, settlement gold_pack_small SETTLED, чек, строка в ledger"
}

REFUND() {
  log "== REFUND (отзыв доступа, story 1.3) =="
  pause "Play Console (play.google.com/console) → Order management → найди тестовые заказы → Refund."
  log "  gold_pack_small: полный клобэк — gold снимется, settlement → REFUNDED (если золото"
  log "    уже потрачено — уйдёт в shortfall, без минуса)."
  log "  premium_30d: СЕЙЧАС паркуется (functions/purchase-clawback.js: 'subscription refund"
  log "    parked') — RTDN запишется, токен уйдёт в pending-fence (начисления не будет),"
  log "    но премия на счету останется до отдельного решения."

  WAIT=$(( ${REFUND_WAIT_MIN:-15} * 2 ))
  U=$(cat "$OUT/uid.txt" 2>/dev/null)
  for i in $(seq 1 "$WAIT"); do
    [ -n "$U" ] && verify "$U" | tail -40 > "$OUT/refund-poll-$i.txt"
    if [ -f "$OUT/refund-poll-$i.txt" ] && grep -q "REFUNDED" "$OUT/refund-poll-$i.txt"; then
      log "ok: REFUNDED увиден на опросе $i (RTDN-клобэк дошёл)"
      break
    fi
    sleep 30
  done
  open_shop
  shot 30-after-refund.png
  text > "$OUT/shop-after-refund.txt"
  [ -n "$U" ] && verify "$U"
}

VERIFY() {
  U=$(app_uid); [ -n "$U" ] || U=$(cat "$OUT/uid.txt" 2>/dev/null)
  [ -n "$U" ] || { log "нет uid — сначала preflight"; exit 1; }
  echo "$U" > "$OUT/uid.txt"
  verify "$U"
}

case "$PHASE" in
  preflight) PREFLIGHT ;;
  premium)   PREMIUM ;;
  gold)      GOLD ;;
  refund)    REFUND ;;
  verify)    VERIFY ;;
  all)       PREFLIGHT; PREMIUM; GOLD; REFUND ;;
  *) echo "фаза?: preflight|premium|gold|refund|verify|all"; exit 2 ;;
esac
log "готово. артефакты: $OUT/"
