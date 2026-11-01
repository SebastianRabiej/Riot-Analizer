import { ROLES, ROLE_KEYS, type Role } from '../../lib/decision';

/** Full-width role cards (icon, name, what the role is about). */
export function RolePicker({ value, onChange }: { value: Role; onChange: (r: Role) => void }) {
  return (
    <div className="dm-roles" role="tablist" aria-label="Choose role">
      {ROLE_KEYS.map((r) => (
        <button key={r} type="button" role="tab" aria-selected={r === value} className={`dm-role ${r === value ? 'active' : ''}`} onClick={() => onChange(r)}>
          <span className="dm-role-ico">{ROLES[r].icon}</span>
          <span><strong>{ROLES[r].name}</strong><small>{ROLES[r].tag}</small></span>
        </button>
      ))}
    </div>
  );
}
