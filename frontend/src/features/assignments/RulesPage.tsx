import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal, ConfirmDialog } from '../../components/Modal';
import { SelectField, TextAreaField, TextField } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { StatusTag } from '../../components/StatusTag';
import { apiErrorMessage } from '../../lib/api';
import { useQuestionnaire, useQuestionnaires } from '../questionnaires/api';
import {
  FIELD_OPTIONS, NUMERIC_FIELDS, RULE_FIELDS, useAssignmentRules, useDeleteRule, useSaveRule, useTestRules,
  type AssignmentRule, type RuleCondition, type RuleContext, type RuleField, type RuleGroup, type RuleNode,
} from './api';

const emptyCondition = (): RuleCondition => ({ type: 'condition', field: 'CATEGORY', operator: 'IN', values: [] });
const emptyRoot = (): RuleGroup => ({ type: 'group', op: 'AND', children: [emptyCondition()] });

/**
 * Module 5-bis rule administration (spec COL-01/02): the Upper Coordinator
 * keeps the "profile → questionnaire form" matrix as data. Each rule is an
 * AND/OR condition tree on profile/context fields that assigns one form; the
 * test panel previews what a sample profile would receive.
 */
export function RulesPage() {
  const { t } = useTranslation();
  const rules = useAssignmentRules();
  const deleteRule = useDeleteRule();
  const [editing, setEditing] = useState<AssignmentRule | 'new' | null>(null);
  const [deleting, setDeleting] = useState<AssignmentRule | null>(null);

  const columns: Column<AssignmentRule>[] = [
    { key: 'priority', header: t('rules.priority'), render: (r) => <span className="font-mono">{r.priority}</span> },
    {
      key: 'name',
      header: t('rules.name'),
      render: (r) => (
        <div>
          <div className="font-semibold">{r.name}</div>
          <div className="text-xs text-neutral-500">{describe(r.conditions, t)}</div>
        </div>
      ),
    },
    {
      key: 'target',
      header: t('rules.targetForm'),
      render: (r) => <span className="font-mono text-xs">{r.targetFormCode ?? r.targetFormName ?? r.targetFormId}</span>,
    },
    {
      key: 'validity',
      header: t('rules.validity'),
      render: (r) => (r.validFrom || r.validTo ? `${r.validFrom ?? '…'} → ${r.validTo ?? '…'}` : t('rules.always')),
    },
    {
      key: 'active',
      header: t('rules.status'),
      render: (r) => <StatusTag tone={r.active ? 'success' : 'neutral'}>{t(r.active ? 'rules.active' : 'rules.inactive')}</StatusTag>,
    },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setEditing(r)}>{t('common.edit')}</Button>
          <Button variant="ghost" onClick={() => setDeleting(r)}>{t('common.delete')}</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('rules.title')}
        subtitle={t('rules.subtitle')}
        actions={<Button onClick={() => setEditing('new')}>{t('rules.newRule')}</Button>}
      />

      {rules.error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm text-error">{apiErrorMessage(rules.error)}</p>}
      {rules.data && rules.data.length === 0
        ? <EmptyState message={t('rules.empty')} />
        : <DataTable columns={columns} rows={rules.data ?? []} rowKey={(r) => r.id} />}

      <RuleTester />

      {editing !== null && (
        <RuleEditor rule={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      )}
      <ConfirmDialog
        title={t('rules.deleteTitle')}
        message={t('rules.deleteMessage', { name: deleting?.name ?? '' })}
        open={deleting !== null}
        confirming={deleteRule.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={async () => {
          if (deleting) await deleteRule.mutateAsync(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
}

/** One-line plain-language summary of a condition tree for the rule list. */
function describe(node: RuleNode, t: (key: string) => string): string {
  if (node.type === 'condition') {
    return `${t(`rules.fields.${node.field}`)} ${t(`rules.operators.${node.operator}`)} ${node.values.join(', ')}`;
  }
  const parts = node.children.map((child) => (child.type === 'group' ? `(${describe(child, t)})` : describe(child, t)));
  return parts.join(` ${t(`rules.ops.${node.op}`)} `);
}

function RuleEditor({ rule, onClose }: { rule: AssignmentRule | null; onClose: () => void }) {
  const { t } = useTranslation();
  const saveRule = useSaveRule();
  const questionnaires = useQuestionnaires();
  const [questionnaireId, setQuestionnaireId] = useState<number>(rule?.targetQuestionnaireId ?? 0);
  const effectiveQuestionnaireId = questionnaireId
    || (questionnaires.data ?? []).find((q) => q.active && q.formCount > 0)?.id || 0;
  const detail = useQuestionnaire(effectiveQuestionnaireId);
  const [form, setForm] = useState({
    name: rule?.name ?? '',
    description: rule?.description ?? '',
    targetFormId: rule?.targetFormId ? String(rule.targetFormId) : '',
    priority: String(rule?.priority ?? 0),
    active: rule?.active ?? true,
    validFrom: rule?.validFrom ?? '',
    validTo: rule?.validTo ?? '',
  });
  const [root, setRoot] = useState<RuleNode>(
    rule?.conditions ?? emptyRoot(),
  );
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    if (!form.name.trim() || !form.targetFormId) {
      setError(t('rules.requiredFields'));
      return;
    }
    try {
      await saveRule.mutateAsync({
        id: rule?.id ?? null,
        payload: {
          name: form.name.trim(),
          description: form.description.trim() || null,
          conditions: root,
          targetFormId: Number(form.targetFormId),
          priority: Number(form.priority) || 0,
          active: form.active,
          validFrom: form.validFrom || null,
          validTo: form.validTo || null,
        },
      });
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal
      title={rule ? t('rules.editRule') : t('rules.newRule')}
      open
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleSave} loading={saveRule.isPending}>{t('common.save')}</Button>
        </>
      }
    >
      <div className="space-y-4">
        <TextField label={t('rules.name')} required value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <TextAreaField label={t('rules.description')} value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />

        <div>
          <div className="mb-1 text-sm font-semibold text-neutral-700">{t('rules.conditions')}</div>
          <p className="mb-2 text-xs text-neutral-500">{t('rules.conditionsHint')}</p>
          <NodeEditor node={root} onChange={setRoot} />
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label={t('rules.questionnaire')} value={String(effectiveQuestionnaireId || '')}
            onChange={(e) => { setQuestionnaireId(Number(e.target.value)); setForm({ ...form, targetFormId: '' }); }}>
            {(questionnaires.data ?? []).filter((q) => q.formCount > 0).map((q) => (
              <option key={q.id} value={q.id}>{q.code} — {q.name}</option>
            ))}
          </SelectField>
          <SelectField label={t('rules.targetForm')} required value={form.targetFormId}
            onChange={(e) => setForm({ ...form, targetFormId: e.target.value })}>
            <option value="">—</option>
            {(detail.data?.forms ?? []).map((f) => (
              <option key={f.id} value={f.id}>{f.formCode ?? f.id} — {f.name}</option>
            ))}
          </SelectField>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <TextField label={t('rules.priority')} type="number" value={form.priority}
            onChange={(e) => setForm({ ...form, priority: e.target.value })} />
          <TextField label={t('rules.validFrom')} type="date" value={form.validFrom}
            onChange={(e) => setForm({ ...form, validFrom: e.target.value })} />
          <TextField label={t('rules.validTo')} type="date" value={form.validTo}
            onChange={(e) => setForm({ ...form, validTo: e.target.value })} />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          {t('rules.active')}
        </label>
        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}

/** Recursive AND/OR tree editor: groups hold conditions and nested groups. */
function NodeEditor({ node, onChange, onRemove, depth = 0 }: {
  node: RuleNode;
  onChange: (node: RuleNode) => void;
  onRemove?: () => void;
  depth?: number;
}) {
  const { t } = useTranslation();
  if (node.type === 'condition') {
    return <ConditionEditor condition={node} onChange={onChange} onRemove={onRemove} />;
  }
  const replaceChild = (index: number, child: RuleNode) =>
    onChange({ ...node, children: node.children.map((c, i) => (i === index ? child : c)) });
  const removeChild = (index: number) => onChange({ ...node, children: node.children.filter((_, i) => i !== index) });

  return (
    <div className={`space-y-2 rounded-lg border p-3 ${depth === 0 ? 'border-blue-200 bg-blue-50/40' : 'border-neutral-200 bg-white'}`}>
      <div className="flex flex-wrap items-center gap-2">
        <select aria-label={t('rules.groupOp')} value={node.op}
          onChange={(e) => onChange({ ...node, op: e.target.value as RuleGroup['op'] })}
          className="rounded-md border border-neutral-300 px-2 py-1 text-xs font-bold">
          <option value="AND">{t('rules.ops.AND')}</option>
          <option value="OR">{t('rules.ops.OR')}</option>
        </select>
        <span className="text-xs text-neutral-500">{t(node.op === 'AND' ? 'rules.allMatch' : 'rules.anyMatch')}</span>
        {onRemove && (
          <button type="button" onClick={onRemove} className="ml-auto text-xs text-error hover:underline">
            {t('rules.removeGroup')}
          </button>
        )}
      </div>
      {node.children.map((child, index) => (
        <NodeEditor key={index} node={child} depth={depth + 1}
          onChange={(updated) => replaceChild(index, updated)}
          onRemove={node.children.length > 1 ? () => removeChild(index) : undefined} />
      ))}
      <div className="flex gap-3">
        <button type="button" className="text-xs font-semibold text-blue-700 hover:underline"
          onClick={() => onChange({ ...node, children: [...node.children, emptyCondition()] })}>
          + {t('rules.addCondition')}
        </button>
        {depth < 3 && (
          <button type="button" className="text-xs font-semibold text-blue-700 hover:underline"
            onClick={() => onChange({ ...node, children: [...node.children, { ...emptyRoot(), op: node.op === 'AND' ? 'OR' : 'AND' }] })}>
            + {t('rules.addGroup')}
          </button>
        )}
      </div>
    </div>
  );
}

function ConditionEditor({ condition, onChange, onRemove }: {
  condition: RuleCondition;
  onChange: (node: RuleNode) => void;
  onRemove?: () => void;
}) {
  const { t } = useTranslation();
  const numeric = NUMERIC_FIELDS.includes(condition.field);
  const options = FIELD_OPTIONS[condition.field];
  const operators = numeric ? (['GTE', 'LTE', 'IN', 'NOT_IN'] as const) : (['IN', 'NOT_IN'] as const);
  const inputClass = 'rounded-md border border-neutral-300 px-2 py-1 text-xs';

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-md border border-neutral-200 bg-white px-2 py-1.5">
      <select aria-label={t('rules.field')} className={inputClass} value={condition.field}
        onChange={(e) => onChange({ ...condition, field: e.target.value as RuleField, operator: 'IN', values: [] })}>
        {RULE_FIELDS.map((field) => <option key={field} value={field}>{t(`rules.fields.${field}`)}</option>)}
      </select>
      <select aria-label={t('rules.operator')} className={inputClass} value={condition.operator}
        onChange={(e) => onChange({ ...condition, operator: e.target.value as RuleCondition['operator'] })}>
        {operators.map((op) => <option key={op} value={op}>{t(`rules.operators.${op}`)}</option>)}
      </select>
      {options ? (
        <div className="flex flex-wrap gap-1.5">
          {options.map((option) => (
            <label key={option} className="flex items-center gap-1 text-xs">
              <input type="checkbox" checked={condition.values.includes(option)}
                onChange={(e) => onChange({
                  ...condition,
                  values: e.target.checked ? [...condition.values, option] : condition.values.filter((v) => v !== option),
                })} />
              {option}
            </label>
          ))}
        </div>
      ) : (
        <input aria-label={t('rules.values')} className={`${inputClass} min-w-[10rem] flex-1`}
          placeholder={numeric && condition.operator !== 'IN' && condition.operator !== 'NOT_IN' ? '18' : t('rules.valuesPlaceholder')}
          value={condition.values.join(', ')}
          onChange={(e) => onChange({
            ...condition,
            values: e.target.value.split(',').map((v) => v.trim()).filter((v) => v !== ''),
          })} />
      )}
      {onRemove && (
        <button type="button" onClick={onRemove} aria-label={t('rules.removeCondition')}
          className="ml-auto text-xs text-error hover:underline">✕</button>
      )}
    </div>
  );
}

/** Spec COL-02 rule test tool: a sample profile in, the forms it would receive out. */
function RuleTester() {
  const { t } = useTranslation();
  const test = useTestRules();
  const [values, setValues] = useState<Partial<Record<RuleField, string>>>({});
  const context: RuleContext = useMemo(() => {
    const result: RuleContext = {};
    for (const field of RULE_FIELDS) {
      const raw = values[field]?.trim();
      if (raw) result[field] = raw.split(',').map((v) => v.trim()).filter(Boolean);
    }
    return result;
  }, [values]);

  return (
    <section className="rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
        <h2 className="text-sm font-bold text-blue-700">{t('rules.testTitle')}</h2>
        <p className="mt-0.5 text-xs text-neutral-500">{t('rules.testHint')}</p>
      </header>
      <div className="grid gap-3 p-5 sm:grid-cols-3">
        {RULE_FIELDS.map((field) => (
          FIELD_OPTIONS[field] ? (
            <SelectField key={field} label={t(`rules.fields.${field}`)} value={values[field] ?? ''}
              onChange={(e) => setValues({ ...values, [field]: e.target.value })}>
              <option value="">—</option>
              {FIELD_OPTIONS[field]!.map((option) => <option key={option} value={option}>{option}</option>)}
            </SelectField>
          ) : (
            <TextField key={field} label={t(`rules.fields.${field}`)} value={values[field] ?? ''}
              onChange={(e) => setValues({ ...values, [field]: e.target.value })} />
          )
        ))}
      </div>
      <div className="flex items-center gap-3 border-t border-neutral-200 px-5 py-3">
        <Button variant="secondary" loading={test.isPending} onClick={() => test.mutate({ context })}>
          {t('rules.runTest')}
        </Button>
        {test.error && <span className="text-sm text-error">{apiErrorMessage(test.error)}</span>}
      </div>
      {test.data && (
        <div className="border-t border-neutral-200 px-5 py-3 text-sm">
          {test.data.forms.length === 0 ? (
            <p className="text-neutral-500">{t('rules.testNone')}</p>
          ) : (
            <ul className="space-y-1">
              {test.data.forms.map((form) => (
                <li key={form.formId}>
                  <span className="font-mono font-semibold">{form.questionnaireCode} · {form.formCode ?? form.formName}</span>
                  <span className="text-neutral-500"> — {t('rules.viaRule', { name: form.ruleName })}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
