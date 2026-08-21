import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { Spinner } from '../../components/Spinner';
import { EmptyState } from '../../components/EmptyState';
import { SelectField, TextField } from '../../components/fields';
import { StatusTag } from '../../components/StatusTag';
import { useFacilities } from '../facilities/api';
import { useQuestionnaires } from '../questionnaires/api';
import {
  AUDIT_TYPES, useAuditMutations, useAudits, useRolePicker,
  type Audit, type AuditType, type SaveAudit,
} from './api';

/** Module 3 — quality audits of the selected cycle. */
export function AuditsPage() {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const isAdmin = hasAnyRole('ADMIN', 'SUPER_ADMIN');
  const canWriteOutcomes = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'AUDIT_COORDINATOR');

  const audits = useAudits();
  const [editing, setEditing] = useState<Audit | 'new' | null>(null);
  const [outcomesFor, setOutcomesFor] = useState<Audit | null>(null);

  if (audits.isLoading) return <Spinner label={t('common.loading')} />;

  return (
    <div>
      <PageHeader
        title={t('audits.title')}
        subtitle={t('audits.subtitle')}
        actions={isAdmin && !readOnly && (
          <Button onClick={() => setEditing('new')}>+ {t('audits.create')}</Button>
        )}
      />

      {(audits.data ?? []).length === 0 ? (
        <EmptyState message={t('audits.empty')} />
      ) : (
        <div className="space-y-4">
          {(audits.data ?? []).map((audit) => (
            <section key={audit.id} className="rounded-xl border border-neutral-200 bg-white shadow-sm">
              <header className="flex flex-wrap items-center gap-3 border-b border-neutral-200 bg-blue-50 px-5 py-3">
                <h2 className="font-mono text-sm font-bold text-blue-700">{audit.auditCode}</h2>
                <StatusTag tone="gold">{t(`audits.types.${audit.auditType}`)}</StatusTag>
                <span className="text-sm text-neutral-700">
                  {audit.facilityCode} — {audit.facilityName}
                </span>
                {audit.renewalNumber != null && (
                  <span className="text-xs text-neutral-500">{t('audits.renewal', { n: audit.renewalNumber })}</span>
                )}
                <span className="ml-auto flex gap-2">
                  {canWriteOutcomes && (
                    <Button variant="ghost" onClick={() => setOutcomesFor(audit)}>{t('audits.outcomes')}</Button>
                  )}
                  {isAdmin && !readOnly && (
                    <Button variant="ghost" onClick={() => setEditing(audit)}>{t('common.edit')}</Button>
                  )}
                </span>
              </header>
              <div className="grid gap-4 px-5 py-4 text-sm sm:grid-cols-2">
                <div>
                  <p className="mb-1 text-xs font-bold uppercase tracking-wide text-neutral-500">
                    {t('audits.coordinators')}
                  </p>
                  <p className="text-neutral-800">
                    {audit.coordinators.map((p) => p.fullName).join(', ') || '—'}
                  </p>
                  <p className="mb-1 mt-3 text-xs font-bold uppercase tracking-wide text-neutral-500">
                    {t('audits.evaluators')}
                  </p>
                  <p className="text-neutral-800">
                    {audit.evaluators.map((p) => p.fullName).join(', ') || '—'}
                  </p>
                </div>
                <div>
                  <p className="mb-1 text-xs font-bold uppercase tracking-wide text-neutral-500">
                    {t('audits.accredited')}
                  </p>
                  {audit.accreditedProcesses.length + audit.accreditedAreas.length + audit.accreditedItems.length === 0 ? (
                    <p className="text-neutral-400">—</p>
                  ) : (
                    <div className="flex flex-wrap gap-1.5">
                      {audit.accreditedProcesses.map((code) => (
                        <span key={`p-${code}`} className="rounded bg-neutral-100 px-2 py-0.5 font-mono text-xs font-semibold text-neutral-700">
                          {code}
                        </span>
                      ))}
                      {audit.accreditedAreas.map((area) => (
                        <span key={`a-${area.areaId}`} className="rounded bg-blue-50 px-2 py-0.5 font-mono text-xs font-semibold text-blue-700">
                          {area.areaCode}
                        </span>
                      ))}
                      {audit.accreditedItems.map((item) => (
                        <span key={`i-${item.itemId}`} className="rounded bg-success-surface px-2 py-0.5 font-mono text-xs font-semibold text-success">
                          {item.itemCode}{item.excellencePoints != null ? ` = ${item.excellencePoints}` : ''}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </section>
          ))}
        </div>
      )}

      {editing !== null && (
        <AuditFormModal audit={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      )}
      {outcomesFor !== null && (
        <OutcomesModal audit={outcomesFor} onClose={() => setOutcomesFor(null)} />
      )}
    </div>
  );
}

function AuditFormModal({ audit, onClose }: { audit: Audit | null; onClose: () => void }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const isAdmin = hasAnyRole('ADMIN', 'SUPER_ADMIN');
  const { create, update, remove } = useAuditMutations();
  const facilities = useFacilities({ page: 0, size: 200 });
  const coordinators = useRolePicker('AUDIT_COORDINATOR', isAdmin);
  const evaluators = useRolePicker('EVALUATOR', isAdmin);

  const [draft, setDraft] = useState<SaveAudit>({
    auditCode: audit?.auditCode ?? '',
    facilityId: audit?.facilityId ?? 0,
    auditType: audit?.auditType ?? 'SIMPLE',
    renewalNumber: audit?.renewalNumber ?? null,
    coordinatorIds: audit?.coordinators.map((p) => p.id) ?? [],
    evaluatorIds: audit?.evaluators.map((p) => p.id) ?? [],
  });
  const [error, setError] = useState<string | null>(null);

  const maxEvaluators = draft.auditType === 'CGQ' ? 3 : 1;
  useEffect(() => {
    setDraft((d) => d.evaluatorIds.length > maxEvaluators
      ? { ...d, evaluatorIds: d.evaluatorIds.slice(0, maxEvaluators) }
      : d);
  }, [maxEvaluators]);

  const toggle = (list: number[], id: number, max?: number): number[] => {
    if (list.includes(id)) return list.filter((v) => v !== id);
    if (max !== undefined && list.length >= max) return list;
    return [...list, id];
  };

  const save = async () => {
    setError(null);
    try {
      if (audit) {
        await update.mutateAsync({ id: audit.id, body: draft });
      } else {
        await create.mutateAsync(draft);
      }
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal title={audit ? t('audits.edit') : t('audits.create')} open onClose={onClose}
      footer={
        <>
          {audit && (
            <Button variant="danger" onClick={async () => {
              if (window.confirm(t('audits.confirmDelete'))) {
                await remove.mutateAsync(audit.id);
                onClose();
              }
            }}>
              {t('common.delete')}
            </Button>
          )}
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={save} loading={create.isPending || update.isPending}
            disabled={!draft.auditCode || !draft.facilityId}>
            {t('common.save')}
          </Button>
        </>
      }>
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label={t('audits.code')} value={draft.auditCode} placeholder="A1"
            onChange={(e) => setDraft({ ...draft, auditCode: e.target.value })} />
          <SelectField label={t('audits.type')} value={draft.auditType}
            onChange={(e) => setDraft({ ...draft, auditType: e.target.value as AuditType })}>
            {AUDIT_TYPES.map((type) => (
              <option key={type} value={type}>{type} — {t(`audits.types.${type}`)}</option>
            ))}
          </SelectField>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <SelectField label={t('audits.facility')} value={draft.facilityId || ''}
            onChange={(e) => setDraft({ ...draft, facilityId: Number(e.target.value) })}>
            <option value="">—</option>
            {(facilities.data?.content ?? []).map((facility) => (
              <option key={facility.id} value={facility.id}>
                {facility.facilityCode} — {facility.name}
              </option>
            ))}
          </SelectField>
          <TextField label={t('audits.renewalNumber')} type="number" value={draft.renewalNumber ?? ''}
            onChange={(e) => setDraft({ ...draft, renewalNumber: e.target.value === '' ? null : Number(e.target.value) })} />
        </div>

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('audits.coordinators')}</legend>
          <p className="mb-1.5 text-xs text-neutral-500">{t('audits.coordinatorHint')}</p>
          <div className="grid max-h-36 grid-cols-1 gap-1.5 overflow-y-auto rounded-lg border border-neutral-200 p-2 sm:grid-cols-2">
            {(coordinators.data ?? []).map((user) => (
              <label key={user.id} className="flex items-center gap-2 text-sm text-neutral-700">
                <input type="checkbox" checked={draft.coordinatorIds.includes(user.id)}
                  onChange={() => setDraft({ ...draft, coordinatorIds: toggle(draft.coordinatorIds, user.id) })} />
                {user.fullName} ({user.username})
              </label>
            ))}
            {coordinators.data?.length === 0 && (
              <p className="col-span-full text-xs text-neutral-400">{t('audits.noRoleUsers')}</p>
            )}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">
            {t('audits.evaluators')}{' '}
            <span className="font-normal text-neutral-500">
              ({draft.evaluatorIds.length}/{maxEvaluators})
            </span>
          </legend>
          <div className="grid max-h-36 grid-cols-1 gap-1.5 overflow-y-auto rounded-lg border border-neutral-200 p-2 sm:grid-cols-2">
            {(evaluators.data ?? []).map((user) => (
              <label key={user.id} className="flex items-center gap-2 text-sm text-neutral-700">
                <input type="checkbox" checked={draft.evaluatorIds.includes(user.id)}
                  disabled={!draft.evaluatorIds.includes(user.id) && draft.evaluatorIds.length >= maxEvaluators}
                  onChange={() => setDraft({ ...draft, evaluatorIds: toggle(draft.evaluatorIds, user.id, maxEvaluators) })} />
                {user.fullName} ({user.username})
              </label>
            ))}
            {evaluators.data?.length === 0 && (
              <p className="col-span-full text-xs text-neutral-400">{t('audits.noRoleUsers')}</p>
            )}
          </div>
        </fieldset>

        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}

function OutcomesModal({ audit, onClose }: { audit: Audit; onClose: () => void }) {
  const { t } = useTranslation();
  const { saveOutcomes } = useAuditMutations();
  const questionnaires = useQuestionnaires();

  const [processText, setProcessText] = useState(audit.accreditedProcesses.join(', '));
  const [areaIds, setAreaIds] = useState<number[]>(audit.accreditedAreas.map((a) => a.areaId));
  const [items, setItems] = useState<{ itemId: number; excellencePoints: string | null }[]>(
    audit.accreditedItems.map((i) => ({ itemId: i.itemId, excellencePoints: i.excellencePoints })),
  );
  const [questionnaireId, setQuestionnaireId] = useState<number | ''>('');
  const [itemToAdd, setItemToAdd] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuestionnaireAreasAndItems(questionnaireId === '' ? null : questionnaireId);

  const save = async () => {
    setError(null);
    try {
      await saveOutcomes.mutateAsync({
        id: audit.id,
        processCodes: processText.split(',').map((s) => s.trim()).filter(Boolean),
        areaIds,
        items,
      });
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal title={t('audits.outcomesFor', { code: audit.auditCode })} open onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={save} loading={saveOutcomes.isPending}>{t('common.save')}</Button>
        </>
      }>
      <div className="space-y-4">
        <TextField label={t('audits.processes')} value={processText} placeholder="AQP-1, AQP-2"
          onChange={(e) => setProcessText(e.target.value)} />

        <SelectField label={t('audits.pickQuestionnaire')} value={questionnaireId}
          onChange={(e) => { setQuestionnaireId(e.target.value === '' ? '' : Number(e.target.value)); setItemToAdd(''); }}>
          <option value="">—</option>
          {(questionnaires.data ?? []).map((q) => (
            <option key={q.id} value={q.id}>{q.code}</option>
          ))}
        </SelectField>

        {detail && (
          <>
            <fieldset>
              <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('audits.areas')}</legend>
              <div className="grid max-h-32 grid-cols-2 gap-1.5 overflow-y-auto rounded-lg border border-neutral-200 p-2 sm:grid-cols-3">
                {detail.areas.map((area) => (
                  <label key={area.id} className="flex items-center gap-2 text-xs text-neutral-700">
                    <input type="checkbox" checked={areaIds.includes(area.id)}
                      onChange={() => setAreaIds(areaIds.includes(area.id)
                        ? areaIds.filter((id) => id !== area.id)
                        : [...areaIds, area.id])} />
                    <span className="font-mono font-bold">{area.areaCode}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            <div className="flex items-end gap-2">
              <SelectField label={t('audits.addItem')} value={itemToAdd} className="flex-1"
                onChange={(e) => setItemToAdd(e.target.value === '' ? '' : Number(e.target.value))}>
                <option value="">—</option>
                {detail.items
                  .filter((item) => !items.some((chosen) => chosen.itemId === item.id))
                  .map((item) => (
                    <option key={item.id} value={item.id}>{item.itemCode}</option>
                  ))}
              </SelectField>
              <Button variant="ghost" disabled={itemToAdd === ''}
                onClick={() => {
                  if (itemToAdd !== '') {
                    setItems([...items, { itemId: itemToAdd, excellencePoints: null }]);
                    setItemToAdd('');
                  }
                }}>
                + {t('common.add')}
              </Button>
            </div>
          </>
        )}

        {items.length > 0 && (
          <div className="space-y-1.5">
            {items.map((item, index) => (
              <div key={item.itemId} className="flex flex-wrap items-center gap-2 rounded-lg bg-neutral-50 px-3 py-1.5 text-sm">
                <span className="font-mono font-bold text-blue-700">
                  {audit.accreditedItems.find((i) => i.itemId === item.itemId)?.itemCode
                    ?? detail?.items.find((i) => i.id === item.itemId)?.itemCode ?? item.itemId}
                </span>
                <input type="number" step="0.001" min={0} max={4}
                  placeholder={t('audits.epValue')}
                  value={item.excellencePoints ?? ''}
                  onChange={(e) => {
                    const next = [...items];
                    next[index] = { ...item, excellencePoints: e.target.value === '' ? null : e.target.value };
                    setItems(next);
                  }}
                  className="w-28 rounded-md border border-neutral-300 px-2 py-1 text-xs" />
                <button type="button" onClick={() => setItems(items.filter((_, i) => i !== index))}
                  className="ml-auto rounded px-2 text-xs font-bold text-error hover:bg-error-surface">
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}

        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}

/** Areas+items of one questionnaire for the outcome pickers. */
function useQuestionnaireAreasAndItems(questionnaireId: number | null) {
  const { data } = useQuestionnaireDetailLite(questionnaireId);
  return data ?? null;
}

import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';

function useQuestionnaireDetailLite(questionnaireId: number | null) {
  return useQuery({
    queryKey: ['questionnaires', 'lite', questionnaireId],
    queryFn: async () => {
      const { data } = await api.get<{
        areas: { id: number; areaCode: string; name: string }[];
        items: { id: number; itemCode: string }[];
      }>(`/questionnaires/${questionnaireId}`);
      return { areas: data.areas, items: data.items };
    },
    enabled: questionnaireId !== null,
  });
}
