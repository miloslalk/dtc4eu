import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AdminAuthProvider, useAdminAuth } from './auth';
import { LoginPage } from './LoginPage';
import { PagesListPage } from './PagesListPage';
import { PageEditorPage } from './PageEditorPage';
import { NewsPage } from './NewsPage';
import { MediaPage } from './MediaPage';
import { GalleriesPage } from './GalleriesPage';
import { InboxPage } from './InboxPage';
import { SettingsPage } from './SettingsPage';
import { UsersPage } from './UsersPage';
import { Spinner } from '../site/components/Spinner';

export default function AdminApp() {
  return (
    <AdminAuthProvider>
      <AdminGate />
    </AdminAuthProvider>
  );
}

function AdminGate() {
  const { me, ready } = useAdminAuth();
  if (!ready) {
    return <Spinner />;
  }
  if (!me) {
    return <LoginPage />;
  }
  return <AdminShell />;
}

function AdminShell() {
  const { t } = useTranslation();
  const { me, logout } = useAdminAuth();

  const items = [
    { to: '/admin', key: 'pages', end: true },
    { to: '/admin/news', key: 'news' },
    { to: '/admin/media', key: 'media' },
    { to: '/admin/galleries', key: 'galleries' },
    { to: '/admin/inbox', key: 'inbox' },
    { to: '/admin/settings', key: 'settings' },
    ...(me?.role === 'ADMIN' ? [{ to: '/admin/users', key: 'users' }] : []),
  ];

  return (
    <div className="flex min-h-screen flex-col bg-neutral-50">
      <header className="border-b-[3px] border-gold-500 bg-blue-600 text-white">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
          <img src="/logo.png" alt="" className="size-9 rounded-lg bg-white object-contain p-0.5" />
          <span className="text-base font-extrabold tracking-wide">{t('admin.title')}</span>
          <a href="/" target="_blank" rel="noreferrer" className="text-xs text-blue-100 underline hover:text-white">
            dtc4eu.eu ↗
          </a>
          <span className="ml-auto flex items-center gap-3 text-sm">
            <span className="hidden text-blue-100 sm:inline">{me?.fullName}</span>
            <button type="button" onClick={logout}
              className="rounded-lg border border-blue-400 px-3 py-1.5 text-sm font-medium text-blue-50 hover:bg-blue-700">
              {t('admin.logout')}
            </button>
          </span>
        </div>
        <nav className="mx-auto flex w-full max-w-6xl gap-1 overflow-x-auto px-4 pb-2 sm:px-6" aria-label="CMS">
          {items.map((item) => (
            <NavLink key={item.key} to={item.to} end={item.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-semibold transition-colors ${
                  isActive ? 'bg-white text-blue-700' : 'text-blue-100 hover:bg-blue-700'
                }`
              }>
              {t(`admin.nav.${item.key}`)}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6">
        <Routes>
          <Route index element={<PagesListPage />} />
          <Route path="pages/:id" element={<PageEditorPage />} />
          <Route path="news" element={<NewsPage />} />
          <Route path="media" element={<MediaPage />} />
          <Route path="galleries" element={<GalleriesPage />} />
          <Route path="inbox" element={<InboxPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="*" element={<Navigate to="/admin" replace />} />
        </Routes>
      </main>

      <footer className="border-t border-neutral-200 bg-white">
        <div className="mx-auto flex w-full max-w-6xl items-center gap-3 px-4 py-3 sm:px-6">
          <img src="/eu-cofunded.png" alt="Co-funded by the European Union" className="h-9 w-auto" />
        </div>
      </footer>
    </div>
  );
}
