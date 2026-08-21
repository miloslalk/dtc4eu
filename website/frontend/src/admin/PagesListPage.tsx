import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { usePageMutations, usePages } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, EmptyState, FieldLabel, Modal, StatusChip, TextInput } from './components/ui';
import { Spinner } from '../site/components/Spinner';

export function PagesListPage() {
  const { t } = useTranslation();
  const pages = usePages();
  const { create } = usePageMutations();
  const [modalOpen, setModalOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [slug, setSlug] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    setError(null);
    try {
      const maxOrder = Math.max(0, ...(pages.data ?? []).map((p) => p.navOrder));
      await create.mutateAsync({
        slug: slug || title, title, showInNav: false, navLabel: null,
        navOrder: maxOrder + 1, seoTitle: null, seoDescription: null,
      });
      setModalOpen(false);
      setTitle('');
      setSlug('');
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  if (pages.isLoading) return <Spinner />;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.pages')}</h1>
        <Button onClick={() => setModalOpen(true)}>+ {t('admin.common.create')}</Button>
      </div>

      {(pages.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="bg-blue-600 text-white">
                  <th className="px-4 py-3 font-semibold">Title</th>
                  <th className="px-4 py-3 font-semibold">Slug</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">In nav</th>
                  <th className="px-4 py-3 font-semibold">Nav order</th>
                </tr>
              </thead>
              <tbody>
                {(pages.data ?? []).map((page, index) => (
                  <tr key={page.id} className={`border-t border-neutral-200 ${index % 2 === 1 ? 'bg-blue-50/60' : ''}`}>
                    <td className="px-4 py-2.5 font-semibold text-blue-700">
                      <Link to={`/admin/pages/${page.id}`} className="hover:underline">{page.title}</Link>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-xs text-neutral-600">/{page.slug}</td>
                    <td className="px-4 py-2.5"><StatusChip published={page.status === 'PUBLISHED'} /></td>
                    <td className="px-4 py-2.5">{page.showInNav ? '✓' : '—'}</td>
                    <td className="px-4 py-2.5">{page.navOrder}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal title={t('admin.common.create')} open={modalOpen} onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setModalOpen(false)}>{t('admin.common.cancel')}</Button>
            <Button onClick={handleCreate} loading={create.isPending} disabled={!title}>{t('admin.common.create')}</Button>
          </>
        }>
        <div className="space-y-4">
          <FieldLabel label="Title">
            <TextInput value={title} onChange={(e) => setTitle(e.target.value)} />
          </FieldLabel>
          <FieldLabel label="Slug (URL path — leave empty to derive from title)">
            <TextInput value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="my-page" />
          </FieldLabel>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}
