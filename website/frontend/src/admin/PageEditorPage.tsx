import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { Block, BlockType } from '../lib/types';
import { usePageDetail, usePageMutations } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { BLOCK_FORMS, emptyPayload } from './components/blockForms';
import { BlockEditorCard } from './components/BlockEditor';
import { Button, FieldLabel, Modal, StatusChip, TextArea, TextInput } from './components/ui';
import { Spinner } from '../site/components/Spinner';

/** The page builder: metadata panel + ordered block list with add palette. */
export function PageEditorPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  const pageId = Number(id);
  const navigate = useNavigate();
  const page = usePageDetail(pageId);
  const { updateMeta, saveBlocks, setPublished, remove } = usePageMutations();

  const [blocks, setBlocks] = useState<Block[]>([]);
  const [meta, setMeta] = useState({
    slug: '', title: '', showInNav: false, navLabel: '', navOrder: 0, seoTitle: '', seoDescription: '',
  });
  const [dirty, setDirty] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (page.data && !dirty) {
      setBlocks(page.data.blocks);
      setMeta({
        slug: page.data.slug,
        title: page.data.title,
        showInNav: page.data.showInNav,
        navLabel: page.data.navLabel ?? '',
        navOrder: page.data.navOrder,
        seoTitle: page.data.seoTitle ?? '',
        seoDescription: page.data.seoDescription ?? '',
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page.data]);

  if (page.isLoading) return <Spinner />;
  if (!page.data) return <p className="text-sm text-error">Page not found.</p>;

  const touch = () => {
    setDirty(true);
    setMessage(null);
  };

  const updateBlock = (index: number, payload: Record<string, unknown>) => {
    setBlocks((prev) => prev.map((block, i) => (i === index ? { ...block, payload } : block)));
    touch();
  };

  const moveBlock = (index: number, direction: -1 | 1) => {
    setBlocks((prev) => {
      const next = prev.slice();
      const [moved] = next.splice(index, 1);
      next.splice(index + direction, 0, moved);
      return next;
    });
    touch();
  };

  const addBlock = (type: BlockType) => {
    setBlocks((prev) => [...prev, { blockType: type, payload: emptyPayload(type) }]);
    setPaletteOpen(false);
    touch();
  };

  const save = async () => {
    setError(null);
    try {
      await updateMeta.mutateAsync({
        id: pageId,
        meta: {
          slug: meta.slug,
          title: meta.title,
          showInNav: meta.showInNav,
          navLabel: meta.navLabel || null,
          navOrder: meta.navOrder,
          seoTitle: meta.seoTitle || null,
          seoDescription: meta.seoDescription || null,
        },
      });
      await saveBlocks.mutateAsync({
        id: pageId,
        blocks: blocks.map((block) => ({ blockType: block.blockType, payload: block.payload })),
      });
      setDirty(false);
      setMessage(t('admin.common.saved'));
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  const saving = updateMeta.isPending || saveBlocks.isPending;
  const published = page.data.status === 'PUBLISHED';

  return (
    <div>
      <div className="mb-2">
        <Link to="/admin" className="text-sm font-medium text-azure-500 hover:underline">← {t('admin.nav.pages')}</Link>
      </div>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{page.data.title}</h1>
        <StatusChip published={published} />
        {dirty && <span className="text-xs font-semibold text-warning">{t('admin.common.unsaved')}</span>}
        {message && <span className="text-xs font-semibold text-success">{message}</span>}
        <span className="ml-auto flex flex-wrap gap-2">
          {published && (
            <a href={`/${page.data.slug === 'home' ? '' : page.data.slug}`} target="_blank" rel="noreferrer"
              className="rounded-lg border border-neutral-300 px-4 py-2 text-sm font-semibold text-neutral-700 hover:bg-neutral-100">
              {t('admin.common.viewPage')} ↗
            </a>
          )}
          <Button variant="ghost" loading={setPublished.isPending}
            onClick={() => setPublished.mutate({ id: pageId, publish: !published })}>
            {published ? t('admin.common.unpublish') : t('admin.common.publish')}
          </Button>
          <Button onClick={save} loading={saving}>{t('admin.common.save')}</Button>
        </span>
      </div>

      {error && <p className="mb-4 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}

      <div className="grid items-start gap-6 lg:grid-cols-[280px_1fr]">
        {/* metadata panel */}
        <aside className="space-y-4 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm lg:sticky lg:top-4">
          <FieldLabel label="Title">
            <TextInput value={meta.title} onChange={(e) => { setMeta({ ...meta, title: e.target.value }); touch(); }} />
          </FieldLabel>
          <FieldLabel label="Slug">
            <TextInput value={meta.slug} onChange={(e) => { setMeta({ ...meta, slug: e.target.value }); touch(); }} />
          </FieldLabel>
          <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
            <input type="checkbox" checked={meta.showInNav}
              onChange={(e) => { setMeta({ ...meta, showInNav: e.target.checked }); touch(); }} />
            Show in navigation
          </label>
          <FieldLabel label="Nav label (optional)">
            <TextInput value={meta.navLabel} onChange={(e) => { setMeta({ ...meta, navLabel: e.target.value }); touch(); }} />
          </FieldLabel>
          <FieldLabel label="Nav order">
            <TextInput type="number" value={meta.navOrder}
              onChange={(e) => { setMeta({ ...meta, navOrder: Number(e.target.value) }); touch(); }} className="max-w-24" />
          </FieldLabel>
          <FieldLabel label="SEO title">
            <TextInput value={meta.seoTitle} onChange={(e) => { setMeta({ ...meta, seoTitle: e.target.value }); touch(); }} />
          </FieldLabel>
          <FieldLabel label="SEO description">
            <TextArea rows={3} value={meta.seoDescription}
              onChange={(e) => { setMeta({ ...meta, seoDescription: e.target.value }); touch(); }} />
          </FieldLabel>
          <Button variant="danger" className="w-full"
            onClick={async () => {
              if (window.confirm(t('admin.common.confirmDelete'))) {
                await remove.mutateAsync(pageId);
                navigate('/admin');
              }
            }}>
            {t('admin.common.delete')}
          </Button>
        </aside>

        {/* block list */}
        <div className="space-y-4">
          {blocks.map((block, index) => (
            <BlockEditorCard
              key={`${block.id ?? 'new'}-${index}`}
              block={block}
              index={index}
              total={blocks.length}
              onChange={(payload) => updateBlock(index, payload)}
              onMove={(direction) => moveBlock(index, direction)}
              onDuplicate={() => {
                setBlocks((prev) => {
                  const next = prev.slice();
                  next.splice(index + 1, 0, { blockType: block.blockType, payload: structuredClone(block.payload) });
                  return next;
                });
                touch();
              }}
              onRemove={() => {
                setBlocks((prev) => prev.filter((_, i) => i !== index));
                touch();
              }}
            />
          ))}
          <button type="button" onClick={() => setPaletteOpen(true)}
            className="w-full rounded-xl border-2 border-dashed border-neutral-300 bg-white px-6 py-5 text-sm
              font-semibold text-neutral-500 transition-colors hover:border-blue-400 hover:text-blue-600">
            + {t('admin.common.add')} block
          </button>
        </div>
      </div>

      <Modal title="Add block" open={paletteOpen} onClose={() => setPaletteOpen(false)} wide>
        <div className="grid gap-3 sm:grid-cols-2">
          {(Object.keys(BLOCK_FORMS) as BlockType[]).map((type) => (
            <button key={type} type="button" onClick={() => addBlock(type)}
              className="rounded-xl border border-neutral-200 p-4 text-left transition-colors hover:border-blue-400 hover:bg-blue-50">
              <span className="block text-sm font-bold text-blue-700">{BLOCK_FORMS[type].label}</span>
              <span className="mt-1 block text-xs text-neutral-500">{BLOCK_FORMS[type].description}</span>
            </button>
          ))}
        </div>
      </Modal>
    </div>
  );
}
