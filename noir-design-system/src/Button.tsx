import type { CSSProperties, ReactNode } from 'react';
import { useNoir } from './NoirProvider';
import { alpha, font, fx, line, radius, status, surface, text } from './tokens';

export type ButtonVariant = 'primary' | 'ghost' | 'gold' | 'done' | 'disabled';

/**
 * Кнопка NOIR.
 *
 * Шрифт — JetBrains Mono 12/700 uppercase, tracking .12em. Archivo на кнопках заборонено.
 * Радіус 12, мінімальна висота 48, padding 13/18.
 * Правило: одна залита primary на екран; поруч із нею — тільки ghost.
 */
export function Button({
  variant = 'primary',
  children,
  onClick,
  style,
}: {
  variant?: ButtonVariant;
  children: ReactNode;
  onClick?: () => void;
  style?: CSSProperties;
}) {
  const { accent } = useNoir();

  const base: CSSProperties = {
    fontFamily: font.mono,
    fontSize: 12,
    fontWeight: 700,
    letterSpacing: '0.12em',
    textTransform: 'uppercase',
    borderRadius: radius.md,
    minHeight: 48,
    padding: '13px 18px',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    border: '1px solid transparent',
    cursor: variant === 'disabled' ? 'default' : 'pointer',
  };

  const skins: Record<ButtonVariant, CSSProperties> = {
    primary: { background: accent, color: text.ink, boxShadow: fx.accentGlow(accent) },
    ghost: { background: surface.s3, borderColor: line.outline, color: text.t1 },
    gold: { background: 'transparent', borderColor: alpha(status.gold, 0.52), color: status.gold },
    done: { background: surface.s2, borderColor: alpha(status.success, 0.45), color: status.success },
    disabled: { background: surface.s2, color: text.off },
  };

  return (
    <button
      type="button"
      disabled={variant === 'disabled'}
      onClick={onClick}
      style={{ ...base, ...skins[variant], ...style }}
    >
      {children}
    </button>
  );
}

Button.displayName = 'Button';
