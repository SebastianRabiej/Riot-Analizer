// Data Dragon asset helpers. Latest version resolved once and cached.

const VERSIONS_URL = 'https://ddragon.leagueoflegends.com/api/versions.json';
const FALLBACK_VERSION = '14.19.1';

let cachedVersion: string | null = null;
let versionPromise: Promise<string> | null = null;

/** Summoner-spell id -> internal key (e.g. 4 -> SummonerFlash). Loaded lazily. */
let spellMap: Record<number, string> | null = null;
let spellPromise: Promise<Record<number, string>> | null = null;

interface SummonerSpellData {
  data: Record<string, { key: string; id: string }>;
}

/** Resolve (and cache) the latest Data Dragon version. */
export async function getVersion(): Promise<string> {
  if (cachedVersion) return cachedVersion;
  if (versionPromise) return versionPromise;

  versionPromise = fetch(VERSIONS_URL)
    .then((r) => (r.ok ? r.json() : Promise.reject(new Error('versions fetch failed'))))
    .then((list: string[]) => {
      cachedVersion = list && list.length > 0 ? list[0] : FALLBACK_VERSION;
      return cachedVersion;
    })
    .catch(() => {
      cachedVersion = FALLBACK_VERSION;
      return cachedVersion;
    });

  return versionPromise;
}

/** Synchronous accessor: current known version, or the fallback until resolved. */
export function versionOrFallback(): string {
  return cachedVersion ?? FALLBACK_VERSION;
}

/**
 * Champion icon URL. Data Dragon keys are PascalCase with no spaces/punctuation
 * (e.g. "Kai'Sa" -> "Kaisa", "Wukong" is "MonkeyKing" but championName from the
 * API is already the DDragon id in most cases). We normalize common punctuation.
 */
export function championIconUrl(championName: string, version?: string): string {
  const v = version ?? versionOrFallback();
  const id = normalizeChampionId(championName);
  return `https://ddragon.leagueoflegends.com/cdn/${v}/img/champion/${id}.png`;
}

function normalizeChampionId(name: string): string {
  if (!name) return '';
  // DDragon ids strip spaces, apostrophes and periods; keep original casing.
  return name.replace(/[\s'.]/g, '');
}

/** Item icon URL. itemId 0 means empty slot (no icon). */
export function itemIconUrl(itemId: number, version?: string): string | null {
  if (!itemId || itemId <= 0) return null;
  const v = version ?? versionOrFallback();
  return `https://ddragon.leagueoflegends.com/cdn/${v}/img/item/${itemId}.png`;
}

/** Profile icon URL. */
export function profileIconUrl(iconId: number, version?: string): string {
  const v = version ?? versionOrFallback();
  return `https://ddragon.leagueoflegends.com/cdn/${v}/img/profileicon/${iconId}.png`;
}

/** Load and cache the summoner-spell id -> key map from DDragon summoner.json. */
async function loadSpellMap(): Promise<Record<number, string>> {
  if (spellMap) return spellMap;
  if (spellPromise) return spellPromise;

  spellPromise = (async () => {
    const v = await getVersion();
    try {
      const res = await fetch(
        `https://ddragon.leagueoflegends.com/cdn/${v}/data/en_US/summoner.json`,
      );
      if (!res.ok) throw new Error('summoner.json fetch failed');
      const json = (await res.json()) as SummonerSpellData;
      const map: Record<number, string> = {};
      for (const key of Object.keys(json.data)) {
        const entry = json.data[key];
        map[Number(entry.key)] = entry.id;
      }
      spellMap = map;
      return map;
    } catch {
      spellMap = {};
      return spellMap;
    }
  })();

  return spellPromise;
}

// Ensure the spell map begins loading early so icon URLs resolve.
export function preloadSpellMap(): void {
  void loadSpellMap();
}

/** A champion entry for the search datalist. `id` is the Data Dragon key that
 *  matches the API's stored championName; `name` is the friendly display name. */
export interface ChampionEntry {
  id: string;
  name: string;
}

interface ChampionData {
  data: Record<string, { id: string; name: string }>;
}

let championList: ChampionEntry[] | null = null;
let championPromise: Promise<ChampionEntry[]> | null = null;

/** Load and cache the champion list (id + display name) from DDragon. */
export async function getChampionNames(): Promise<ChampionEntry[]> {
  if (championList) return championList;
  if (championPromise) return championPromise;

  championPromise = (async () => {
    const v = await getVersion();
    try {
      const res = await fetch(
        `https://ddragon.leagueoflegends.com/cdn/${v}/data/en_US/champion.json`,
      );
      if (!res.ok) throw new Error('champion.json fetch failed');
      const json = (await res.json()) as ChampionData;
      const list = Object.values(json.data)
        .map((c) => ({ id: c.id, name: c.name }))
        .sort((a, b) => a.name.localeCompare(b.name));
      championList = list;
      return list;
    } catch {
      championList = [];
      return championList;
    }
  })();

  return championPromise;
}

/**
 * Summoner-spell icon URL by numeric spell id. Returns null if the map is not
 * yet loaded or the id is unknown (caller should fall back to showing the id).
 */
export function spellIconUrl(spellId: number, version?: string): string | null {
  if (!spellId || spellId <= 0) return null;
  const map = spellMap;
  if (!map) return null;
  const key = map[spellId];
  if (!key) return null;
  const v = version ?? versionOrFallback();
  return `https://ddragon.leagueoflegends.com/cdn/${v}/img/spell/${key}.png`;
}

/** Item id -> {name, gold} from Data Dragon item.json. Loaded once, cached. */
export interface ItemInfo {
  name: string;
  gold: number; // full recipe cost (gold.total)
  base: number; // combine cost only (gold.base)
}

let itemData: Record<number, ItemInfo> | null = null;
let itemPromise: Promise<Record<number, ItemInfo>> | null = null;

interface ItemJson {
  data: Record<string, { name: string; gold?: { total?: number; base?: number } }>;
}

export async function getItemData(): Promise<Record<number, ItemInfo>> {
  if (itemData) return itemData;
  if (itemPromise) return itemPromise;
  itemPromise = (async () => {
    const v = await getVersion();
    try {
      const res = await fetch(`https://ddragon.leagueoflegends.com/cdn/${v}/data/en_US/item.json`);
      if (!res.ok) throw new Error('item.json fetch failed');
      const json = (await res.json()) as ItemJson;
      const map: Record<number, ItemInfo> = {};
      for (const key of Object.keys(json.data)) {
        const it = json.data[key];
        const total = it.gold?.total ?? 0;
        map[Number(key)] = { name: it.name, gold: total, base: it.gold?.base ?? total };
      }
      itemData = map;
      return map;
    } catch {
      itemData = {};
      return itemData;
    }
  })();
  return itemPromise;
}
