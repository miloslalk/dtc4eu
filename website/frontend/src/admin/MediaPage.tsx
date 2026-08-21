import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMedia, useMediaMutations } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, EmptyState, TextInput } from './components/ui';
import { Spinner } from '../site/components/Spinner';

export function MediaPage() {
  const { t } = useTranslation();
  const media = useMedia();
  const { upload, updateAlt, remove } = useMediaMutations();
  const fileInput = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [altDrafts, setAltDrafts] = useState<Record<number, string>>({});

  const handleUpload = async (files: FileList | null) => {
    if (!files) return;
    setError(null);
    try {
      for (const file of Array.from(files)) {
        await upload.mutateAsync({ file });
      }
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  if (media.isLoading) return <Spinner />;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.media')}</h1>
        <Button loading={upload.isPending} onClick={() => fileInput.current?.click()}>+ {t('admin.common.upload')}</Button>
        <input ref={fileInput} type="file" hidden multiple accept="image/*,application/pdf"
          onChange={(e) => handleUpload(e.target.files)} />
      </div>
      {error && <p className="mb-4 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}

      {(media.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(media.data ?? []).map((asset) => (
            <figure key={asset.id} className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
              {asset.contentType.startsWith('image/') ? (
                <img src={asset.url} alt={asset.altText ?? ''} className="aspect-[4/3] w-full object-cover" />
              ) : (
                <span className="flex aspect-[4/3] items-center justify-center bg-neutral-100 text-4xl">📄</span>
              )}
              <figcaption className="space-y-2 p-3">
                <p className="truncate text-xs font-semibold text-neutral-700" title={asset.filename}>{asset.filename}</p>
                <p className="text-[11px] text-neutral-400">{Math.round(asset.sizeBytes / 1024)} KB · {asset.contentType}</p>
                <div className="flex gap-2">
                  <TextInput placeholder="Alt text" value={altDrafts[asset.id] ?? asset.altText ?? ''}
                    onChange={(e) => setAltDrafts({ ...altDrafts, [asset.id]: e.target.value })} />
                  <Button variant="ghost" loading={updateAlt.isPending}
                    onClick={() => updateAlt.mutate({ id: asset.id, altText: altDrafts[asset.id] ?? asset.altText ?? '' })}>
                    {t('admin.common.save')}
                  </Button>
                </div>
                <Button variant="danger" className="w-full"
                  onClick={async () => {
                    if (!window.confirm(t('admin.common.confirmDelete'))) return;
                    try {
                      await remove.mutateAsync(asset.id);
                    } catch (e) {
                      setError(apiErrorMessage(e));
                    }
                  }}>
                  {t('admin.common.delete')}
                </Button>
              </figcaption>
            </figure>
          ))}
        </div>
      )}
    </div>
  );
}
