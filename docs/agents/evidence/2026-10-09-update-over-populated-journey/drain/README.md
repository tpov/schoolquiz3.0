# Live outbox drain on the redroid rig (story 2.7, the queue dimension) — 2026-10-09 ~03:22

Живая досылка очереди из двух WAITING-операций (`6856201c…` от 08.10 и `bfd35496…`
от 09.10, оба `lesson_runner.SUBMIT_ATTEMPT`, анонимный demo-игрок
`HDb3uVEO99PLIaceEvkAabcCxtZp`) на обновлённой установке против demo-backend —
то, что предыдущая сессия не смогла сделать и записала в «rig WorkManager
dysfunction». Вердикт той сессии ревизируется: **воркеры запускались и падали,
а не «не стартовали»** — счётчик `run_attempt_count` в WorkSpec рос (9→12), а
«ноль строк лога» был свойством релизной сборки (R8 режет WM-логи), не
доказательством отсутствия запусков. Диспетчеризация на риге работает
натурально; не работает (и не работало) только `cmd jobscheduler run -f`.

## Почему очередь не дренилась раньше — три независимых причины, найденные по очереди

1. **Экспоненциальный backoff от офлайн-попыток.** Каждая офлайн-неудача удваивала
   задержку: после 12 попыток WM регистрировал джобы с `Minimum latency ≈ 5h`
   (кап MAX_BACKOFF_MS). Хирургия workspec (`no_backup/androidx.work.workdb`,
   root-доступ на userdebug-риге): `backoff_delay_duration=0, run_attempt_count=1,
   next_schedule_time_override=0` для ENQUEUED-работ. Только обнуление базы не
   помогает — формула `база × 2^(попытки−1)` использует счётчик попыток, поэтому
   первый сброс (только база) дал джобы с задержкой снова ~4h48m.
2. **`adb root` стирает reverse-правила.** `adb root` перезапускает adbd, и
   ранее выставленные `adb reverse` пропадают — первая же попытка дренилась
   с `ECONNREFUSED 127.0.0.1:8080` (Firestore «client is offline»), хотя 5001
   был проброшен уже после root. Полный набор правил: `8080→18090`,
   `9099→19090`, `5001→5001`; после любого `adb root` — перевыставить.
3. **Не поднят приёмник callables.** Demo-контейнер поднимает только
   auth+firestore (firestore.json: `--only auth,firestore`); воркер падал с
   `ECONNREFUSED 127.0.0.1:5001` (порт functions). Поднят настоящий
   functions-эмулятор на хосте: `GCLOUD_PROJECT=demo-schoolquiz-mvp
   FIRESTORE_EMULATOR_HOST=127.0.0.1:18090 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19090
   npx firebase-tools emulators:start --only functions` — все callable
   инициализированы, приёмник отвечает протоколом callable (проверено curl).

## Согласие с платными условиями — живая работа гейта (2.8+2.12) и честная подстановка

Первая же доехавшая попытка дренилась **до сервера**: приёмник вернул
`terms-consent-required` (SUBMIT_ATTEMPT в реестре `MUTATION_TERMS_OPERATIONS`,
captured terms пусты), и транспорт запарковал обе записи в `CONFLICT` — дверь
«ждёт решения игрока», не карантин и не вечный повтор: ровно спроектированное в
2.8/2.12 поведение, впервые наблюдаемое живьём end-to-end
(`last_error = TermsConsentRequired`, состояние CONFLICT).

Изолированная сборка ещё не имеет UI согласия (ветвь ждёт решения, что показать
игроку — `NoirSettingsScreen.SyncError.label`, вызова `acceptCurrentTerms()` в
presentation нет). Решение игрока воспроизведено тем же путём данных, каким его
пишет домен: `shared_prefs/schoolquiz_economy.xml` ← consent-JSON
(`PayableTermsCodec`, дефолтная таблица: `configs/economy` в demo-backend
отсутствует, сервер деградирует к DEFAULTS, `termsVersion 0`), плюс requeue по
точному SQL дао (`UPDATE outbox SET state='WAITING', next_retry_at_ms=0,
last_error=NULL WHERE state='CONFLICT' AND operation IN ('lesson_runner.SUBMIT_ATTEMPT')`,
ключи/тела/счётчики не тронуты) — семантика `acceptCurrentTerms()` →
`OutboxDao.requeueConflictedTerms` из 2.12.

## Досылка: outbox пуст, сервер записал каждую мутацию ровно один раз

После финального цикла (force-stop → сброс backoff → запуск):

- `07-drawer-last-sync.png` — drawer: **«Last sync: 09.10.2026 03:22»**, «Not
  synced yet» исчез; таблица outbox в `db-after-drain.db` **пуста** (обе записи
  ушли и удалены движком), ворки одноразовой синхронизации — SUCCEEDED
  (`workmanager-workdb-after-drain.db`: 22 succeeded, 2 periodic enqueued);
- `server-mutation-records.txt` — по одному `mutation_keys`-документу на
  mutationId, оба `state: completed` с квитанциями SETTLED:
  - `…-6856201c…`: percentScore 50, lifePointsSpent 33, reward skillPoints 9 +
    nolics 1, `termsVersion 0`;
  - `…-bfd35496…`: percentScore 100, lifePointsSpent 33, reward skillPoints 23 +
    nolics 2, `termsVersion 0`.
  Повтора мутаций нет: каждая — одна завершённая запись; replay-ветка
  exactly-once остаётся покрытой JVM-сюитой 2.2 (OutboxDrain120Test) и live-сюитами 2.13.

Demo-гигиена: записи принадлежат анонимному demo-игроку рига (устойчивый uid с
08.10); `configs/economy` не создан и не изменён (отсутствует); probe-профилей и
probe-ключей дренирование не добавляло.

## Что осталось за 2.7 (без изменений по сути)

- один online-переход (повтор 2.6) на устройстве — нужен online-режим урока;
- смена аккаунта — у изолированной сборки нет UI смены аккаунта (как и раньше:
  изоляция доказана на JVM, 2.3/5fb40db4).
