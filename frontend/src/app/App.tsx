import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequireRole } from '../lib/auth';
import { CycleProvider } from '../lib/cycle';
import { AppShell } from './AppShell';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { UsersPage } from '../features/admin/UsersPage';
import { CyclesPage } from '../features/admin/CyclesPage';
import { FacilitiesPage } from '../features/facilities/FacilitiesPage';
import { FacilityDetailPage } from '../features/facilities/FacilityDetailPage';
import { TernasPage } from '../features/ternas/TernasPage';
import { TernaBuilderPage } from '../features/ternas/TernaBuilderPage';
import { QuestionnairesPage } from '../features/questionnaires/QuestionnairesPage';
import { QuestionnaireDetailPage } from '../features/questionnaires/QuestionnaireDetailPage';
import { ResponsesPage } from '../features/responses/ResponsesPage';
import { ResponseFillPage } from '../features/responses/ResponseFillPage';

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
        <Route path="facilities" element={<FacilitiesPage />} />
        <Route path="facilities/:id" element={<FacilityDetailPage />} />
        <Route path="ternas" element={<TernasPage />} />
        <Route path="ternas/:id" element={<TernaBuilderPage />} />
        <Route
          path="questionnaires"
          element={
            <RequireRole roles={['ADMIN', 'SUPER_ADMIN']}>
              <QuestionnairesPage />
            </RequireRole>
          }
        />
        <Route
          path="questionnaires/:id"
          element={
            <RequireRole roles={['ADMIN', 'SUPER_ADMIN']}>
              <QuestionnaireDetailPage />
            </RequireRole>
          }
        />
        <Route path="responses" element={<ResponsesPage />} />
        <Route path="responses/:id" element={<ResponseFillPage />} />
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
