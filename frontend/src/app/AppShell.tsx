import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../lib/auth';
import { useCycle } from '../lib/cycle';
import { cycleLabel } from '../lib/types';
import { changeLanguage, LANGUAGES } from '../lib/i18n';
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
  { to: '/audits', labelKey: 'nav.audits', roles: ['SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'AUDIT_COORDINATOR', 'UPPER_COORDINATOR'] },
  { to: '/questionnaires', labelKey: 'nav.questionnaires', roles: ['SUPER_ADMIN', 'ADMIN'] },
  { to: '/my-questionnaires', labelKey: 'nav.myQuestionnaires', roles: ['RESPONDENT'] },
  { to: '/responses', labelKey: 'nav.responses', roles: ['SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'COORDINATOR', 'INTERNAL_FACILITATOR', 'RESPONDENT'] },
  { to: '/admin/users', labelKey: 'nav.users', roles: ['SUPER_ADMIN', 'ADMIN'] },
  { to: '/admin/cycles', labelKey: 'nav.cycles', roles: ['SUPER_ADMIN', 'ADMIN'] },
  { to: '/admin/rules', labelKey: 'nav.rules', roles: ['SUPER_ADMIN', 'ADMIN', 'UPPER_COORDINATOR'] },
];

export function AppShell() {
  const { t, i18n } = useTranslation();
  const { user, logout, hasAnyRole } = useAuth();
  const { cycles, selected, select, readOnly } = useCycle();
  const navigate = useNavigate();
  const [navOpen, setNavOpen] = useState(false);

  const visibleItems = NAV_ITEMS.filter((item) => !item.roles || hasAnyRole(...item.roles));

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-screen">
      {/* Mobile drawer backdrop */}
      {navOpen && (
        <div
          className="fixed inset-0 z-30 bg-neutral-900/40 lg:hidden"
          onClick={() => setNavOpen(false)}
          aria-hidden
        />
      )}

      {/* Sidebar — DTC Blue (--bg-sidebar); off-canvas drawer below lg */}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-60 shrink-0 flex-col bg-blue-600 text-white
          transition-transform duration-200 lg:static lg:translate-x-0 ${
            navOpen ? 'translate-x-0' : '-translate-x-full'
          }`}
      >
        <div className="flex items-center px-4 py-4">
          <img src="/logo.png" alt="DTC4EU" className="size-20 rounded-xl bg-white object-contain p-1" />
          <button
            type="button"
            onClick={() => setNavOpen(false)}
            aria-label="Close menu"
            className="ml-auto rounded-md px-2 py-1 text-lg text-blue-100 hover:bg-blue-700 lg:hidden"
          >
            ×
          </button>
        </div>
        <nav className="mt-2 flex-1 space-y-1 overflow-y-auto px-2">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setNavOpen(false)}
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
        <header className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-neutral-200 bg-white px-4 py-3 sm:px-6">
          <button
            type="button"
            onClick={() => setNavOpen(true)}
            aria-label="Open menu"
            className="rounded-lg border border-neutral-200 px-2.5 py-1.5 text-neutral-600 hover:bg-neutral-100 lg:hidden"
          >
            <span aria-hidden className="block text-base leading-none">☰</span>
          </button>

          {/* Cycle selector chip — Inclusion Gold (--cycle-chip-bg) */}
          <div className="flex items-center gap-2">
            <span className="hidden text-xs font-semibold uppercase tracking-wide text-neutral-500 sm:inline">
              {t('cycle.label')}
            </span>
            <select
              value={selected?.id ?? ''}
              onChange={(e) => select(Number(e.target.value))}
              className="rounded-full bg-gold-500 px-3 py-1 text-sm font-bold text-[#3A2C00]
                focus:outline-2 focus:outline-offset-2 focus:outline-gold-600"
            >
              {selected === null && <option value="">—</option>}
              {cycles.map((cycle) => (
                <option key={cycle.id} value={cycle.id}>
                  {cycleLabel(cycle)} · {t(`admin.statuses.${cycle.status}`)}
                </option>
              ))}
            </select>
            {readOnly && selected && (
              <span className="text-xs font-medium text-warning">
                {t('cycle.readOnly', { status: t(`admin.statuses.${selected.status}`) })}
              </span>
            )}
          </div>

          <div className="ml-auto flex items-center gap-2 sm:gap-4">
            <select
              value={i18n.language}
              onChange={(e) => void changeLanguage(e.target.value)}
              className="rounded-lg border border-neutral-200 px-2 py-1 text-sm text-neutral-600"
              aria-label="Language"
            >
              {LANGUAGES.map((lang) => (
                <option key={lang.code} value={lang.code} title={lang.label}>
                  {lang.code.toUpperCase()}
                </option>
              ))}
            </select>
            <div className="hidden text-right leading-tight md:block">
              <NavLink to="/account/password" className="text-sm font-semibold text-neutral-800 hover:underline"
                title={t('account.title')}>
                {user?.fullName}
              </NavLink>
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

        <main className="flex-1 px-4 py-4 sm:px-6 sm:py-6">
          {selected && (
            <p className="mb-4 rounded-lg border border-gold-500/40 bg-gold-50 px-3 py-2 text-xs text-neutral-700"
              role="status">
              {t('cycle.banner', { cycle: cycleLabel(selected), status: t(`admin.statuses.${selected.status}`) })}
            </p>
          )}
          <Outlet />
        </main>

        {/* EU co-funding acknowledgement — must stay visible on every screen */}
        <footer className="flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-neutral-200 bg-white px-4 py-2.5 sm:px-6">
          <img src="/eu-cofunded.png" alt="Co-funded by the European Union" className="h-9 w-auto" />
          <span className="text-[11px] text-neutral-500">{t('app.tagline')}</span>
        </footer>
      </div>
    </div>
  );
}
