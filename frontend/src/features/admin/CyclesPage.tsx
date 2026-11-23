import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { TextField } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import { cycleLabel, type CycleInfo } from '../../lib/types';
import { useCreateCycle, useUpdateCycle } from './api';

/**
 * S-40 annual cycles admin. Cycles span 16 months (Sep 1 → Dec 31 of the
 * following year) and ADJACENT CYCLES OVERLAP (v0.2 register §8) — several
 * can be OPEN at once and there is no "current" flag; everyone works in the
 * cycle they select in the header.
 */
export function CyclesPage() {
  const { t } = useTranslation();
  const { cycles } = useCycle();
  const createCycle = useCreateCycle();
  const updateCycle = useUpdateCycle();

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', startDate: '', endDate: '' });
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    if (!/^\d{4}-\d{4}$/.test(form.code)) {
      setError(`${t('admin.cycleCode')}: 2027-2028`);
      return;
    }
    try {
      await createCycle.mutateAsync({
        code: form.code,
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
      });
      setOpen(false);
      setForm({ code: '', startDate: '', endDate: '' });
      setError(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  const handleStatusChange = async (cycle: CycleInfo, status: string) => {
    try {
      await updateCycle.mutateAsync({ id: cycle.id, payload: { status } });
    } catch (e) {
      alert(apiErrorMessage(e));
    }
  };

  const handleDefaultChange = async (cycle: CycleInfo, isDefault: boolean) => {
    try {
      await updateCycle.mutateAsync({ id: cycle.id, payload: { isDefault } });
    } catch (e) {
      alert(apiErrorMessage(e));
    }
  };

  const columns: Column<CycleInfo>[] = [
    {
      key: 'code',
      header: t('admin.cycleCode'),
      render: (c) => <StatusTag tone="gold">{cycleLabel(c)}</StatusTag>,
    },
    { key: 'startDate', header: t('admin.startDate'), render: (c) => c.startDate ?? '—' },
    { key: 'endDate', header: t('admin.endDate'), render: (c) => c.endDate ?? '—' },
    {
      key: 'status',
      header: t('admin.status'),
      render: (c) => (
        <select
          value={c.status}
          onChange={(e) => handleStatusChange(c, e.target.value)}
          className="rounded-md border border-neutral-300 px-2 py-1 text-sm"
          onClick={(e) => e.stopPropagation()}
        >
          {(['OPEN', 'CLOSED', 'ARCHIVED'] as const).map((status) => (
            <option key={status} value={status}>{t(`admin.statuses.${status}`)}</option>
          ))}
        </select>
      ),
    },
    {
      key: 'isDefault',
      header: t('admin.defaultCycle'),
      render: (c) => (
        <label className="flex items-center gap-2 text-sm" onClick={(e) => e.stopPropagation()}>
          <input type="radio" name="default-cycle" checked={c.isDefault === true}
            onChange={() => handleDefaultChange(c, true)} />
          {c.isDefault && (
            <button type="button" className="text-xs text-azure-500 hover:underline"
              onClick={() => handleDefaultChange(c, false)}>
              {t('admin.clearDefault')}
            </button>
          )}
        </label>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('admin.cyclesTitle')}
        subtitle={`${t('admin.overlapHint')} ${t('admin.defaultCycleHint')}`}
        actions={<Button onClick={() => setOpen(true)}>{t('admin.newCycle')}</Button>}
      />

      <DataTable columns={columns} rows={cycles} rowKey={(c) => c.id} />

      <Modal
        title={t('admin.newCycle')}
        open={open}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleCreate} loading={createCycle.isPending}>{t('common.create')}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField label={t('admin.cycleCode')} required placeholder="2027-2028" value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })} />
          <p className="text-xs text-neutral-500">{t('admin.ordinalHint')}</p>
          <TextField label={t('admin.startDate')} type="date" value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
          <TextField label={t('admin.endDate')} type="date" value={form.endDate}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
          <p className="text-xs text-neutral-500">{t('admin.overlapHint')}</p>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}
