import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { FilterBar } from '../../components/FilterBar';
import { filterInputClass } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { useFacilities } from '../facilities/api';
import { TERNA_TYPES, useTernas, type TernaFilters, type TernaRow } from './api';

export function TernasPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canCreate = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COORDINATOR');

  const [filters, setFilters] = useState<TernaFilters>({ page: 0 });
  const { data, isLoading } = useTernas(filters);
  const facilityOptions = useFacilities({ page: 0 });

  const columns: Column<TernaRow>[] = [
    { key: 'ternaCode', header: t('ternas.code'), render: (row) => <span className="font-semibold">{row.ternaCode}</span> },
    { key: 'ternaType', header: t('ternas.type'), render: (row) => <StatusTag tone="info">{row.ternaType}</StatusTag> },
    {
      key: 'facilityCodes',
      header: t('ternas.facilities'),
      render: (row) => (
        <div className="flex flex-wrap gap-1">
          {row.facilityCodes.map((code) => (
            <span key={code} className="rounded bg-blue-50 px-1.5 py-0.5 font-mono text-xs text-blue-700">{code}</span>
          ))}
        </div>
      ),
    },
    {
      key: 'coordinatorNames',
      header: t('ternas.coordinators'),
      render: (row) => row.coordinatorNames.join(', ') || '—',
    },
    { key: 'foundingMeetingDate', header: t('ternas.foundingMeeting'), render: (row) => row.foundingMeetingDate ?? '—' },
    { key: 'finalForumDate', header: t('ternas.finalForum'), render: (row) => row.finalForumDate ?? '—' },
  ];

  return (
    <div>
      <PageHeader
        title={t('ternas.title')}
        actions={canCreate && <Button onClick={() => navigate('/ternas/new')}>{t('ternas.new')}</Button>}
      />

      <FilterBar onClear={() => setFilters({ page: 0 })}>
        <select className={filterInputClass} value={filters.type ?? ''}
          onChange={(e) => setFilters({ ...filters, type: e.target.value, page: 0 })}>
          <option value="">{t('ternas.type')}: {t('common.all')}</option>
          {TERNA_TYPES.map((type) => (
            <option key={type} value={type}>{type} — {t(`ternas.types.${type}`)}</option>
          ))}
        </select>
        <select className={filterInputClass} value={filters.facilityId ?? ''}
          onChange={(e) => setFilters({ ...filters, facilityId: e.target.value, page: 0 })}>
          <option value="">{t('facilities.title')}: {t('common.all')}</option>
          {(facilityOptions.data?.content ?? []).map((facility) => (
            <option key={facility.id} value={facility.id}>{facility.facilityCode}</option>
          ))}
        </select>
        <label className="flex items-center gap-1 text-sm text-neutral-600">
          {t('ternas.from')}
          <input type="date" className={filterInputClass} value={filters.dateFrom ?? ''}
            onChange={(e) => setFilters({ ...filters, dateFrom: e.target.value, page: 0 })} />
        </label>
        <label className="flex items-center gap-1 text-sm text-neutral-600">
          {t('ternas.to')}
          <input type="date" className={filterInputClass} value={filters.dateTo ?? ''}
            onChange={(e) => setFilters({ ...filters, dateTo: e.target.value, page: 0 })} />
        </label>
      </FilterBar>

      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={(row) => row.id}
        loading={isLoading}
        page={data?.page ?? 0}
        size={data?.size ?? 20}
        totalElements={data?.totalElements}
        onPageChange={(page) => setFilters({ ...filters, page })}
        onRowClick={(row) => navigate(`/ternas/${row.id}`)}
      />
    </div>
  );
}
