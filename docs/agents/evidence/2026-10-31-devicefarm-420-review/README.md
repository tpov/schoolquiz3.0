# Device Farm · джорни 4.20-partial (автор → Send to arena → ревью) — v3: FALL past v2, blocked by the client's economy gate (local wallet 0) (31.10)

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
