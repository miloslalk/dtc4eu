import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAdminAuth } from './auth';
import { apiErrorMessage } from '../lib/api';
import { Button, FieldLabel, TextInput } from './components/ui';

export function LoginPage() {
  const { t } = useTranslation();
  const { login } = useAdminAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-neutral-50 p-4">
      <div className="w-full max-w-sm rounded-2xl border border-neutral-200 bg-white p-8 shadow-lg shadow-blue-600/5">
        <img src="/logo.png" alt="DTC4EU" className="mx-auto mb-4 size-20 object-contain" />
        <h1 className="text-center text-xl font-extrabold text-blue-600">{t('admin.title')}</h1>
        <form onSubmit={submit} className="mt-6 space-y-4">
          <FieldLabel label={t('admin.username')}>
            <TextInput autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
          </FieldLabel>
          <FieldLabel label={t('admin.password')}>
            <TextInput type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </FieldLabel>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
          <Button type="submit" loading={busy} className="w-full">{t('admin.login')}</Button>
        </form>
      </div>
      <img src="/eu-cofunded.png" alt="Co-funded by the European Union" className="h-10 w-auto" />
    </div>
  );
}
