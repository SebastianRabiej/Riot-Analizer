import { createContext, useContext } from 'react';
import type { PlayerDto } from './types';

export interface PlayerContextValue {
  player: PlayerDto;
  gameName: string;
  tagLine: string;
  /** Selected queue filter; undefined means "all queues". */
  queue: number | undefined;
  setQueue: (q: number | undefined) => void;
  refreshKey: number;
  refreshing: boolean;
  doRefresh: () => void;
}

export const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayerCtx(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error('usePlayerCtx must be used within a PlayerShell');
  return ctx;
}
