import { useState } from 'react';
import type { Block } from '../../lib/types';
import { useAdminGalleries } from '../adminApi';
import { BLOCK_FORMS, type FieldDef } from './blockForms';
import { MediaField } from './MediaPicker';
import { RichTextEditor } from './RichTextEditor';
import { Button, FieldLabel, Select, TextArea, TextInput } from './ui';

/** One block's card in the page builder: header controls + typed edit form. */
export function BlockEditorCard({ block, index, total, onChange, onMove, onDuplicate, onRemove }: {
  block: Block;
  index: number;
  total: number;
  onChange: (payload: Record<string, unknown>) => void;
  onMove: (direction: -1 | 1) => void;
  onDuplicate: () => void;
  onRemove: () => void;
}) {
  const def = BLOCK_FORMS[block.blockType];
  const [collapsed, setCollapsed] = useState(false);

  const setField = (key: string, value: unknown) => {
    const next = { ...block.payload };
    if (value === '' || value === null || value === undefined) {
      delete next[key];
    } else {
      next[key] = value;
    }
    onChange(next);
  };

  return (
    <section className="rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="flex flex-wrap items-center gap-2 border-b border-neutral-200 bg-blue-50 px-4 py-2.5">
        <button type="button" onClick={() => setCollapsed((c) => !c)}
          className="text-xs font-bold text-blue-400 hover:text-blue-600" aria-label={collapsed ? 'Expand' : 'Collapse'}>
          {collapsed ? '▸' : '▾'}
        </button>
        <h3 className="text-sm font-bold text-blue-700">{def.label}</h3>
        <span className="text-xs text-neutral-500">{summaryOf(block)}</span>
        <span className="ml-auto flex items-center gap-1">
          <IconBtn label="Move up" disabled={index === 0} onClick={() => onMove(-1)}>↑</IconBtn>
          <IconBtn label="Move down" disabled={index === total - 1} onClick={() => onMove(1)}>↓</IconBtn>
          <IconBtn label="Duplicate" onClick={onDuplicate}>⧉</IconBtn>
          <IconBtn label="Remove" onClick={onRemove} danger>✕</IconBtn>
        </span>
      </header>

      {!collapsed && (
        <div className="space-y-4 px-4 py-4">
          {def.fields.map((field) => (
            <BlockField key={field.key} field={field} value={block.payload[field.key]} onChange={(v) => setField(field.key, v)} />
          ))}
          {def.items && (
            <ItemsEditor
              label={def.items.label}
              fields={def.items.fields}
              value={Array.isArray(block.payload[def.items.key]) ? (block.payload[def.items.key] as Record<string, unknown>[]) : []}
              onChange={(items) => setField(def.items!.key, items)}
            />
          )}
        </div>
      )}
    </section>
  );
}

function summaryOf(block: Block): string {
  const text = block.payload.heading ?? block.payload.title ?? '';
  return typeof text === 'string' ? text.slice(0, 60) : '';
}

function IconBtn({ children, label, onClick, disabled, danger }: {
  children: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  danger?: boolean;
}) {
  return (
    <button type="button" title={label} aria-label={label} onClick={onClick} disabled={disabled}
      className={`rounded-md px-2 py-1 text-sm font-bold disabled:opacity-30 ${
        danger ? 'text-error hover:bg-error-surface' : 'text-neutral-500 hover:bg-blue-100'
      }`}>
      {children}
    </button>
  );
}

function BlockField({ field, value, onChange }: {
  field: FieldDef;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const label = field.required ? `${field.label} *` : field.label;
  switch (field.kind) {
    case 'text':
      return (
        <FieldLabel label={label}>
          <TextInput value={typeof value === 'string' ? value : ''} onChange={(e) => onChange(e.target.value)} />
        </FieldLabel>
      );
    case 'textarea':
      return (
        <FieldLabel label={label}>
          <TextArea rows={3} value={typeof value === 'string' ? value : ''} onChange={(e) => onChange(e.target.value)} />
        </FieldLabel>
      );
    case 'html':
      return (
        <div>
          <span className="mb-1 block text-xs font-semibold text-neutral-600">{label}</span>
          <RichTextEditor value={typeof value === 'string' ? value : ''} onChange={onChange} />
        </div>
      );
    case 'number':
      return (
        <FieldLabel label={label}>
          <TextInput type="number" value={typeof value === 'number' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))} className="max-w-32" />
        </FieldLabel>
      );
    case 'media':
      return <MediaField label={label} value={typeof value === 'number' ? value : null} onChange={onChange} />;
    case 'gallery':
      return <GallerySelect label={label} value={typeof value === 'number' ? value : null} onChange={onChange} />;
    case 'enum':
      return (
        <FieldLabel label={label}>
          <Select value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)} className="max-w-48">
            <option value="">—</option>
            {(field.options ?? []).map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </Select>
        </FieldLabel>
      );
  }
}

function GallerySelect({ label, value, onChange }: {
  label: string;
  value: number | null;
  onChange: (id: number | null) => void;
}) {
  const galleries = useAdminGalleries();
  return (
    <FieldLabel label={label}>
      <Select value={value ?? ''} onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
        className="max-w-64">
        <option value="">—</option>
        {(galleries.data ?? []).map((gallery) => (
          <option key={gallery.id} value={gallery.id}>{gallery.name}</option>
        ))}
      </Select>
    </FieldLabel>
  );
}

function ItemsEditor({ label, fields, value, onChange }: {
  label: string;
  fields: FieldDef[];
  value: Record<string, unknown>[];
  onChange: (items: Record<string, unknown>[]) => void;
}) {
  const setItem = (index: number, item: Record<string, unknown> | null) => {
    const next = value.slice();
    if (item === null) {
      next.splice(index, 1);
    } else {
      next[index] = item;
    }
    onChange(next);
  };

  const move = (index: number, direction: -1 | 1) => {
    const next = value.slice();
    const [moved] = next.splice(index, 1);
    next.splice(index + direction, 0, moved);
    onChange(next);
  };

  return (
    <fieldset className="rounded-lg border border-neutral-200 p-3">
      <legend className="px-1 text-xs font-semibold text-neutral-600">{label}</legend>
      <div className="space-y-3">
        {value.map((item, index) => (
          <div key={index} className="rounded-lg bg-neutral-50 p-3">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-xs font-bold text-neutral-500">#{index + 1}</span>
              <span className="flex gap-1">
                <IconBtn label="Move up" disabled={index === 0} onClick={() => move(index, -1)}>↑</IconBtn>
                <IconBtn label="Move down" disabled={index === value.length - 1} onClick={() => move(index, 1)}>↓</IconBtn>
                <IconBtn label="Remove" danger onClick={() => setItem(index, null)}>✕</IconBtn>
              </span>
            </div>
            <div className="space-y-3">
              {fields.map((field) => (
                <BlockField key={field.key} field={field} value={item[field.key]}
                  onChange={(v) => {
                    const nextItem = { ...item };
                    if (v === '' || v === null || v === undefined) {
                      delete nextItem[field.key];
                    } else {
                      nextItem[field.key] = v;
                    }
                    setItem(index, nextItem);
                  }} />
              ))}
            </div>
          </div>
        ))}
        <Button variant="ghost" onClick={() => onChange([...value, {}])}>+ Add</Button>
      </div>
    </fieldset>
  );
}
