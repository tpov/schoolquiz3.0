# Решения владельца: подписки Google Play (premium) — 2026-10-10

## Директива
«Короче делай как делаешь без автопополнения» — все премиум-планы prepaid (передоплата), БЕЗ автоматического продления. Идея авто-продления со скидкой 15% отложена владельцем.

## Создано и АКТИВИРОВАНО (4 подписки, у каждой 1 активный base plan)
| ID подписки | Название | Base plan ID | Тип | Период | Базовая цена |
|---|---|---|---|---|---|
| premium_1d | Premium 1 day | premium-1d-prepaid | prepaid | 1 день | 45 UAH (~54,99 с НДС) |
| premium_7d | Premium 7 days | premium-7d-prepaid | prepaid | 1 неделя | 200 UAH |
| premium_30d | Premium 1 month | premium-30d-prepaid | prepaid | 1 месяц | 650 UAH |
| premium_365d | Premium 1 year | premium-365d-prepaid | prepaid | 1 год | 5000 UAH |

Цены: UAH-база, автоконверсия во все 175 стран (минимум Google ~$0.99 не давал поставить 20 UAH/день → 45 UAH).
Лестница цен 45/200/650/5000 — черновик владельца, меняется свободно до релиза.
«Allow extension» = Allow (пользователь может сам продлить prepaid в сторе — это НЕ автопродление).

## Технические находки (для будущих сессий)
1. Баг Play Console: поле «Base plan ID» на блюре ложно помечается invalid («Must start with a number or lowercase letter…») при ЛЮБОМ валидном значении → «Your changes couldn't be saved» без сетевых запросов. Из-за этого же массовое «Set prices» выглядело применённым, но сохранение падало.
2. Обход: заполнять поле через native setter + диспатч input/change/compositionend (bubbles), затем blur; guard повторять перед каждым Save. Реальные клики по радио/дропдаунам обязательны (JS-клик по radio Prepaid не рендерит секцию Duration → модель без billingPeriod → save фейл).
3. «Set prices»: select all по gridcell (не по checkbox), кнопка «Set price» (не «Set prices»), цена UAH → Update. Статусы «Error Set a price» в таблице бывают несброшенной отрисовкой виртуального скролла — проверять медленным скроллом до низа (реальные ошибки видны в row innerText).
4. Покупка в приложении: buy option ID = base plan ID (prepaid) — для проверки покупок использовать premium-1d-prepaid и т.д.

## Правка владельца (2026-10-11): дневной тариф убран
«Уберем оплату на день, используя старые цены» — base plan premium-1d-prepaid ДЕАКТИВИРОВАН через Play Console (статус Inactive). Подписка premium_1d остаётся без активных планов → недоступна к покупке.
Итоговая полка: premium_7d (200 UAH/неделя), premium_30d (650 UAH/месяц), premium_365d (5000 UAH/год).
История 2-1 «четыре длительности» = ТРИ длительности. В промпт лупа schoolquiz внесено.

## ФИНАЛЬНАЯ МОДЕЛЬ (2026-10-11, решение владельца)
«Давай сделаем одну подписку месячную с автопополнением и 3 дня бесплатно, 200 грн в месяц».
- premium_30d / base plan premium-30d-autorenew: Monthly, auto-renewing, 200 UAH/мес — АКТИВЕН.
- Оффер trial-3d на нём: Free trial 3 дня, new customer acquisition — АКТИВЕН.
- Деактивированы: premium-1d-prepaid, premium-7d-prepaid, premium-30d-prepaid, premium-365d-prepaid (все Inactive).
- Истории про 4 длительности (2-1 и связанные) — выполнять как «одна месячная автоподписка + триал 3 дня».
