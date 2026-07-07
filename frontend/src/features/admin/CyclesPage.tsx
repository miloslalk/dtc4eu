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
import type { CycleInfo } from '../../lib/types';
import { useCreateCycle, useSetCurrentCycle, useUpdateCycle } from './api';

export function CyclesPage() {
  const { t } = useTranslation();
  const { cycles } = useCycle();
  const createCycle = useCreateCycle();
  const updateCycle = useUpdateCycle();
  const setCurrent = useSetCurrentCycle();

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', startDate: '', endDate: '' });
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    if (!/^\d{4}-\d{4}$/.test(form.code)) {
      setError(`${t('admin.cycleCode')}: 2026-2027`);
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

  const columns: Column<CycleInfo & { startDate?: string; endDate?: string }>[] = [
    { key: 'code', header: t('admin.cycleCode'), render: (c) => <span className="font-semibold">{c.code}</span> },
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
      key: 'current',
      header: t('admin.current'),
      render: (c) =>
        c.current ? (
          <StatusTag tone="gold">{t('admin.current')}</StatusTag>
        ) : (
          <Button variant="ghost" className="!px-2 !py-1 text-xs" onClick={() => setCurrent.mutate(c.id)}>
            {t('admin.setCurrent')}
          </Button>
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('admin.cyclesTitle')}
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
          <TextField label={t('admin.startDate')} type="date" value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
          <TextField label={t('admin.endDate')} type="date" value={form.endDate}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}
