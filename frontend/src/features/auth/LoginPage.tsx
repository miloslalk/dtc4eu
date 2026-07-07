import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../lib/auth';
import { apiErrorMessage } from '../../lib/api';
import { Button } from '../../components/Button';
import { TextField } from '../../components/fields';

const schema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

type FormValues = z.infer<typeof schema>;

/** S-00 — sign-in screen. */
export function LoginPage() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      await login(values.username, values.password);
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/';
      navigate(from, { replace: true });
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 p-4">
      <div className="w-full max-w-sm rounded-2xl border border-neutral-200 bg-white p-8 shadow-lg shadow-blue-600/5">
        <img src="/logo.png" alt="DTC4EU" className="mx-auto mb-4 size-24 object-contain" />
        <h1 className="text-center text-xl font-extrabold text-blue-600">{t('app.title')}</h1>
        <p className="mb-6 mt-1 text-center text-xs text-neutral-500">{t('app.tagline')}</p>

        <form onSubmit={onSubmit} className="space-y-4">
          <TextField
            label={t('auth.username')}
            autoComplete="username"
            autoFocus
            error={errors.username && t('auth.usernameRequired')}
            {...register('username')}
          />
          <TextField
            label={t('auth.password')}
            type="password"
            autoComplete="current-password"
            error={errors.password && t('auth.passwordRequired')}
            {...register('password')}
          />
          {error && (
            <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>
          )}
          <Button type="submit" loading={isSubmitting} className="w-full justify-center">
            {t('auth.signIn')}
          </Button>
        </form>
      </div>
    </div>
  );
}
