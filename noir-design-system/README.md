# NOIR — дизайн-система SchoolQuiz

Інженерна темрява на чистому чорному: широкий технічний гротеск, моно-числа,
hairline-структура і рідкісні рольові ефекти. SpaceX/Bloomberg, а не різнобарвна дитяча гра.

Повна канонічна специфікація — `NOIR-SPEC.md`. Токени — `tokens/noir.tokens.json` (v2.0,
зафіксовано 2026-08-18). React-порт у `src/`.

## Скелет і скіни

Один токен-скелет, три скіни акценту з однаковими L≈0.66 і C≈0.165 — змінюється лише hue:
`azure #0599EF` (дефолт), `amethyst #9680F2`, `teal #00AFAF`.
Статуси (`gold`, `success`, `danger`, `violet`) — константи, від скіна не залежать.

```tsx
import { NoirProvider, Button, Group, Row, Switch } from 'noir-design-system';
import 'noir-design-system/src/theme.css';

<NoirProvider skin="azure">
  <Group>
    <Row title="Нагадування" subtitle="Щодня о 19:00" trailing={<Switch checked />} />
  </Group>
  <Button variant="primary">Підтвердити</Button>
</NoirProvider>
```

## Правила, які не обговорюються

- **Elevation — це світліша поверхня, не тінь.** На чистому чорному тінь невидима.
  Підняття: `bg → s1 → s2 → s3 → s4`, плюс кромка `top-light` на картках.
- **Кнопки — моно.** JetBrains Mono 12/700 uppercase, tracking .12em. Archivo — тільки заголовки.
- **Один акцент на екран, максимум два застосування** — кікер і головна дія.
- **Одна залита primary на дію.** Поруч — тільки ghost.
- **Ефект = роль.** Бюджет — максимум 2 ефекти на екран:
  золотий контур + sheen (Pro), фіолетовий контур (лімітоване),
  акцентне світіння (активна дія, фокус), radial-свічення режиму (фон екрана питання).
- **Контраст:** текст ≥4.5:1, великий текст та іконки ≥3:1. Єдиний виняток — `text-off` на disabled.
- **Тач-цілі ≥44**, кнопки 48, ряди списку min-height 56.
- **Числа, час і ціни — моно з `tabular-nums`.**

## Заборонено

Фіолетовий або веселковий градієнт як фон · емодзі замість іконок · Archivo на кнопках ·
кутова дужка як заголовок секції (це плашка `SectionHeader`) · чекбокс для on/off (це `Switch`) ·
плаваючі надуті картки з тінню (це `Group`) · hover, що робить текст сірішим ·
дві залиті primary на одну дію · вигадані метрики і порожні картки-заглушки ·
raw hex поза `src/tokens.ts`.

## Стан портів

| Артефакт | Стан |
|---|---|
| `tokens/noir.tokens.json` | канонічні значення, набір поверхонь `soft` |
| `src/` (React) | цей пакет — токени + 9 компонентів |
| `android/core/designsystem/.../noir/` (Compose) | тема на `soft`-поверхнях, компоненти екранів переведені; шрифти Archivo variable (wdth 112) + JetBrains Mono вшиті в APK (`res/font`), підключені в `NoirTheme` |
| Skillify (XML) | кольори, кнопки і `.gt`-бар зроблено; лишились світчі, hairline-групи, ряди з іконками |

Шрифти: Archivo variable (wdth 112) і JetBrains Mono. У React — з Google Fonts у `src/theme.css`;
у Compose — вшиті варіативні TTF (`NoirFonts.kt`), тож застосунок читає як NOIR і офлайн.
