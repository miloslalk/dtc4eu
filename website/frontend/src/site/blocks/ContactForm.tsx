import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { publicApi, apiErrorMessage } from '../../lib/api';
import { Container } from '../components/Container';
import { SectionHeading } from '../components/SectionHeading';
import { str } from './helpers';

const schema = z.object({
  name: z.string().min(1),
  email: z.string().email(),
  subject: z.string().optional(),
  message: z.string().min(1),
  website: z.string().optional(), // honeypot — humans never see or fill it
});

type FormValues = z.infer<typeof schema>;

const inputClass =
  'w-full rounded-xl border border-neutral-300 bg-white px-3.5 py-2.5 text-sm text-neutral-900 transition-shadow ' +
  'focus:outline-2 focus:outline-offset-1 focus:outline-gold-500';

export function ContactForm({ payload }: { payload: Record<string, unknown> }) {
  const { t } = useTranslation();
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle');
  const [error, setError] = useState('');
  const heading = str(payload, 'heading');
  const intro = str(payload, 'intro');

  const { register, handleSubmit, reset, formState } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    setState('sending');
    try {
      await publicApi.post('/contact', values);
      setState('sent');
      reset();
    } catch (e) {
      setError(apiErrorMessage(e));
      setState('error');
    }
  });

  return (
    <section className="py-14">
      <Container className="max-w-2xl">
        {heading && <SectionHeading>{heading}</SectionHeading>}
        {intro && <p className="mb-6 text-sm text-neutral-600">{intro}</p>}

        <div className="rounded-3xl border border-neutral-200/70 bg-white p-6 shadow-xl shadow-blue-600/5 sm:p-8">
        {state === 'sent' ? (
          <p className="rounded-xl bg-success-surface px-5 py-4 text-sm font-semibold text-success">
            {t('contact.sent')}
          </p>
        ) : (
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="mb-1 block text-sm font-semibold text-neutral-700">{t('contact.name')} *</span>
                <input type="text" className={inputClass} {...register('name')} />
                {formState.errors.name && (
                  <span className="mt-1 block text-xs font-medium text-error">{t('contact.required')}</span>
                )}
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-semibold text-neutral-700">{t('contact.email')} *</span>
                <input type="email" className={inputClass} {...register('email')} />
                {formState.errors.email && (
                  <span className="mt-1 block text-xs font-medium text-error">{t('contact.invalidEmail')}</span>
                )}
              </label>
            </div>
            <label className="block">
              <span className="mb-1 block text-sm font-semibold text-neutral-700">{t('contact.subject')}</span>
              <input type="text" className={inputClass} {...register('subject')} />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-semibold text-neutral-700">{t('contact.message')} *</span>
              <textarea rows={6} className={inputClass} {...register('message')} />
              {formState.errors.message && (
                <span className="mt-1 block text-xs font-medium text-error">{t('contact.required')}</span>
              )}
            </label>
            <input type="text" tabIndex={-1} autoComplete="off" aria-hidden
              className="absolute left-[-9999px] top-auto h-px w-px" {...register('website')} />
            {state === 'error' && (
              <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">
                {error || t('contact.error')}
              </p>
            )}
            <button
              type="submit"
              disabled={state === 'sending'}
              className="group inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-azure-600 px-7 py-3.5 text-sm font-bold text-white
                shadow-lg shadow-blue-600/25 transition-all hover:shadow-xl hover:brightness-110 disabled:opacity-60
                focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500"
            >
              {state === 'sending' ? t('contact.sending') : t('contact.send')}
              <span aria-hidden className="transition-transform group-hover:translate-x-1">→</span>
            </button>
          </form>
        )}
        </div>
      </Container>
    </section>
  );
}
