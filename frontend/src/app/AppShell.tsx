import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../lib/auth';
import { useCycle } from '../lib/cycle';
import { LANGUAGES } from '../lib/i18n';
import type { RoleCode } from '../lib/types';

interface NavItem {
  to: string;
  labelKey: string;
  roles?: RoleCode[]; // undefined = visible to everyone
  end?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', labelKey: 'nav.dashboard', end: true },
  { to: '/facilities', labelKey: 'nav.facilities', roles: ['SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'COORDINATOR', 'INTERNAL_FACILITATOR'] },
  { to: '/ternas', labelKey: 'nav.ternas', roles: ['SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'COORDINATOR'] },
  { to: '/questionnaires', labelKey: 'nav.questionnaires', roles: ['SUPER_ADMIN', 'ADMIN'] },
  { to: '/responses', labelKey: 'nav.responses', roles: ['SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'COORDINATOR', 'INTERNAL_FACILITATOR', 'RESPONDENT'] },
  { to: '/admin/users', labelKey: 'nav.users', roles: ['SUPER_ADMIN', 'ADMIN'] },
  { to: '/admin/cycles', labelKey: 'nav.cycles', roles: ['SUPER_ADMIN', 'ADMIN'] },
];

export function AppShell() {
  const { t, i18n } = useTranslation();
  const { user, logout, hasAnyRole } = useAuth();
  const { cycles, selected, select, readOnly } = useCycle();
  const navigate = useNavigate();

  const visibleItems = NAV_ITEMS.filter((item) => !item.roles || hasAnyRole(...item.roles));

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-screen">
      {/* Sidebar — DTC Blue (--bg-sidebar) */}
      <aside className="flex w-60 shrink-0 flex-col bg-blue-600 text-white">
        <div className="flex items-center gap-3 px-4 py-4">
          <img src="/logo.png" alt="DTC4EU" className="size-10 rounded-lg bg-white object-contain p-0.5" />
          <div className="leading-tight">
            <div className="text-base font-extrabold tracking-wide">DTC4EU</div>
            <div className="text-[11px] text-blue-100">Erasmus+ KA220</div>
          </div>
        </div>
        <nav className="mt-2 flex-1 space-y-1 px-2">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive ? 'bg-blue-700 text-gold-400' : 'text-blue-50 hover:bg-blue-700/60'
                }`
              }
            >
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>
        <div className="px-4 py-3 text-[11px] text-blue-200">{t('app.tagline')}</div>
      </aside>

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-neutral-200 bg-white px-6 py-3">
          {/* Cycle selector chip — Inclusion Gold (--cycle-chip-bg) */}
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500">{t('cycle.label')}</span>
            <select
              value={selected?.id ?? ''}
              onChange={(e) => select(Number(e.target.value))}
              className="rounded-full bg-gold-500 px-3 py-1 text-sm font-bold text-[#3A2C00]
                focus:outline-2 focus:outline-offset-2 focus:outline-gold-600"
            >
              {selected === null && <option value="">—</option>}
              {cycles.map((cycle) => (
                <option key={cycle.id} value={cycle.id}>
                  {cycle.code}
                </option>
              ))}
            </select>
            {readOnly && selected && (
              <span className="text-xs font-medium text-warning">
                {t('cycle.readOnly', { status: t(`admin.statuses.${selected.status}`) })}
              </span>
            )}
          </div>

          <div className="ml-auto flex items-center gap-4">
            <select
              value={i18n.language}
              onChange={(e) => i18n.changeLanguage(e.target.value)}
              className="rounded-lg border border-neutral-200 px-2 py-1 text-sm text-neutral-600"
              aria-label="Language"
            >
              {LANGUAGES.map((lang) => (
                <option key={lang.code} value={lang.code}>
                  {lang.code.toUpperCase()}
                </option>
              ))}
            </select>
            <div className="text-right leading-tight">
              <div className="text-sm font-semibold text-neutral-800">{user?.fullName}</div>
              <div className="text-xs text-neutral-500">{user?.roles.map((r) => t(`admin.roleNames.${r}`)).join(', ')}</div>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm font-medium text-neutral-600 hover:bg-neutral-100"
            >
              {t('auth.logout')}
            </button>
          </div>
        </header>

        <main className="flex-1 px-6 py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
