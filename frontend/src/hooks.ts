import { useEffect, useState } from 'react';
import {
  getChampionNames,
  getVersion,
  preloadSpellMap,
  versionOrFallback,
  getItemData,
  type ChampionEntry,
  type ItemInfo,
} from './ddragon';

/** Resolve the current Data Dragon version, re-rendering once it loads. */
export function useDdragonVersion(): string {
  const [version, setVersion] = useState<string>(versionOrFallback());

  useEffect(() => {
    let active = true;
    preloadSpellMap();
    getVersion().then((v) => {
      if (active) setVersion(v);
    });
    return () => {
      active = false;
    };
  }, []);

  return version;
}

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

/** Load the champion list once for search autocomplete. */
export function useChampionNames(): ChampionEntry[] {
  const [champions, setChampions] = useState<ChampionEntry[]>([]);

  useEffect(() => {
    let active = true;
    getChampionNames().then((list) => {
      if (active) setChampions(list);
    });
    return () => {
      active = false;
    };
  }, []);

  return champions;
}

/**
 * Generic data-loading hook. Re-runs whenever `deps` change. The fetcher is
 * called on each run; results from stale runs are ignored.
 */
export function useAsync<T>(
  fetcher: () => Promise<T>,
  deps: unknown[],
): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({
    data: null,
    loading: true,
    error: null,
  });

  useEffect(() => {
    let active = true;
    setState((s) => ({ data: s.data, loading: true, error: null }));
    fetcher()
      .then((d) => {
        if (active) setState({ data: d, loading: false, error: null });
      })
      .catch((e: unknown) => {
        if (active)
          setState({
            data: null,
            loading: false,
            error: e instanceof Error ? e.message : 'Something went wrong',
          });
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}

/** Load the Data Dragon item metadata map once (id -> {name, gold}). */
export function useItemData(): Record<number, ItemInfo> {
  const [items, setItems] = useState<Record<number, ItemInfo>>({});
  useEffect(() => {
    let active = true;
    getItemData().then((map) => {
      if (active) setItems(map);
    });
    return () => {
      active = false;
    };
  }, []);
  return items;
}
