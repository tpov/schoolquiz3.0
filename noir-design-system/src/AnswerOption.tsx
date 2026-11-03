import type { CSSProperties } from 'react';
import { useNoir } from './NoirProvider';
import { alpha, font, line, radius, surface, text } from './tokens';

/**
 * Варіант відповіді на екрані питання.
 *
 * Обраний підсвічується кольором РЕЖИМУ (arena/easy/hard), а не акцентом:
 * гравець бачить складність раунду не читаючи підпис.
 */
export function AnswerOption({
  optionKey,
  label,
  selected = false,
  onSelect,
  style,
}: {
  /** A / B / C / D — моно 11/700. */
  optionKey: string;
  label: string;
  selected?: boolean;
  onSelect?: () => void;
  style?: CSSProperties;
}) {
  const { glow } = useNoir();

  return (
    <div
      role="button"
      onClick={onSelect}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        padding: '15px 16px',
        borderRadius: radius.md,
        background: selected ? alpha(glow, 0.14) : alpha(surface.s2, 0.88),
        border: `1px solid ${selected ? glow : line.outline}`,
        cursor: 'pointer',
        ...style,
      }}
    >
      <span
        style={{
          width: 20,
          fontFamily: font.mono,
          fontFeatureSettings: '"tnum"',
          fontSize: 11,
          fontWeight: 700,
          color: selected ? glow : text.t3,
        }}
      >
        {optionKey}
      </span>
      <span style={{ fontSize: 14, fontWeight: 500, lineHeight: 1.36, color: text.t1 }}>{label}</span>
    </div>
  );
}

AnswerOption.displayName = 'AnswerOption';
