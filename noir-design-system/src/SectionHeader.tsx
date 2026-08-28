import type { CSSProperties, ReactNode } from 'react';
import { useNoir } from './NoirProvider';
import { alpha, line, radius, status } from './tokens';

export type HeaderRole = 'accent' | 'gold' | 'violet' | 'danger';

/**
 * Секційний заголовок `.gt` — плашка на всю ширину.
 *
 * Іконка 18 у кольорі ролі + титул 13.5/600 тим самим кольором.
 * Фон — градієнт роль×16% → surface-2 55%. Кутова дужка як заголовок секції заборонена.
 */
export function SectionHeader({
  title,
  role = 'accent',
  icon,
  style,
}: {
  title: string;
  role?: HeaderRole;
  /** SVG 18×18, stroke-based. Емодзі як іконки заборонені. */
  icon?: ReactNode;
  style?: CSSProperties;
}) {
  const { accent } = useNoir();
  const color =
    role === 'gold' ? status.gold : role === 'violet' ? status.violet : role === 'danger' ? status.danger : accent;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '13px 15px',
        border: `1px solid ${line.hair}`,
        borderRadius: radius.md,
        background: `linear-gradient(90deg, ${alpha(color, 0.16)} 0%, rgba(38,38,46,0.55) 100%)`,
        ...style,
      }}
    >
      {icon ? <span style={{ display: 'flex', color }}>{icon}</span> : null}
      <span style={{ fontSize: 13.5, fontWeight: 600, color }}>{title}</span>
    </div>
  );
}

SectionHeader.displayName = 'SectionHeader';
