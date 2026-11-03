import { useNoir } from './NoirProvider';
import { fx, line, radius, surface, text } from './tokens';

/**
 * Світч `.sw` — 48×28 пігулка.
 *
 * on: фон акценту + світіння 18px×38%, повзунок ink.
 * off: surface-4 з inset-обводкою outline.
 * Для on/off використовується ТІЛЬКИ світч; чекбокс — лише для множинного вибору.
 */
export function Switch({ checked = false, onChange }: { checked?: boolean; onChange?: (next: boolean) => void }) {
  const { accent } = useNoir();

  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange?.(!checked)}
      style={{
        width: 48,
        height: 28,
        flex: '0 0 auto',
        padding: '0 3px',
        borderRadius: radius.pill,
        border: checked ? '1px solid transparent' : `1px solid ${line.outline}`,
        background: checked ? accent : surface.s4,
        boxShadow: checked ? fx.accentGlow(accent) : 'none',
        display: 'flex',
        alignItems: 'center',
        justifyContent: checked ? 'flex-end' : 'flex-start',
        cursor: 'pointer',
      }}
    >
      <span
        style={{
          width: 22,
          height: 22,
          borderRadius: radius.pill,
          background: checked ? text.ink : text.off,
        }}
      />
    </button>
  );
}

Switch.displayName = 'Switch';
