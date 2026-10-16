# 2026-10-16 — internal-трек опубликован: 4.0.0 (40000), тестеры привязаны

Смена 15–16.10 (ZCode, сессия sandbox-purchase). Результаты для эпика commerce:

1. Ключ прод-подписи восстановлен: /workspace/keys/Schoolquiz3.0.jks. alias key0,
   SHA1 4C:72:45:15:63:BF:88:EF:16:1A:D1:1B:AA:7C:87:53:6C:34:57:D3 — подтверждён Play
   (бандл ПРИНЯТ) и тремя историческими прод-AAB из git-истории.
2. AAB 4.0.0 (40000) опубликован в track internal — edit закоммичен
   (04693525195249369731), статус completed, подтверждено API. Попутно починено:
   consumer-rules.pro для всех Android-модулей, SDK 36 lift (перенос из
   chore/targetsdk-36-next), dex-merge куча 6GB.
3. Декларации заполнены: финансовые функции — нет; здоровье — нет.
4. Тестеры: oleg12794@gmail.com (License testers, авто) + SchoolQuiz3.0 (5, включая
   oodipsorpa — добавлен владельцем 16.10); License testing для SchoolQuiz3.0 ВКЛЮЧЁН.
5. SA-ключ ротирован (после утечки в публичный git): старые 0d54a7e117/db501e845
   ОТЗВАНЫ, действующий d6fcf09c в /etc/tpov (0600); pubsub.publisher выдан; RTDN
   проверен end-to-end (kind=TEST, RECORDED).
6. Приложение установлено из Google Play на Pixel (oleg12794): 40000/4.0.0 работает.

## Блокер (Google-side, не наш)

Opt-in под oleg12794 и oodipsorpa отвечает «hasn't yet been invited» —
приглашения из свежепривязанных списков не разосланы (до 48ч от привязки). До этого
покупки на устройствах невозможны; магазин продолжает отдавать старый internal 3010
(«створено для старішої версії Android» на Android 15/16).

## Разблокировка

Opt-in любым тестером (ссылка play.google.com/apps/internaltest/4701382105842016287)
→ «Стати тестувальником» → Update на странице приложения → 40000 → истории 7.1–7.6.
