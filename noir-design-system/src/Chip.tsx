import type { CSSProperties, ReactNode } from 'react';
import { useNoir } from './NoirProvider';
import { alpha, font, line, radius, status, surface, text } from './tokens';

export type ChipRole = 'neutral' | 'accent' | 'gold' | 'violet' | 'success' | 'danger';

/**
 * Чип `.chip` — моно uppercase 10, tracking .12em, пігулка.
 * Роль дає колір тексту, обводку роль×45% і фон роль×10%.
 */
export function Chip({
  label,
  role = 'neutral',
  icon,
  style,
}: {
  label: string;
  role?: ChipRole;
  icon?: ReactNode;
  style?: CSSProperties;
}) {
  const { accent } = useNoir();
  const roleColor =
    role === 'accent' ? accent
    : role === 'gold' ? status.gold
    : role === 'violet' ? status.violet
    : role === 'success' ? status.success
    : role === 'danger' ? status.danger
    : null;

  return (
    <span
      style={{
        fontFamily: font.mono,
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        borderRadius: radius.pill,
        padding: '6px 12px',
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        color: roleColor ?? text.t2,
        background: roleColor ? alpha(roleColor, 0.1) : surface.s2,
        border: `1px solid ${roleColor ? alpha(roleColor, 0.45) : line.hair}`,
        ...style,
      }}
    >
      {icon}
      {label}
    </span>
  );
}

Chip.displayName = 'Chip';
