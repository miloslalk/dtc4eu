import type { ProfileFieldDef, ProfileSchema } from '../features/questionnaires/api';

type ProfileValue = Record<string, unknown>;

/**
 * Generic renderer for the per-form profile header (v0.2 register §2.4),
 * driven by the schema served by the backend. Purely controlled — persistence
 * (debounced PUT of the profile snapshot) lives in the page.
 */
export function ProfileForm({ schema, value, onChange, readOnly }: {
  schema: ProfileSchema;
  value: ProfileValue;
  onChange: (next: ProfileValue) => void;
  readOnly: boolean;
}) {
  const setField = (key: string, fieldValue: unknown) => {
    const next = { ...value };
    if (fieldValue === undefined) {
      delete next[key];
    } else {
      next[key] = fieldValue;
    }
    onChange(next);
  };

  return (
    <div className="space-y-6">
      {schema.sections.map((section) => (
        <section key={section.key} className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
            <h2 className="text-sm font-bold text-blue-700">{section.label}</h2>
          </header>
          <div className="space-y-4 px-5 py-4">
            {section.fields.map((field) => (
              <FieldControl
                key={field.key}
                field={field}
                value={value[field.key]}
                onChange={(v) => setField(field.key, v)}
                readOnly={readOnly}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

const inputClass =
  'w-full rounded-lg border border-neutral-300 px-3 py-1.5 text-sm ' +
  'focus:outline-2 focus:outline-offset-1 focus:outline-gold-500 disabled:bg-neutral-100';

function FieldControl({ field, value, onChange, readOnly, compact }: {
  field: ProfileFieldDef;
  value: unknown;
  onChange: (value: unknown) => void;
  readOnly: boolean;
  compact?: boolean;
}) {
  if (field.type === 'group') {
    return <GroupControl field={field} value={value} onChange={onChange} readOnly={readOnly} />;
  }

  const label = !compact && (
    <span className="mb-1 block text-xs font-semibold text-neutral-600">
      {field.label}
      {field.required && <span className="ml-0.5 text-error">*</span>}
    </span>
  );

  switch (field.type) {
    case 'integer':
    case 'decimal':
      return (
        <label className={compact ? '' : 'block max-w-xs'}>
          {label}
          <input
            type="number"
            step={field.type === 'integer' ? 1 : 'any'}
            min={field.min}
            max={field.max}
            disabled={readOnly}
            value={value == null ? '' : String(value)}
            onChange={(e) => {
              const raw = e.target.value;
              if (raw === '') return onChange(undefined);
              const parsed = field.type === 'integer' ? parseInt(raw, 10) : parseFloat(raw);
              onChange(Number.isNaN(parsed) ? undefined : parsed);
            }}
            className={inputClass}
          />
        </label>
      );
    case 'text':
      if (field.multiline) {
        return (
          <label className="block">
            {label}
            <textarea
              rows={3}
              disabled={readOnly}
              maxLength={field.maxLength}
              value={typeof value === 'string' ? value : ''}
              onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
              className={inputClass}
            />
          </label>
        );
      }
      return (
        <label className={compact ? '' : 'block max-w-md'}>
          {label}
          <input
            type="text"
            disabled={readOnly}
            maxLength={field.maxLength}
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
            className={inputClass}
          />
        </label>
      );
    case 'date':
      return (
        <label className={compact ? '' : 'block max-w-[12rem]'}>
          {label}
          <input
            type="date"
            disabled={readOnly}
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
            className={inputClass}
          />
        </label>
      );
    case 'multiselect': {
      const selected = Array.isArray(value) ? (value as string[]) : [];
      const toggle = (option: string) => {
        const next = selected.includes(option)
          ? selected.filter((v) => v !== option)
          : [...selected, option];
        onChange(next.length === 0 ? undefined : next);
      };
      return (
        <fieldset className={compact ? '' : 'block'}>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">
            {field.label}
            {field.required && <span className="ml-0.5 text-error">*</span>}
          </legend>
          <div className={compact ? 'flex flex-wrap gap-x-3 gap-y-1' : 'grid gap-1.5 sm:grid-cols-2'}>
            {(field.options ?? []).map((option) => (
              <label key={option} className="flex items-start gap-2 text-sm text-neutral-700">
                <input
                  type="checkbox"
                  disabled={readOnly}
                  checked={selected.includes(option)}
                  onChange={() => toggle(option)}
                  className="mt-0.5"
                />
                <span>{option}</span>
              </label>
            ))}
          </div>
        </fieldset>
      );
    }
    case 'boolean':
      return (
        <label className={compact ? '' : 'block max-w-[10rem]'}>
          {label}
          <select
            disabled={readOnly}
            value={value === true ? 'yes' : value === false ? 'no' : ''}
            onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value === 'yes')}
            className={inputClass}
          >
            <option value="">—</option>
            <option value="yes">Yes</option>
            <option value="no">No</option>
          </select>
        </label>
      );
    case 'enum':
      return (
        <label className={compact ? '' : 'block max-w-xs'}>
          {label}
          <select
            disabled={readOnly}
            value={typeof value === 'string' ? value : ''}
            onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
            className={inputClass}
          >
            <option value="">—</option>
            {(field.options ?? []).map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
        </label>
      );
    default:
      return null;
  }
}

function GroupControl({ field, value, onChange, readOnly }: {
  field: ProfileFieldDef;
  value: unknown;
  onChange: (value: unknown) => void;
  readOnly: boolean;
}) {
  const subFields = field.fields ?? [];

  if (!field.repeat) {
    const groupValue = (value ?? {}) as Record<string, unknown>;
    const setSub = (key: string, subValue: unknown) => {
      const next = { ...groupValue };
      if (subValue === undefined) delete next[key];
      else next[key] = subValue;
      onChange(Object.keys(next).length === 0 ? undefined : next);
    };
    return (
      <fieldset className="rounded-lg border border-neutral-200 p-3">
        <legend className="px-1 text-xs font-semibold text-neutral-600">{field.label}</legend>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {subFields.map((sub) => (
            <FieldControl key={sub.key} field={sub} value={groupValue[sub.key]}
              onChange={(v) => setSub(sub.key, v)} readOnly={readOnly} />
          ))}
        </div>
      </fieldset>
    );
  }

  if (field.repeat.mode === 'fixedRows') {
    const rows = field.repeat.rows;
    const groupValue = (value ?? {}) as Record<string, Record<string, unknown>>;
    const setCell = (rowKey: string, subKey: string, cellValue: unknown) => {
      const row = { ...(groupValue[rowKey] ?? {}) };
      if (cellValue === undefined) delete row[subKey];
      else row[subKey] = cellValue;
      const next = { ...groupValue };
      if (Object.keys(row).length === 0) delete next[rowKey];
      else next[rowKey] = row;
      onChange(Object.keys(next).length === 0 ? undefined : next);
    };
    return (
      <fieldset className="rounded-lg border border-neutral-200 p-3">
        <legend className="px-1 text-xs font-semibold text-neutral-600">{field.label}</legend>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-neutral-500">
                <th className="py-1 pr-3 font-medium" />
                {subFields.map((sub) => (
                  <th key={sub.key} className="min-w-[6rem] py-1 pr-3 font-medium">{sub.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.key} className="border-t border-neutral-100">
                  <td className="max-w-[16rem] py-1.5 pr-3 text-xs text-neutral-700">{row.label}</td>
                  {subFields.map((sub) => (
                    <td key={sub.key} className="py-1.5 pr-3">
                      <FieldControl field={sub} value={groupValue[row.key]?.[sub.key]}
                        onChange={(v) => setCell(row.key, sub.key, v)} readOnly={readOnly} compact />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </fieldset>
    );
  }

  // list mode
  const listValue = Array.isArray(value) ? (value as Record<string, unknown>[]) : [];
  const setRow = (index: number, row: Record<string, unknown> | null) => {
    const next = listValue.slice();
    if (row === null) next.splice(index, 1);
    else next[index] = row;
    onChange(next.length === 0 ? undefined : next);
  };
  return (
    <fieldset className="rounded-lg border border-neutral-200 p-3">
      <legend className="px-1 text-xs font-semibold text-neutral-600">{field.label}</legend>
      <div className="space-y-2">
        {listValue.map((row, index) => (
          <div key={index} className="flex flex-wrap items-end gap-2 rounded-lg bg-neutral-50 p-2">
            {subFields.map((sub) => (
              <div key={sub.key} className="min-w-[8rem] flex-1">
                <span className="mb-0.5 block text-[10px] font-semibold text-neutral-500">{sub.label}</span>
                <FieldControl field={sub} value={row[sub.key]}
                  onChange={(v) => {
                    const nextRow = { ...row };
                    if (v === undefined) delete nextRow[sub.key];
                    else nextRow[sub.key] = v;
                    setRow(index, nextRow);
                  }}
                  readOnly={readOnly} compact />
              </div>
            ))}
            {!readOnly && (
              <button type="button" onClick={() => setRow(index, null)}
                className="rounded-lg px-2 py-1.5 text-xs font-semibold text-error hover:bg-error-surface">
                ✕
              </button>
            )}
          </div>
        ))}
        {!readOnly && (
          <button type="button" onClick={() => onChange([...listValue, {}])}
            className="rounded-lg border border-dashed border-neutral-300 px-3 py-1.5 text-xs font-semibold
              text-neutral-600 hover:border-blue-400 hover:text-blue-600">
            + Add row
          </button>
        )}
      </div>
    </fieldset>
  );
}
