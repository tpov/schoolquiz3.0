/**
 * NOIR — токени дизайн-системи.
 * Джерело правди: tokens/noir.tokens.json (v2.0, зафіксовано 2026-08-18).
 * Поверхні — набір `soft`; жодних raw hex поза цим файлом.
 */

/** Поверхні. На чистому чорному тінь невидима — elevation передається світлішою поверхнею. */
export const surface = {
  /** Фон застосунку. OLED-чорний, не #121212. */
  bg: '#000000',
  /** Картка — помітний острів на чорному. */
  s1: '#1E1E24',
  /** Бар, поле вводу, чип. */
  s2: '#26262E',
  /** Піднята картка, плитка іконки, ghost-кнопка. */
  s3: '#2E2E36',
  /** Діалог, bottom sheet, тумблер-off. */
  s4: '#383840',
} as const;

/** Обводки. Старий #2C2C2C давав 1.1:1 — його не було видно. */
export const line = {
  outline: '#44444E',
  outlineStrong: '#5E5E6A',
  /** Внутрішні розділювачі груп. */
  hair: 'rgba(255,255,255,0.065)',
  /** «Зафрезерована» кромка карток — стоїть скрізь, замінює тінь. */
  topLight: 'inset 0 1px 0 rgba(255,255,255,0.045)',
} as const;

/** Текст. Усі пари, крім disabled, проходять WCAG AA. */
export const text = {
  /** Заголовки, числа. 16.6:1 на surface-1. */
  t1: '#FFFFFF',
  /** Основний текст. 8.4:1. */
  t2: '#B8B8B8',
  /** Другорядний, підписи. 6.1:1. */
  t3: '#9C9CA6',
  /** Лише disabled — єдиний дозволений виняток AA. */
  off: '#6E6E78',
  /** Текст та іконки на заливці акценту. */
  ink: '#05090F',
} as const;

/** Скіни акценту: один скелет, змінюється лише hue. L≈0.66, C≈0.165. */
export const skin = {
  /** Дефолт. oklch(0.660 0.165 245) — hue зсунуто від Google Blue. 6.8:1 на чорному. */
  azure: '#0599EF',
  /** oklch(0.670 0.165 290). 6.7:1. */
  amethyst: '#9680F2',
  /** oklch(0.680 0.120 195). Найконтрастніший — 7.8:1. */
  teal: '#00AFAF',
} as const;

export type SkinName = keyof typeof skin;

/** Статуси. Константи — не залежать від обраного скіна. */
export const status = {
  /** Pro / преміум. */
  gold: '#FFD700',
  /** Успіх, легкий режим. */
  success: '#5CC97A',
  /** Помилка, деструктив, складний режим. */
  danger: '#F0564B',
  /** Лімітоване, сезонне. */
  violet: '#9680F2',
} as const;

/** Режим раунду керує свіченням фону на екрані питання. */
export type ModeName = 'arena' | 'easy' | 'hard';

export const radius = { sm: 8, md: 12, lg: 16, xl: 20, pill: 999 } as const;

/** База 4. */
export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const font = {
  /** Тільки display і заголовки. wdth 112, uppercase, tracking +.06…+.22em. */
  display: '"Archivo", system-ui, sans-serif',
  body: 'system-ui, "Roboto", sans-serif',
  /** Числа, таймери, ціни, кікери і ВСІ кнопки. */
  mono: '"JetBrains Mono", ui-monospace, monospace',
} as const;

export const motion = {
  fast: 120,
  base: 180,
  slow: 280,
  standard: 'cubic-bezier(0.2, 0, 0, 1)',
  emphasis: 'cubic-bezier(0.2, 0.7, 0.3, 1.15)',
} as const;

/** hex → «r,g,b» для rgba-ефектів. */
export function rgb(hex: string): string {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)).join(',');
}

/** rgba з токена. */
export function alpha(hex: string, a: number): string {
  return `rgba(${rgb(hex)},${a})`;
}

/**
 * Рольові ефекти. Бюджет — максимум 2 на екран, кожен із роллю.
 * Без ролі ефекту немає: це не декор.
 */
export const fx = {
  /** Тільки головна дія, фокус поля, обраний елемент. */
  accentGlow: (accent: string) => `0 0 18px ${alpha(accent, 0.38)}`,
  /** Тільки Pro / преміум. Одна картка на екран. */
  goldRing: `0 0 24px ${alpha(status.gold, 0.14)}`,
  goldBorder: alpha(status.gold, 0.55),
  /** Тільки лімітоване / сезонне. */
  violetRing: `0 0 20px ${alpha(status.violet, 0.12)}`,
  violetBorder: alpha(status.violet, 0.55),
  /** Фон екрана питання: колір несе режим раунду. */
  modeBed: (color: string) =>
    `radial-gradient(circle at 50% 52%, ${alpha(color, 0.17)} 0%, rgba(0,0,0,0) 62%)`,
} as const;
