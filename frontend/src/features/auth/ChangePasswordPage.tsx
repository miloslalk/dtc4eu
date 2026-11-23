import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../lib/auth';
import { api, apiErrorMessage } from '../../lib/api';
import { Button } from '../../components/Button';
import { TextField } from '../../components/fields';
import { PageHeader } from '../../components/PageHeader';

type FormValues = { currentPassword: string; newPassword: string; confirm: string };

/**
 * Own-password change (FIX-LIST X4). `forced`: a temporary or published
 * password — the API refuses everything else until it is changed, so this is
 * the only screen shown.
 */
export function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
  const { t } = useTranslation();
  const { passwordChanged, logout } = useAuth();
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const schema = z.object({
    currentPassword: z.string().min(1, t('auth.passwordRequired')),
    newPassword: z.string().min(10, t('account.minLength')),
    confirm: z.string(),
  }).refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: t('account.mismatch') });
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    setMessage(null);
    try {
      await api.post('/auth/me/password', { currentPassword: values.currentPassword, newPassword: values.newPassword });
      reset();
      setMessage({ ok: true, text: t('account.changed') });
      passwordChanged();
    } catch (e) {
      setMessage({ ok: false, text: apiErrorMessage(e) });
    }
  });

  const form = (
    <form onSubmit={onSubmit} className="space-y-4">
      <TextField label={t('account.current')} type="password" autoComplete="current-password"
        error={errors.currentPassword?.message} {...register('currentPassword')} />
      <TextField label={t('account.new')} type="password" autoComplete="new-password"
        error={errors.newPassword?.message} {...register('newPassword')} />
      <TextField label={t('account.confirm')} type="password" autoComplete="new-password"
        error={errors.confirm?.message} {...register('confirm')} />
      <p className="text-xs text-neutral-500">{t('account.rules')}</p>
      {message && (
        <p className={`rounded-lg px-3 py-2 text-sm font-medium ${message.ok
          ? 'bg-success-surface text-success' : 'bg-error-surface text-error'}`}>{message.text}</p>
      )}
      <Button type="submit" loading={isSubmitting} className="w-full justify-center">{t('account.submit')}</Button>
    </form>
  );

  if (!forced) {
    return (
      <div className="max-w-sm">
        <PageHeader title={t('account.title')} />
        <div className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">{form}</div>
      </div>
    );
  }
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-neutral-50 p-4">
      <div className="w-full max-w-sm rounded-2xl border border-neutral-200 bg-white p-8 shadow-lg shadow-blue-600/5">
        <h1 className="text-xl font-extrabold text-blue-600">{t('account.title')}</h1>
        <p className="mb-5 mt-1 text-sm text-neutral-600">{t('account.forcedIntro')}</p>
        {form}
        <button type="button" onClick={() => void logout()}
          className="mt-4 w-full text-center text-sm font-medium text-neutral-500 hover:underline">
          {t('auth.logout')}
        </button>
      </div>
    </div>
  );
}
