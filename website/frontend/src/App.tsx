import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { SiteShell } from './site/SiteShell';
import { PageView } from './site/PageView';
import { NewsDetailPage } from './site/NewsDetailPage';
import { NotFound } from './site/NotFound';
import { Spinner } from './site/components/Spinner';

// The CMS is code-split so the public bundle stays lean.
const AdminApp = lazy(() => import('./admin/AdminApp'));

export function App() {
  return (
    <Routes>
      <Route
        path="/admin/*"
        element={
          <Suspense fallback={<div className="p-10"><Spinner /></div>}>
            <AdminApp />
          </Suspense>
        }
      />
      <Route element={<SiteShell />}>
        <Route index element={<PageView slug="home" />} />
        <Route path="/news/:slug" element={<NewsDetailPage />} />
        <Route path="/home" element={<Navigate to="/" replace />} />
        <Route path="/:slug" element={<PageView />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
