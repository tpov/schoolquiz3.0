import type { CSSProperties } from 'react';
import { useNoir } from './NoirProvider';
import { font, fx, line, radius, surface, text } from './tokens';

/**
 * Поле вводу — фон surface-2, обводка outline.
 * Фокус переводить обводку на акцент і додає світіння: hover/фокус ніколи не робить текст сірішим.
 */
export function Input({
  value,
  placeholder,
  focused = false,
  mono = false,
  onChange,
  style,
}: {
  value?: string;
  placeholder?: string;
  /** Візуальний стан фокусу для превʼю варіантів. */
  focused?: boolean;
  /** true для чисел, кодів, цін. */
  mono?: boolean;
  onChange?: (next: string) => void;
  style?: CSSProperties;
}) {
  const { accent } = useNoir();

  return (
    <input
      value={value}
      placeholder={placeholder}
      onChange={(e) => onChange?.(e.target.value)}
      style={{
        minHeight: 48,
        width: '100%',
        boxSizing: 'border-box',
        padding: '14px 16px',
        borderRadius: radius.md,
        background: surface.s2,
        border: `1px solid ${focused ? accent : line.outline}`,
        boxShadow: focused ? fx.accentGlow(accent) : 'none',
        color: text.t1,
        fontSize: 13.5,
        fontFamily: mono ? font.mono : font.body,
        fontFeatureSettings: mono ? '"tnum"' : undefined,
        outline: 'none',
        ...style,
      }}
    />
  );
}

Input.displayName = 'Input';
