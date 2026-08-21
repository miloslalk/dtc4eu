import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSaveSetting, useSettings } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, FieldLabel, TextArea, TextInput } from './components/ui';
import { Spinner } from '../site/components/Spinner';

/** Structured editors over the site_settings keys the public site reads. */
export function SettingsPage() {
  const { t } = useTranslation();
  const settings = useSettings();
  const save = useSaveSetting();
  const [draft, setDraft] = useState<Record<string, Record<string, string>>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (settings.data) {
      const toStrings = (value: Record<string, unknown> | undefined): Record<string, string> =>
        Object.fromEntries(Object.entries(value ?? {}).map(([k, v]) => [k, typeof v === 'string' ? v : '']));
      setDraft({
        footer: toStrings(settings.data.footer),
        eu: toStrings(settings.data.eu),
        contact: toStrings(settings.data.contact),
        platform: toStrings(settings.data.platform),
      });
    }
  }, [settings.data]);

  const set = (key: string, field: string, value: string) =>
    setDraft((prev) => ({ ...prev, [key]: { ...prev[key], [field]: value } }));

  const saveAll = async () => {
    setError(null);
    setMessage(null);
    try {
      for (const key of Object.keys(draft)) {
        await save.mutateAsync({ key, value: draft[key] });
      }
      setMessage(t('admin.common.saved'));
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  if (settings.isLoading) return <Spinner />;

  return (
    <div className="max-w-2xl">
      <div className="mb-5 flex items-center gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.settings')}</h1>
        {message && <span className="text-xs font-semibold text-success">{message}</span>}
        <Button className="ml-auto" onClick={saveAll} loading={save.isPending}>{t('admin.common.save')}</Button>
      </div>
      {error && <p className="mb-4 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}

      <div className="space-y-6">
        <section className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-bold text-blue-700">Footer</h2>
          <FieldLabel label="Tagline">
            <TextInput value={draft.footer?.tagline ?? ''} onChange={(e) => set('footer', 'tagline', e.target.value)} />
          </FieldLabel>
          <FieldLabel label="Copyright line">
            <TextInput value={draft.footer?.copyright ?? ''} onChange={(e) => set('footer', 'copyright', e.target.value)} />
          </FieldLabel>
        </section>

        <section className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-bold text-blue-700">EU co-funding</h2>
          <FieldLabel label="Funding statement">
            <TextInput value={draft.eu?.fundingStatement ?? ''} onChange={(e) => set('eu', 'fundingStatement', e.target.value)} />
          </FieldLabel>
          <FieldLabel label="Disclaimer">
            <TextArea rows={4} value={draft.eu?.disclaimer ?? ''} onChange={(e) => set('eu', 'disclaimer', e.target.value)} />
          </FieldLabel>
        </section>

        <section className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-bold text-blue-700">Platform link</h2>
          <FieldLabel label="Button label">
            <TextInput value={draft.platform?.label ?? ''} onChange={(e) => set('platform', 'label', e.target.value)} />
          </FieldLabel>
          <FieldLabel label="Platform URL">
            <TextInput value={draft.platform?.url ?? ''} onChange={(e) => set('platform', 'url', e.target.value)} />
          </FieldLabel>
        </section>

        <section className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-bold text-blue-700">Contact</h2>
          <FieldLabel label="Contact email">
            <TextInput value={draft.contact?.email ?? ''} onChange={(e) => set('contact', 'email', e.target.value)} />
          </FieldLabel>
        </section>
      </div>
    </div>
  );
}
