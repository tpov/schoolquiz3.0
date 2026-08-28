import type { CSSProperties } from 'react';
import { useNoir } from './NoirProvider';
import { surface } from './tokens';

/**
 * Прогрес раунду — один сегмент на питання, заповнені кольором режиму.
 * Свідомо не «пігулка»: структура має читатись як шкала.
 */
export function ProgressSegments({
  total = 12,
  done = 0,
  style,
}: {
  total?: number;
  done?: number;
  style?: CSSProperties;
}) {
  const { glow } = useNoir();

  return (
    <div style={{ display: 'flex', gap: 4, ...style }}>
      {Array.from({ length: total }, (_, i) => (
        <span key={i} style={{ flex: 1, height: 3, background: i < done ? glow : surface.s4 }} />
      ))}
    </div>
  );
}

ProgressSegments.displayName = 'ProgressSegments';
