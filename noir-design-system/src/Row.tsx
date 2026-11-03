import type { CSSProperties, ReactNode } from 'react';
import { useNoir } from './NoirProvider';
import { font, line, radius, surface, text } from './tokens';

/**
 * Рядок списку `.row` усередині Group.
 *
 * Плитка іконки 34 (радіус 10, фон surface-3) + назва 13.5 + підпис 12
 * + значення моно 12 + шеврон або власний trailing (світч).
 * min-height 56, padding 13/16, розділювач hair.
 */
export function Row({
  title,
  subtitle,
  value,
  icon,
  trailing,
  divider = true,
  onClick,
  style,
}: {
  title: string;
  subtitle?: string;
  /** Моно, tabular-nums. */
  value?: string;
  icon?: ReactNode;
  trailing?: ReactNode;
  divider?: boolean;
  onClick?: () => void;
  style?: CSSProperties;
}) {
  const { accent } = useNoir();

  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        minHeight: 56,
        padding: '13px 16px',
        borderBottom: divider ? `1px solid ${line.hair}` : 'none',
        cursor: onClick ? 'pointer' : 'default',
        ...style,
      }}
    >
      {icon ? (
        <div
          style={{
            width: 34,
            height: 34,
            flex: '0 0 auto',
            borderRadius: 10,
            background: surface.s3,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: accent,
          }}
        >
          {icon}
        </div>
      ) : null}

      <div style={{ flex: '1 1 auto', display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span style={{ fontSize: 13.5, fontWeight: 600, color: text.t1 }}>{title}</span>
        {subtitle ? <span style={{ fontSize: 12, color: text.t3 }}>{subtitle}</span> : null}
      </div>

      {value ? (
        <span style={{ fontFamily: font.mono, fontFeatureSettings: '"tnum"', fontSize: 12, color: text.t3 }}>
          {value}
        </span>
      ) : null}

      {trailing ?? (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={text.off} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="m9 6 6 6-6 6" />
        </svg>
      )}
    </div>
  );
}

Row.displayName = 'Row';
