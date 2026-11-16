import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useNav, useSiteSettings } from '../lib/publicApi';
import { settingText } from '../lib/types';
import { Container } from './components/Container';

/**
 * Public marketing shell: glassy sticky header with the 3px gold rule
 * (brand-palette chrome), CMS-driven nav, EU co-funding footer.
 */
export function SiteShell() {
  const { t } = useTranslation();
  const nav = useNav();
  const settings = useSiteSettings();
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const items = nav.data ?? [];
  const toPath = (slug: string) => (slug === 'home' ? '/' : `/${slug}`);
  const platformUrl = settingText(settings.data, 'platform', 'url');
  const platformLabel = settingText(settings.data, 'platform', 'label') || 'Platform';

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `relative rounded-lg px-3 py-2 text-sm font-bold transition-colors
     after:absolute after:inset-x-3 after:-bottom-0.5 after:h-0.5 after:origin-left after:rounded-full
     after:bg-gold-500 after:transition-transform after:duration-300 ${
       isActive
         ? 'text-blue-700 after:scale-x-100'
         : 'text-neutral-600 after:scale-x-0 hover:text-blue-700 hover:after:scale-x-100'
     }`;

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:bg-white focus:px-3 focus:py-2">
        {t('site.skipToContent')}
      </a>

      <header
        className={`sticky top-0 z-40 border-b-[3px] border-gold-500 bg-white/80 backdrop-blur-xl transition-shadow duration-300 ${
          scrolled ? 'shadow-lg shadow-blue-600/5' : ''
        }`}
      >
        <Container className="flex items-center gap-4 py-2">
          <Link to="/" className="group flex items-center" onClick={() => setMenuOpen(false)}>
            <img src="/logo.png" alt="DTC4EU" className="size-20 object-contain transition-transform duration-300 group-hover:scale-105" />
          </Link>

          <nav className="ml-auto hidden items-center gap-1 lg:flex" aria-label="Main">
            {items.map((item) => (
              <NavLink key={item.slug} to={toPath(item.slug)} end={item.slug === 'home'} className={navLinkClass}>
                {item.label}
              </NavLink>
            ))}
            {platformUrl && (
              <a
                href={platformUrl} target="_blank" rel="noreferrer"
                className="group ml-3 inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-blue-600 to-azure-600 px-5 py-2.5 text-sm font-bold text-white
                  shadow-md shadow-blue-600/20 transition-all hover:shadow-lg hover:shadow-blue-600/25 hover:brightness-110
                  focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500"
              >
                {platformLabel}
                <span aria-hidden className="text-xs transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5">↗</span>
              </a>
            )}
          </nav>

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-label={menuOpen ? t('site.close') : t('site.menu')}
            aria-expanded={menuOpen}
            className="ml-auto rounded-lg border border-neutral-200 px-3 py-2 text-neutral-700 hover:bg-neutral-100 lg:hidden"
          >
            <span aria-hidden className="block text-base leading-none">{menuOpen ? '×' : '☰'}</span>
          </button>
        </Container>

        {menuOpen && (
          <nav className="border-t border-neutral-200 bg-white/95 backdrop-blur-xl lg:hidden" aria-label="Main">
            <Container className="flex flex-col py-2">
              {items.map((item) => (
                <NavLink
                  key={item.slug}
                  to={toPath(item.slug)}
                  end={item.slug === 'home'}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-2.5 text-sm font-bold ${
                      isActive ? 'bg-blue-50 text-blue-700' : 'text-neutral-700 hover:bg-neutral-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
              {platformUrl && (
                <a
                  href={platformUrl} target="_blank" rel="noreferrer"
                  onClick={() => setMenuOpen(false)}
                  className="mt-2 rounded-xl bg-gradient-to-r from-blue-600 to-azure-600 px-4 py-3 text-center text-sm font-bold text-white shadow-md shadow-blue-600/20"
                >
                  {platformLabel} ↗
                </a>
              )}
            </Container>
          </nav>
        )}
      </header>

      <main id="main" className="flex-1">
        <Outlet />
      </main>

      <footer className="relative mt-20 overflow-hidden bg-gradient-to-br from-blue-700 via-blue-800 to-blue-900 text-blue-50">
        <div aria-hidden className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-gold-500 via-gold-400 to-azure-500" />
        <div aria-hidden className="absolute -right-24 -top-24 size-80 rounded-full bg-azure-500/10 blur-3xl" />
        <div aria-hidden className="absolute -bottom-32 left-1/4 size-72 rounded-full bg-gold-400/10 blur-3xl" />
        <Container className="relative grid gap-10 py-14 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <div className="flex items-center gap-3">
              <img src="/logo.png" alt="" className="size-11 rounded-xl bg-white object-contain p-1 shadow-lg" />
              <span className="text-lg font-extrabold tracking-wide text-white">DTC4EU</span>
            </div>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-blue-100">
              {settingText(settings.data, 'footer', 'tagline')}
            </p>
          </div>
          <div>
            <p className="mb-3 text-xs font-extrabold uppercase tracking-[2px] text-gold-400">Pages</p>
            <ul className="space-y-2">
              {items.map((item) => (
                <li key={item.slug}>
                  <Link to={toPath(item.slug)}
                    className="text-sm text-blue-100 transition-colors hover:text-white hover:underline hover:underline-offset-4">
                    {item.label}
                  </Link>
                </li>
              ))}
              {platformUrl && (
                <li>
                  <a href={platformUrl} target="_blank" rel="noreferrer"
                    className="text-sm font-bold text-gold-400 transition-colors hover:text-gold-300 hover:underline hover:underline-offset-4">
                    {platformLabel} ↗
                  </a>
                </li>
              )}
            </ul>
          </div>
          <div className="sm:col-span-2 lg:col-span-1">
            {/* EU emblem must sit on a light background when the surround is coloured */}
            <span className="inline-block rounded-xl bg-white p-2.5 shadow-lg">
              <img
                src="/eu-cofunded.png"
                alt={settingText(settings.data, 'eu', 'fundingStatement') || 'Co-funded by the European Union'}
                className="h-12 w-auto"
              />
            </span>
            <p className="mt-4 text-xs leading-relaxed text-blue-200/90">
              {settingText(settings.data, 'eu', 'disclaimer')}
            </p>
          </div>
        </Container>
        <div className="relative border-t border-white/10">
          <Container className="flex flex-wrap items-center justify-between gap-2 py-4 text-xs text-blue-200">
            <span>© {new Date().getFullYear()} {settingText(settings.data, 'footer', 'copyright')}</span>
            <Link to="/admin" className="transition-colors hover:text-white hover:underline">{t('site.adminLink')}</Link>
          </Container>
        </div>
      </footer>
    </div>
  );
}
