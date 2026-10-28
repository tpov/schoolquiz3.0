# Device Farm smoke — связность облако↔VPS-риг (2026-10-28)

Вердикт: **PASS (прогон №7, run 4914555f, Pixel 10)** — флоу 02-request-sync
полностью зелёный на честной спеке: Launch → HOME QUESTS → меню → SYNC NOW →
«Last sync: .*» виден → «Not synced yet» скрыт → скриншот (maestro-tests-pass/).

APK: android-next-mvp 4.0.1-isolated sha256 2268f62a… (run/device-farm/apk-identity-2026-10-28.json);
Device Farm переподписывает APK своей подписью — sha фиксируется по тест-пакету.
DF: project schoolquiz-mvp-rig 69133ff9, pool smoke-pixel10, spec v5 (repo docs/agents/device-farm/testspec-maestro.yaml, 624b5308).

## Семь прогонов = четыре реальные починки (всеコミты в night/e7-commerce)

1. №1 false-PASS: Maestro не стартовал (java контейнера <17), tee глотал код — spec v2: JDK17 Temurin + pipefail + || exit 1 (1a911d5c).
2. №2-3 FAILED честно: Firestore-канал лип к сети первого старта (ENETUNREACH) — spec v5: am force-stop перед флоу (624b5308); диагностика №4: ICMP до рига 0% потерь — сеть не виновата.
3. №5-6 FAILED: functions-emu стоял от 18.10 (контрактный дрейф против клиента 28.10) — рестарт контейнера на HEAD: 109 функций (5aa4356b…: repo монтируется live в контейнер).
4. №6 FAILED: Firestore-эмулятор демо-контейнера ЗАВИС (TCP принимает, не отвечает; auth рядом жив) — все функции таймаутили по 60с. Чистый стоп + подъём с экспортом от 11.10 (свежее не снялось — процесс завис; сентябрьский стейджинг забэкаплен /root/staging-sept13-backup-20261028.tar.gz). Октябрьские demo-аккаунты потеряны — джорни создают анонимные.

После двух починок №7 PASS за ~4 минуты устройства.

## Хвосты
- Память демо-контейнера 1.37 ГБ (Firestore 56% и завис) — поднять лимит решением владельца.
- Full диф/логи прогона: run/device-farm/smoke-02-final/ (untracked), видео Video.mp4.
- post_test при FAILED-фазе Device Farm не выполняет — дебаг-скриншоты собираются только на зелёном (известная странность DF).
