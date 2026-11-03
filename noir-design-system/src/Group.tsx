import type { CSSProperties, ReactNode } from 'react';
import { line, radius, surface } from './tokens';

/**
 * Група `.group` — hairline-блок, а НЕ плаваюча картка.
 *
 * Обводка hair, радіус 16, фон surface-1, зафрезерована кромка зверху, overflow hidden.
 * Тіней немає: на чистому чорному вони невидимі, підняття дає світліша поверхня.
 */
export function Group({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return (
    <div
      style={{
        border: `1px solid ${line.hair}`,
        borderRadius: radius.lg,
        background: surface.s1,
        boxShadow: line.topLight,
        overflow: 'hidden',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

Group.displayName = 'Group';
