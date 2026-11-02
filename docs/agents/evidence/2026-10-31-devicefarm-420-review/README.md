# Device Farm · джорни 4.20-partial (автор → Send to arena → ревью) — v6 (клиент с 4 фиксами, свежий app 9cad8b0e): «Checking: „Слова“ · „Факты“» ДОКАЗАН живьём (фикс aeb03366), вердикты WORDS/FACTS живой glm-4.7 DONE, фи 1000 списан; конвейер застрял на TRANSLATE PENDING — «Returned»/исходы автора не достигнуты, финальный ассерт честно упал (01.11)

## Волна v7 (подготовка, прогон не исполнялся) — свип терпеливый (02.11)

- scripts/devicefarm-rig-prep.js sweepQueue больше не бросает очередь по первому
  IDLE-свипу (v6: «четвёртый свип сразу IDLE», TRANSLATE остался PENDING, конвейер
  RUNNING, «Returned» не достижим). Свипы идут циклом с паузами SWEEP_IDLE_WAIT_MS
  (10 с) до терминала или бюджета SWEEP_PATIENT_MS (10 минут; оба — env). Продолжает
  цикл queueBusy(): конвейер RUNNING; UNDER_REVIEW-заявка или QUEUED-предложение без
  конвейера (ещё ни разу не клейменное); терминал (QUEST_REVIEWED/REVIEW_DELAYED/
  CHECK_PASSED/CHECK_FAILED/CHECK_DELAYED) отпускает. Латентность живого
  translate-вызова glm-4.7 укладывается в лизу 90 с; паузы закрывают бэкоффы между
  стадиями.


## Прогон v6 (пакет sha256 86dbf7a4…, клиент aeb03366/6b07ce61/3cd726de/719b4dbb; app-аплоад 9cad8b0e — сборка 01.11) — «Checking» появился, заявка и вердикты живые; фолл ушёл в ожидаемый терминал DELAYED, которого при живой модели не бывает (01.11)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/976e3072-203b-417f-a010-e8fa2ec3c6fe`,
  FAILED, 12.67 устройсте-минуты, окно 03:04:31–03:17:11 UTC; спека `upload:…/83e8a338…`
  (SUCCEEDED, SigV4-пут с Mac). Пул smoke-pixel10. Артефакты:
  `run/device-farm/420-review-v6/` (кадры f300/f640; **f640, 03:15 — строка «DF420 review
  journey quest» с бейджем DRAFT и «Checking: “Words” · “Facts”»**); приборка —
  `run/device-farm/watch-420-v6/watch.log`.
- **OPS-предпосылка волны:** Device Farm ставит app из app-аплоада (`c1bc3e88`, 28.10 —
  БЕЗ ноябрьских фиксов), пакетные APK туда не попадают. Свежий mvp-APK залит отдельным
  ANDROID_APP `upload:…/9cad8b0e` (SigV4 CreateUpload+PUT с Mac). Релей «create-upload VPS →
  presigned-URL → PUT» мёртв: presigned-пут отдаёт 403 SignatureDoesNotMatch с ОБЕИХ сторон
  (VPS и Mac) даже на probe-файле — инцидент канала глубже, чем «битый VPS-PUT».
- Цепочка: topup по автоотчёту ДО первого синка (uid `V6JpXA2…`, 0→3000 в оба поля,
  watcher) → авторинг → SYNC NOW → «Send to arena» → economy-гейт пройден → «…queued for
  arena» → заявка `quest_review_requests/arena-submission-hsfscy6j34tj` UNDER_REVIEW,
  **фи 1000 списан: кошелёк 3000→2000 (оба поля)**.
- **Фикс aeb03366 доказан живьём:** `Assert "Checking: “Words” · “Facts”" is visible...
  COMPLETED` (v5: строка оставалась DRAFT и после 5 минут и двух синков) — исходы
  перечитываются по статусу черновика и контентному синку, скрин 420-under-review.
- Живые вердикты glm-4.7: `review_verdicts/…_WORDS_1` («исключительно служебный
  плейсхолдерный текст…») + `…_FACTS_1` — DONE за 2-3 свипа (lease 90 c, stageCalls 2,
  watch-420-v6). TRANSLATE PENDING/0 — не атемптится вовсе (v5: честные TIME_BUDGET;
  v6: четвёртый свип сразу IDLE) → конвейер RUNNING, статус заявки UNDER_REVIEW.
  Владельцу: бюджет свипа vs латентность translate-вызова — теперь это блокирует и
  терминал (REVIEWED→исходы автора «Returned: …»), а не только агрегат.
- Фолл: `Assert "Check is taking longer than expected"` FAILED (420 c) — флоу заморожен
  под no-model-исход DELAYED; с живой моделью конвейер не DELAYED, и строка честно
  остаётся «Checking» (кадр f640). Оба условных ретрай-синка отработали COMPLETED.


## Прогон v5 (пакет sha256 d2164b1a…, коммит ebf8e0c9; lease свипов 90 c) — платная подача и вердикты повторены, фолл ушёл в новый класс (31.10 ночь)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/71329e73-0813-4d55-b496-9dafafe5aecd`,
  FAILED, 15.18 устройсте-минуты, окно 23:13:31–23:29:19 UTC; спека `upload:…/b5419a25…`
  (SUCCEEDED, релей create-upload VPS → presigned-URL → PUT с Mac). Пул smoke-pixel10.
  Артефакты: `run/device-farm/420-review-v5/` (кадры f001–f004); приборка —
  `run/device-farm/watch-420-v5/watch.log` (+ `final-sweeps.js` — терминальные свипы).
- **Вся цепочка v4b повторена живьём + правка v5 отработала:** авторинг → SYNC NOW
  (топап 3000 в pointsNolics по АВТООТЧЁТУ до первого синка, watcher) → «Send to arena» →
  economy-гейт пройден → «…queued for arena» → Back → дренажный SYNC NOW → My quests →
  **повторный SYNC NOW (правка v5, видна в трейсе: Open menu → SYNC NOW → Last sync →
  My quests)** → `Assert "Checking: “Words” · “Facts”"` FAILED.
- **Сервер (база):** заявка `quest_review_requests/arena-submission-1isyv164wa8gi`
  создана **23:23:14.843Z**, status=UNDER_REVIEW; **цикл-фи 1000 списан: кошелёк
  3000→2000 (оба поля)**. Конвейер `review_pipeline/…`: **WORDS DONE + FACTS DONE**
  (`review_verdicts/…_WORDS_1`, `…_FACTS_1` — настоящие вердикты живой glm-4.7:
  WORDS FAIL — маркер «DF420»; FACTS FAIL — плейсхолдер-ORDERING без проверяемых фактов,
  источник ru.wikipedia.org/wiki/Педагогика).
- **Драйвер (правка lease 900→90000 подтверждена):** 2 стадийных вызова за свип,
  WORDS+FACTS до DONE за 2 свипа (v4: почти каждый свип LEASE_LOST/TIME_BUDGET по 0
  стадий). **Новый ops-кандидат:** TRANSLATE/агрегат не терминалятся — воркер честно
  атемптит (TIME_BUDGET, attempted=1, stageCalls=2), но translate-вызов живой модели не
  укладывается в бюджет свипа даже при lease 240000 (80+ свипов, TRANSLATE PENDING,
  статус заявки UNDER_REVIEW). Владельцу: бюджет свипа vs латентность translate-вызова.
- **Фолл — НОВЫЙ класс (кадр f004, 23:28:32):** «MY QUESTS» — строка «DF420 review
  journey quest» с бейджем **DRAFT**, «Checking: “Words” · “Facts”» НЕ появился, хотя
  заявка к этому моменту жила ~5 минут и после неё было два полных синка. **Гонки v4b
  больше нет** — клиентская строка квеста в принципе не отражает UNDER_REVIEW на экране
  «My quests» (клиентский кандидат: статус заявки не маппится в строку/экран). «Исходы
  автора» не достигнуты (следующий шаг владельца; флоу заморожен).
- ops: все четыре спеки v5 залиты релеем «create-upload с VPS → presigned-URL → PUT с
  Mac» (Mac-креды не нужны) — прямой VPS-PUT 403 даже на малых файлах; df-collect с VPS
  при этом работает (GET жив). Волна v5 целиком: 24.33 устройсте-минуты
  (2.71+4.23+2.21+15.18); list-remote-access-sessions = 0 после всех прогонов.

## Прогоны v4/v4b (пакет sha256 8c1b2426…, коммит 51eeafdb) — 31.10 ночь

Итог кампании v4 по 4.20: **главная цель доказана живьём — заявка дошла до сервера, цикл-фи
списан, конвейер «Слова»/«Факты» дал настоящие вердикты glm-4.7 по реальному контенту
устройства; maestro-флоу при этом FAILED на финальном ассерте в обоих прогонах** (клиенту
не хватило ещё одного синка после дренажа — класс фикса «второй синк» 5.13). 2 прогона,
24.55 устройстве-минуты.

### Прогон v4b (топап до первого синка — v4-механика драйвера работает)

- Run `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/971a9e7c-06ef-4df2-bcb0-8d280394ad54`,
  FAILED, 15.29 устройстве-минуты, окно 21:23:05–21:39:16 UTC; спека `upload:…/4b2a17e3…`
  (SUCCEEDED, мост Mac→AWS: VPS-PUT по-прежнему SignatureDoesNotMatch — инцидент провайдера
  не закрыт). Артефакты: `run/device-farm/420-review-v4b/` (+ `run/device-farm/watch-420/watch.log`).
- Пройдено живьём (67 шагов COMPLETED): весь авторинг → промежуточный SYNC NOW
  (420-wallet-synced) → scroll + «Send to arena» → **economy-гейт пройден (v3-блокер снят)** →
  «…queued for arena» → Back → дренажный SYNC NOW → My quests. Упало: `Assert that
  "Checking: “Words” · “Facts”" is visible` (300 с).
- **Сервер (живые доказательства, watch-420.log + база):** автоотчёт аккаунта
  `ea9NpBz6Api3bFyBX6qF4gapsYUX` → topup ДО первого синка (before 0/0 → after nolics=3000,
  pointsNolics=3000 — v4-топап в pointsNolics работает) → **заявка
  `quest_review_requests/arena-submission-157dyt53y44e4` создана, status=UNDER_REVIEW
  (21:33:09Z)** → **цикл-фи 1000 реально списан: кошелёк 3000→2000** → конвейер
  `review_pipeline/arena-submission-157dyt53y44e4`: **WORDS DONE + FACTS DONE с настоящими
  вердиктами живой glm-4.7** (`review_verdicts/…_WORDS_1`, `…_FACTS_1`):
  - WORDS: verdict=FAIL по обоим вопросам — rationale: «…маркер «DF420»; число «420»
    устойчиво ассоциируется с наркотической субкультурой…» (модель реально читала контент
    устройства);
  - FACTS: verdict=FAIL — rationale: «задание состоит из плейсхолдерных строк… не содержит
    ни одного проверяемого факта…» + живые источники (en.wikipedia.org/wiki/Educational_assessment,
    /wiki/Test_(assessment));
  - TRANSLATE: SKIPPED (один язык). Агрегат урока остался PENDING/статус заявки UNDER_REVIEW:
    свипы драйвера бьются о бюджет (см. находку ниже).
- **Точная причина падения флоу (флоу заморожен — владельцу):** заявка создана на сервере
  21:33:0x, а последний (дренажный) синк клиента завершился за секунды ДО этого; статус
  «Checking: “Words” · “Facts”» клиент показывает только по данным синка — сам он не
  обновляется. Нужен ещё один SYNC NOW между дренажным синком и ассертом (тот же класс
  правки, что «второй синк» 5.13 v3).
- **Находка драйвера (ops):** свипы `devicefarm-rig-prep.js` идут с `leaseMs: 900` — живой
  вызов glm-4.7 длиннее лизы, поэтому почти каждый свип отчитывается TIME_BUDGET/LEASE_LOST,
  а стадии движутся по одной. Ручные свипы с lease 180000 с довели WORDS/FACTS до DONE за
  минуту. Для терминальных прогонов воркеру под живую модель нужен lease ≥ длительности
  модельного вызова.

### Прогон v4 (9.26 мин) — операционная гонка наблюдателя, серверный fee-гейт доказан

- Run `arn:…:run:69133ff9…/38f9cbcf-a725-422b-9607-6d814c5b5464`, FAILED, 9.26 мин, окно
  21:03:30–21:13:56. Артефакты: `run/device-farm/420-review-v4/` (кадры OCR в `frames/`).
- Топап НЕ успел: канал спеки съел 14 минут, первый наблюдатель истёк ровно в момент
  рождения аккаунта (21:04:26), перезапущенный взял базовую линию ПОСЛЕ аккаунта. Итог —
  кошелёк 0 и **серверный отказ, отражённый клиентом на строке квеста: «Returned:
  REVIEW_FEE INSUFFICIENT: cycle fee 1000 nolics exceeds balance 0»** (кадр f240+;
  functions/index.js:5782, баланс = pointsNolics ?? nolics). Доказаны обе стороны платной
  подачи: приём в очередь клиентом и честный серверный отказ без денег; v3-шный локальный
  refusal клиента больше не срабатывает — подача уходит на сервер и решается там.

## Run v3 (package sha256 ad9f3939…)

Verdict: **FAILED (1 run, 9.02 device-minutes; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/0e5a02f3-2222-4a46-bf96-c59e12999247`,
window 19:16:13–19:26:01 UTC)**. Spec `upload:…/d28e096d-192d-4c2b-8e70-3f51ac4cd0c6` (SUCCEEDED,
Mac→AWS bridge). Artifacts: `run/device-farm/420-review-v3/` (maestro-stdout, testspec output,
video, logcat, frames).

**Proven live (61 maestro steps COMPLETED):** the entire v2 fall point has been passed —
authoring core (structure + 2 ordering questions) → **scrollUntilVisible «Send to arena»
(the v2 fix works) → tap → «…queued for arena»** → Back → the quest is in the list → sync →
«My quests». The fall is already on the new step: `Assert that "Checking: “Words” · “Facts”"
is visible... FAILED` (300 s) — the status of the submission review did not appear.

### Cause (screen + database + functions logs; the server did not receive the submission)

- Frame of video ~19:24 (list of quests): **«Economy error: not enough nolics (have 0, need
  1000)»** ×3 on the «DF420 review journey quest» row — a client-side economy gate rejected the
  paid submission, comparing the LOCAL wallet snapshot (0).
- Server: `quest_review_requests` = 0 documents; users/3y718syh… nolics = **3000** (topup by
  watch-420 ~19:17:1x, confirmed in the database) — the topup landed on the server AFTER the
  client's first sync (19:17:08, snapshot wallet = 0) and is written by a direct admin set()
  WITHOUT a journal entry (CAP-8: the client learns only from journals) → invisible to the
  client's sync.
- The submission never went to the server: in the window 19:16–19:26 only one
  `submitMutation` (19:20:00.699, 346 ms — the quest structure, not the submission);
  `applyArenaSubmission`/`processArenaRequest` was not called. The refusal is terminal on the
  client (AD-28 quarantine): there was no re-submission (the second sync at 19:20:33 did not
  send a mutation), the error lines hang on the row.

### Precise fix for the next iteration (flows are frozen — to the owner's report)

1. **[flow]** In `420-author-submit-review.yaml`, between the completion of authoring
   («420-questions-done») and the scroll to «Send to arena», insert an intermediate
   «Open menu → SYNC NOW → wait for Last sync» — the topup by watch-420 lands on the server
   immediately after the uid appears (that is, right after the first sync), and any subsequent
   sync (if refreshProfile from sync() pulls up the users-doc — the mechanism that opened
   «Active events» in 5.13 by the second sync) must top up the local wallet to 3000 BEFORE the
   gate. Check that sync() really refreshes the wallet (if not — see point 2).
2. **[rig/prep-driver]** topup in `devicefarm-rig-prep.js` writes users/<uid> directly —
   top-ups of this kind are BY DESIGN invisible to the client. A long-term fix: to credit
   nolics via an economy event visible to the client (journaling/mutation response), otherwise
   EVERY paid journey of a fresh device risks the same gate. The same trap would have hit the
   paid entry 3 of journey 4.26 («Fee: 1000 nolics»).

## Run v2 (package sha256 50ac4967…, 31.10) — FALL on «Send to arena», historical section

Вердикт v2 (пакет sha256 50ac4967…): **FAILED (1 прогон, 9.28 устройстве-минуты; run
`arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/c91d1254-9356-45f2-a83e-fab176098343`)**,
но доказано живьём всё авторинг-ядро до готового черновика: синк → навигация «My quests» (скролл
9-го пункта шторки, правка v2 работает) → Create quest → квест/раздел/тема/урок → 2 ordering-вопроса
(easy+hard) → скрин 420-questions-done. Упало на `tapOn "Send to arena"` — кнопка ниже кромки
после добавленных вопросов (структура выросла). До UNDER_REVIEW/свитов живой модели не дошли.

## Прогон v2

- Run ARN: см. выше; спека `upload:…/36cc2067…` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/420-review-v2/` (maestro-stdout, testspec output, видео, logcat).
- Серверная приборка (watch-420.log): аккаунт устройства `0ooaOJe9pk0gkCj2PRtjwWf2shFL`,
  topup 0→3000 ноликов (до шагов платы) — до подачи заявка не доехала (кнопка не нажата),
  quest_review_requests пуст, await-review-request корректно отвалился по таймауту.

### Причина падения (кадр видео 17:36:28 + maestro-stdout)

Экран «Quest creation» после «Back to structure»: список структуры + «Easy questions» /
«Hard questions» у нижней кромки — «Send to arena» ниже зоны видимости, maestro не нашёл текст.
Ранее (до добавления вопросов) кнопка была видна — флоу не скроллит перед тапом.

### Точная правка флоу на следующую итерацию (владельцу; пакет заморожен)

Перед `tapOn: "Send to arena"` добавить
`scrollUntilVisible: { element: { text: "Send to arena" }, direction: DOWN, timeout: 15000 }`.
Дальше флоу готов: закрывашка consent уже в пакете, watch-420 + живая модель glm-4.7
(functions-emu, REVIEW_MODEL_* на месте, readModelConfig ok=true) доведут заявку до
настоящих вердиктов «Words»/«Facts» — в этом прогоне конвейер не стартовал (заявки не было).

## Прогон v1 (пакет debd5457…, 31.10 утро) — FAILED на навигации, историческая секция

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/110a4b02-2593-4e42-bb9c-70a563aee9f6`
- Спека: `upload:…/4e3b57b5-f5f8-4a91-a757-b8480cde261d` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/420-review/`.
- Прошли: launch → HOME QUESTS → меню → SYNC NOW → «Last sync: 31.10.2026 15:15».
  Упало: `Tap on "^MY QUESTS$"... FAILED — Element not found` (в v4.0.1 пункт называется
  «My quests» и лежит ниже кромки — исправлено в v2, доказано этим прогоном).

## Кадры шторки (OCR видео)

```
Home quests / Archive / Decisions / My lessons: statuses / Curation / Опросы /
Operation history / DESIGN CATALOG / Last sync: 31.10.2026 15:15 / v4.0.1-isolated
```

## Нужные правки флоу (в отчёт владельцу; пакет заморожен)

1. Вход в авторскую зону: уточнить фактический пункт шторки (для 4.26/4.20 это,
   судя по UI, «Decisions»/«Home quests» + «Create quest» на главном экране) —
   сверить на риге с ws-scrcpy и заменить `^MY QUESTS$`.
2. После правки навигации джорни упрётся в economy-consent на платной подаче
   «Send to arena» (1000 ноликов; captured==null → Required, см. evidence
   2026-10-31-devicefarm-99-accessibility) — добавить закрывашку «Accept and send»
   и после неё повторный SYNC NOW.
3. Дальше конвейер готов: watch-420 `await-review-request` гонит настоящий
   review-queue-worker (живая модель glm-4.7 в контейнере) до терминала.
