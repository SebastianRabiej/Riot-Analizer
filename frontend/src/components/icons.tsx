import { useState } from 'react';
import {
  championIconUrl,
  itemIconUrl,
  profileIconUrl,
  spellIconUrl,
} from '../ddragon';
import { useDdragonVersion } from '../hooks';

/** Champion icon that gracefully falls back to the champion name on error. */
export function ChampionIcon({
  championName,
  size = 40,
  rounded = false,
  title,
}: {
  championName: string;
  size?: number;
  rounded?: boolean;
  title?: string;
}) {
  const version = useDdragonVersion();
  const [failed, setFailed] = useState(false);
  const dim = { width: size, height: size };

  if (failed || !championName) {
    return (
      <div
        className={`icon-fallback${rounded ? ' rounded' : ''}`}
        style={dim}
        title={title ?? championName}
      >
        {championName ? championName.slice(0, 4) : '?'}
      </div>
    );
  }

  return (
    <img
      className={`champ-icon${rounded ? ' rounded' : ''}`}
      style={dim}
      src={championIconUrl(championName, version)}
      alt={championName}
      title={title ?? championName}
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}

/** Profile icon with a neutral fallback. */
export function ProfileIcon({ iconId }: { iconId: number }) {
  const version = useDdragonVersion();
  const [failed, setFailed] = useState(false);
  if (failed) {
    return <div className="profile-icon icon-fallback">no icon</div>;
  }
  return (
    <img
      className="profile-icon"
      src={profileIconUrl(iconId, version)}
      alt="Profile icon"
      onError={() => setFailed(true)}
    />
  );
}

/** Single item slot; empty (0) slots render as a blank box. */
export function ItemSlot({ itemId }: { itemId: number }) {
  const version = useDdragonVersion();
  const [failed, setFailed] = useState(false);
  const url = itemIconUrl(itemId, version);
  return (
    <div className="item-slot" title={itemId ? `Item ${itemId}` : 'Empty'}>
      {url && !failed && (
        <img
          src={url}
          alt={`Item ${itemId}`}
          loading="lazy"
          onError={() => setFailed(true)}
        />
      )}
    </div>
  );
}

export function ItemsRow({ items }: { items: number[] }) {
  // Contract guarantees length 7 (items0..6). Pad defensively.
  const slots = Array.from({ length: 7 }, (_, i) => items[i] ?? 0);
  return (
    <div className="items-row">
      {slots.map((id, i) => (
        <ItemSlot key={i} itemId={id} />
      ))}
    </div>
  );
}

/** Summoner-spell icon; falls back to showing the numeric id if unmapped. */
export function SpellSlot({ spellId }: { spellId: number }) {
  const version = useDdragonVersion();
  const [failed, setFailed] = useState(false);
  const url = spellIconUrl(spellId, version);
  if (url && !failed) {
    return (
      <div className="spell-slot" title={`Spell ${spellId}`}>
        <img
          src={url}
          alt={`Spell ${spellId}`}
          loading="lazy"
          onError={() => setFailed(true)}
        />
      </div>
    );
  }
  return (
    <div className="spell-slot" title={`Spell ${spellId}`}>
      {spellId || '?'}
    </div>
  );
}

export function Spells({ spell1, spell2 }: { spell1: number; spell2: number }) {
  return (
    <div className="spells-col match-spells">
      <SpellSlot spellId={spell1} />
      <SpellSlot spellId={spell2} />
    </div>
  );
}
