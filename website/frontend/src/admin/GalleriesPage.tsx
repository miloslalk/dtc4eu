import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAdminGalleries, useGalleryMutations, type AdminGallery } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, EmptyState, FieldLabel, Modal, TextInput } from './components/ui';
import { MediaPickerModal } from './components/MediaPicker';
import { Spinner } from '../site/components/Spinner';

export function GalleriesPage() {
  const { t } = useTranslation();
  const galleries = useAdminGalleries();
  const { create, update, saveImages, remove } = useGalleryMutations();
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [pickerFor, setPickerFor] = useState<AdminGallery | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    setError(null);
    try {
      await create.mutateAsync({ slug: name, name, description: null });
      setCreateOpen(false);
      setName('');
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  const changeImages = (gallery: AdminGallery, images: { mediaId: number; caption: string | null }[]) =>
    saveImages.mutate({ id: gallery.id, images });

  if (galleries.isLoading) return <Spinner />;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.galleries')}</h1>
        <Button onClick={() => setCreateOpen(true)}>+ {t('admin.common.create')}</Button>
      </div>

      {(galleries.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="space-y-6">
          {(galleries.data ?? []).map((gallery) => (
            <section key={gallery.id} className="rounded-xl border border-neutral-200 bg-white shadow-sm">
              <header className="flex flex-wrap items-center gap-3 border-b border-neutral-200 bg-blue-50 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <EditableName gallery={gallery} onSave={(newName) =>
                    update.mutate({ id: gallery.id, body: { slug: gallery.slug, name: newName, description: gallery.description } })} />
                  <p className="font-mono text-xs text-neutral-500">{gallery.slug} · {gallery.images.length} images</p>
                </div>
                <Button variant="ghost" onClick={() => setPickerFor(gallery)}>+ {t('admin.common.add')}</Button>
                <Button variant="danger" onClick={() => {
                  if (window.confirm(t('admin.common.confirmDelete'))) remove.mutate(gallery.id);
                }}>
                  {t('admin.common.delete')}
                </Button>
              </header>
              {gallery.images.length > 0 && (
                <div className="grid grid-cols-3 gap-3 p-4 sm:grid-cols-4 lg:grid-cols-6">
                  {gallery.images.map((image, index) => (
                    <figure key={image.id} className="group relative overflow-hidden rounded-lg border border-neutral-200">
                      <img src={image.url} alt={image.altText} className="aspect-square w-full object-cover" />
                      <span className="absolute inset-x-0 bottom-0 flex justify-center gap-1 bg-neutral-900/60 py-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <MiniBtn label={t('admin.common.moveUp')} disabled={index === 0}
                          onClick={() => {
                            const next = gallery.images.map((i) => ({ mediaId: i.mediaId, caption: i.caption }));
                            const [moved] = next.splice(index, 1);
                            next.splice(index - 1, 0, moved);
                            changeImages(gallery, next);
                          }}>←</MiniBtn>
                        <MiniBtn label={t('admin.common.moveDown')} disabled={index === gallery.images.length - 1}
                          onClick={() => {
                            const next = gallery.images.map((i) => ({ mediaId: i.mediaId, caption: i.caption }));
                            const [moved] = next.splice(index, 1);
                            next.splice(index + 1, 0, moved);
                            changeImages(gallery, next);
                          }}>→</MiniBtn>
                        <MiniBtn label={t('admin.common.remove')}
                          onClick={() => changeImages(gallery,
                            gallery.images.filter((_, i) => i !== index).map((i) => ({ mediaId: i.mediaId, caption: i.caption })))}>
                          ✕
                        </MiniBtn>
                      </span>
                    </figure>
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}

      <Modal title={t('admin.common.create')} open={createOpen} onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCreateOpen(false)}>{t('admin.common.cancel')}</Button>
            <Button onClick={handleCreate} loading={create.isPending} disabled={!name}>{t('admin.common.create')}</Button>
          </>
        }>
        <FieldLabel label="Gallery name">
          <TextInput value={name} onChange={(e) => setName(e.target.value)} />
        </FieldLabel>
        {error && <p className="mt-3 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </Modal>

      <MediaPickerModal open={pickerFor !== null} onClose={() => setPickerFor(null)}
        onPick={(media) => {
          if (!pickerFor) return;
          changeImages(pickerFor, [
            ...pickerFor.images.map((i) => ({ mediaId: i.mediaId, caption: i.caption })),
            { mediaId: media.id, caption: null },
          ]);
        }} />
    </div>
  );
}

function MiniBtn({ children, label, onClick, disabled }: {
  children: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button type="button" title={label} aria-label={label} onClick={onClick} disabled={disabled}
      className="rounded px-1.5 text-xs font-bold text-white hover:bg-white/20 disabled:opacity-30">
      {children}
    </button>
  );
}

function EditableName({ gallery, onSave }: { gallery: AdminGallery; onSave: (name: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(gallery.name);
  if (!editing) {
    return (
      <button type="button" className="text-sm font-bold text-blue-700 hover:underline" onClick={() => setEditing(true)}>
        {gallery.name}
      </button>
    );
  }
  return (
    <span className="flex items-center gap-2">
      <TextInput value={value} onChange={(e) => setValue(e.target.value)} className="max-w-64" />
      <Button variant="ghost" onClick={() => { onSave(value); setEditing(false); }}>✓</Button>
    </span>
  );
}
