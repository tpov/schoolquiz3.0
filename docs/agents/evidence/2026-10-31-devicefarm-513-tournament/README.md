# Device Farm · джорни 5.13 (организатор турнира) — FAIL: флоу не доходит до вкладки событий (31.10)

Вердикт: **FAILED (1 прогон, 1.37 устройстве-минуты)**. Связь устройство→риг доказана
(первый синк «Last sync» — COMPLETED), вся серверная приборка отработала идеально
(setup-513: курс `df513-course` 80 вопросов, кошелёк 3000, оппонент-аноним готов).
Флоу упал на первом навигационном шаге организатора: «QUALIFIER TOURNAMENT» в открытой
шторке не найден — в v4.0.1 события живут на вкладке EVENTS бокового меню (или ниже
скролла шторки), флоу не делает ни переключения вкладки, ни scrollUntilVisible.

## Прогон

- Run ARN: `arn:aws:devicefarm:us-west-2:453713320584:run:69133ff9-bd68-4307-82e3-02855263c8f8/d1f6a54f-24cb-4904-82d4-11006bc6907d`
- Спека: `upload:…/f9e878b2-b7fb-46a4-8399-4c5da30a669a` (SUCCEEDED, мост Mac→AWS).
- Артефакты: `run/device-farm/513-tournament/` (testspec output, видео, logcat).

Прошли: launch → HOME QUESTS → меню → SYNC NOW → «Last sync: 31.10.2026 15:00» (скрин
513-synced). Упало: `Tap on "QUALIFIER TOURNAMENT"... FAILED — Element not found`.

## Доказательство причины (кадры видео + код)

OCR открытой шторки (после синка):

```
Home quests / Archive / My lessons: statuses / Curation / Inbox / Опросы /
Operation history / [DESIGN] CATALOG / SYNC NOW / ABOUT / Last sync: 31.10.2026 15:00
```

Секций событий в видимой части шторки нет. При этом в коде:
`DrawerSection.EventsSection.QualifierTournament` существует без ролевых требований
(`requiredRoles = emptyMap()`, DrawerSection.kt), т.е. пункт доступен каждому — но
попадает в шторку на вкладке `Tab.EVENTS` (в шторке есть нав-полоса вкладок:
`Tab.entries.map { NoirNavItem(…) }`, AppShellScreen.kt:313) и/или ниже скролла.
Старый риг-капчер 10.10 (ui-menu.xml, v0.1.0) вкладок не имел — флоу писался по той
картине. `ACTIVE EVENTS` дополнительно требует ролей TESTER/MODERATOR/ADMIN/DEVELOPER —
свежему аккаунту недоступен (флоу использует его позже как «Browser: Active events» —
ещё одна точка, требующая правки: либо роли приборкой, либо другой вход).

## Препарация (server-side) — вся сработала

watch-513 (лог `run/device-farm/watch-513/watch.log`): `await-users 1` поймал аккаунт
устройства `aYrGUTDft9PaUSMOZbwzIIO37XSu`, `setup-513` создал курс-источник (80 вопросов),
поднял кошелёк (nolics 3000, boxCount 100) и оппонента `P4DnTiNE…` с токеном; далее ждал
событие `df513-course` (не дождался — флоу не дошёл до создания турнира).

## Нужная правка флоу (в отчёт владельцу; пакет заморожен)

1. После синка закрыть шторку/попасть на вкладку событий — фактически работающий вход
   уточнить на риге (переключение вкладки EVENTS в шторке либо скролл шторки):
   `scrollUntilVisible: { element: { text: "QUALIFIER TOURNAMENT" }, direction: DOWN }`
   и/или `tapOn: "Events"` перед этим.
2. Шаг «ACTIVE EVENTS» заменить на доступный свежему аккаунту вход (или выдавать роли
   приборкой в watch-513).
3. Далее джорни упрётся в economy-consent при первом результате квалификации — нужна
   закрывашка «Accept and send» (см. evidence 2026-10-31-devicefarm-99-accessibility).
