import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequireRole } from '../lib/auth';
import { CycleProvider } from '../lib/cycle';
import { AppShell } from './AppShell';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { UsersPage } from '../features/admin/UsersPage';
import { CyclesPage } from '../features/admin/CyclesPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <CycleProvider>
              <AppShell />
            </CycleProvider>
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route
          path="admin/users"
          element={
            <RequireRole roles={['ADMIN', 'SUPER_ADMIN']}>
              <UsersPage />
            </RequireRole>
          }
        />
        <Route
          path="admin/cycles"
          element={
            <RequireRole roles={['ADMIN', 'SUPER_ADMIN']}>
              <CyclesPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
