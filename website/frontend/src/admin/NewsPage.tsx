import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAdminNews, useNewsMutations, type AdminNews, type SaveNews } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, EmptyState, FieldLabel, Modal, StatusChip, TextArea, TextInput } from './components/ui';
import { MediaField } from './components/MediaPicker';
import { RichTextEditor } from './components/RichTextEditor';
import { Spinner } from '../site/components/Spinner';

const EMPTY: SaveNews = { slug: '', title: '', excerpt: null, bodyHtml: '', coverMediaId: null };

export function NewsPage() {
  const { t } = useTranslation();
  const news = useAdminNews();
  const { create, update, setPublished, remove } = useNewsMutations();
  const [editing, setEditing] = useState<AdminNews | 'new' | null>(null);
  const [draft, setDraft] = useState<SaveNews>(EMPTY);
  const [error, setError] = useState<string | null>(null);

  const open = (post: AdminNews | 'new') => {
    setEditing(post);
    setError(null);
    setDraft(post === 'new' ? EMPTY : {
      slug: post.slug, title: post.title, excerpt: post.excerpt, bodyHtml: post.bodyHtml, coverMediaId: post.coverMediaId,
    });
  };

  const save = async () => {
    setError(null);
    try {
      if (editing === 'new') {
        await create.mutateAsync({ ...draft, slug: draft.slug || draft.title });
      } else if (editing) {
        await update.mutateAsync({ id: editing.id, body: draft });
      }
      setEditing(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  if (news.isLoading) return <Spinner />;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.news')}</h1>
        <Button onClick={() => open('new')}>+ {t('admin.common.create')}</Button>
      </div>

      {(news.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="space-y-3">
          {(news.data ?? []).map((post) => (
            <article key={post.id} className="flex flex-wrap items-center gap-3 rounded-xl border border-neutral-200 bg-white px-4 py-3 shadow-sm">
              <div className="min-w-0 flex-1">
                <h2 className="truncate text-sm font-bold text-blue-700">{post.title}</h2>
                <p className="truncate font-mono text-xs text-neutral-500">/news/{post.slug}</p>
              </div>
              <StatusChip published={post.status === 'PUBLISHED'} />
              <span className="flex gap-2">
                <Button variant="ghost" onClick={() => open(post)}>{t('admin.common.edit')}</Button>
                <Button variant="ghost" loading={setPublished.isPending}
                  onClick={() => setPublished.mutate({ id: post.id, publish: post.status !== 'PUBLISHED' })}>
                  {post.status === 'PUBLISHED' ? t('admin.common.unpublish') : t('admin.common.publish')}
                </Button>
                <Button variant="danger" onClick={() => {
                  if (window.confirm(t('admin.common.confirmDelete'))) remove.mutate(post.id);
                }}>
                  {t('admin.common.delete')}
                </Button>
              </span>
            </article>
          ))}
        </div>
      )}

      <Modal title={editing === 'new' ? t('admin.common.create') : t('admin.common.edit')} open={editing !== null}
        onClose={() => setEditing(null)} wide
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t('admin.common.cancel')}</Button>
            <Button onClick={save} loading={create.isPending || update.isPending} disabled={!draft.title}>
              {t('admin.common.save')}
            </Button>
          </>
        }>
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <FieldLabel label="Title">
              <TextInput value={draft.title} onChange={(e) => setDraft({ ...draft, title: e.target.value })} />
            </FieldLabel>
            <FieldLabel label="Slug">
              <TextInput value={draft.slug} onChange={(e) => setDraft({ ...draft, slug: e.target.value })} />
            </FieldLabel>
          </div>
          <FieldLabel label="Excerpt">
            <TextArea rows={2} value={draft.excerpt ?? ''}
              onChange={(e) => setDraft({ ...draft, excerpt: e.target.value || null })} />
          </FieldLabel>
          <MediaField label="Cover image" value={draft.coverMediaId}
            onChange={(coverMediaId) => setDraft({ ...draft, coverMediaId })} />
          <div>
            <span className="mb-1 block text-xs font-semibold text-neutral-600">Body</span>
            <RichTextEditor value={draft.bodyHtml} onChange={(bodyHtml) => setDraft({ ...draft, bodyHtml })} />
          </div>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}
