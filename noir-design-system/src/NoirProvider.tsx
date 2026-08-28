import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { skin, status, type ModeName, type SkinName } from './tokens';

type NoirValue = {
  /** Обраний скін акценту. */
  skin: SkinName;
  /** Резольвнутий колір акценту. */
  accent: string;
  /** Режим раунду — керує свіченням екрана питання. */
  mode: ModeName;
  /** Колір режиму: arena = акцент скіна, easy = success, hard = danger. */
  glow: string;
};

const DEFAULT: NoirValue = {
  skin: 'azure',
  accent: skin.azure,
  mode: 'arena',
  glow: skin.azure,
};

const NoirContext = createContext<NoirValue>(DEFAULT);

/** Компоненти працюють і без провайдера — тоді це azure / arena. */
export function useNoir(): NoirValue {
  return useContext(NoirContext);
}

/**
 * Єдина точка входу дизайн-системи.
 * Один скелет + три скіни: зміна `skin` перефарбовує все дерево.
 */
export function NoirProvider({
  skin: skinName = 'azure',
  mode = 'arena',
  children,
}: {
  skin?: SkinName;
  mode?: ModeName;
  children: ReactNode;
}) {
  const value = useMemo<NoirValue>(() => {
    const accent = skin[skinName];
    const glow = mode === 'easy' ? status.success : mode === 'hard' ? status.danger : accent;
    return { skin: skinName, accent, mode, glow };
  }, [skinName, mode]);

  return <NoirContext.Provider value={value}>{children}</NoirContext.Provider>;
}

NoirProvider.displayName = 'NoirProvider';
