# Owner decision 2026-10-10 — Play SKU inventory (7.1, частичное, hitl остаётся)

Выполнено агентом в Play Console (аккаунт TPOV 6672082928150299984, app com.tpov.schoolquiz):
- Созданы разовые продукты (in-app, активны, цены по регионам от базовой):
  - gold_pack_small (Gold Pack Small — 10 gold), base price 1 USD;
  - gold_pack_medium (Gold Pack Medium — 60 gold), base price 200 UAH;
  - gold_pack_large (Gold Pack Large — 150 gold), base price 500 UAH.
  SKU соответствуют StoreProductId в StoreProduct.kt и goldPacks в economy-constants.json (10/60/150).

НЕ создано (блокер): подписки premium_1d/7d/30d/365d и любые цены —
при сохранении базового плана/цен в create-флоу Play Console стабильно возвращает
«Не вдалося зберегти зміни» (7333E1CC / 441E33DD / 499A7808). Причина установлена:
у аккаунта отсутствует Google merchant-профиль (платежный центр = пуст, «Додати спосіб оплати»).
Создание merchant-профиля (карта, адрес, банковские реквизиты) — только владелец.

Evidence: скриншоты процесса в artifacts сессии агента 2026-10-10; RSA-ключ биллинга
перенесён из Play Console (Налаштування монетизації -> Ліцензування) в
/etc/tpov/schoolquiz/play-billing-rsa-key.txt (0600).

7.1 остаётся hitl/blocked до создания merchant-профиля владельцем; после этого
premium-подписки создаются по той же схеме (проверено: форма подписки работает до сохранения).
