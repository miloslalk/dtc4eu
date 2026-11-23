import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { FilterBar } from '../../components/FilterBar';
import { filterInputClass } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { CycleEmptyMessage } from '../../components/CycleEmptyMessage';
import { StatusTag } from '../../components/StatusTag';
import { useAuth } from '../../lib/auth';
import { downloadFile } from '../../lib/api';
import {
  ACTIVITIES, FACILITY_SUBTYPES, FACILITY_TYPES, useFacilities, type Facility, type FacilityFilters,
} from './api';
import { FacilityFormModal } from './FacilityFormModal';

export function FacilitiesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hasAnyRole } = useAuth();
  const isAdmin = hasAnyRole('ADMIN', 'SUPER_ADMIN');

  const [filters, setFilters] = useState<FacilityFilters>({ page: 0 });
  const { data, isLoading } = useFacilities(filters);
  const [createOpen, setCreateOpen] = useState(false);

  const columns: Column<Facility>[] = [
    { key: 'facilityCode', header: t('facilities.code'), render: (f) => <span className="font-semibold">{f.facilityCode}</span> },
    { key: 'name', header: t('facilities.name'), render: (f) => f.name },
    {
      key: 'facilityType',
      header: t('facilities.type'),
      render: (f) => <StatusTag tone="info">{t(`facilities.types.${f.facilityType}`)}</StatusTag>,
    },
    {
      key: 'facilitySubtype',
      header: t('facilities.subtype'),
      render: (f) =>
        f.facilitySubtype
          ? <StatusTag tone="gold">{t(`facilities.subtypes.${f.facilitySubtype}`)}</StatusTag>
          : '—',
    },
    { key: 'country', header: t('facilities.country'), render: (f) => f.country ?? '—' },
    { key: 'city', header: t('facilities.city'), render: (f) => f.city ?? '—' },
    {
      key: 'activity',
      header: t('facilities.activity'),
      render: (f) =>
        f.activity ? <StatusTag tone="gold">{t(`facilities.activities.${f.activity}`)}</StatusTag> : '—',
    },
    {
      key: 'internalFacilitatorName',
      header: t('facilities.internalFacilitator'),
      render: (f) => (
        <span className="flex items-center gap-2">
          {f.internalFacilitatorName ?? '—'}
          {isAdmin && (
            <button type="button" className="text-xs font-semibold text-azure-500 hover:underline"
              onClick={(e) => { e.stopPropagation(); navigate(`/facilities/${f.id}?tab=participation`); }}>
              {f.internalFacilitatorName ? t('common.edit') : t('facilities.facilitator.assign')}
            </button>
          )}
        </span>
      ),
    },
  ];

  const handleExport = () =>
    downloadFile('/facilities/export', {
      q: filters.q || undefined,
      type: filters.type || undefined,
      subtype: filters.subtype || undefined,
      country: filters.country || undefined,
      city: filters.city || undefined,
      activity: filters.activity || undefined,
    }, 'facilities.csv');

  return (
    <div>
      <PageHeader
        title={t('facilities.title')}
        actions={
          <>
            <Button variant="ghost" onClick={handleExport}>{t('common.exportCsv')}</Button>
            {isAdmin && <Button onClick={() => setCreateOpen(true)}>{t('facilities.new')}</Button>}
          </>
        }
      />

      <FilterBar onClear={() => setFilters({ page: 0 })}>
        <input className={filterInputClass} placeholder={t('common.search')} value={filters.q ?? ''}
          onChange={(e) => setFilters({ ...filters, q: e.target.value, page: 0 })} />
        <select className={filterInputClass} value={filters.type ?? ''}
          onChange={(e) => setFilters({ ...filters, type: e.target.value, subtype: '', page: 0 })}>
          <option value="">{t('facilities.type')}: {t('common.all')}</option>
          {FACILITY_TYPES.map((type) => (
            <option key={type} value={type}>{t(`facilities.typeNames.${type}`)}</option>
          ))}
        </select>
        <select className={filterInputClass} value={filters.subtype ?? ''}
          onChange={(e) => setFilters({ ...filters, subtype: e.target.value, page: 0 })}>
          <option value="">{t('facilities.subtype')}: {t('common.all')}</option>
          {FACILITY_SUBTYPES.map((subtype) => (
            <option key={subtype} value={subtype}>{t(`facilities.subtypes.${subtype}`)}</option>
          ))}
        </select>
        <input className={filterInputClass} placeholder={t('facilities.country')} value={filters.country ?? ''}
          onChange={(e) => setFilters({ ...filters, country: e.target.value, page: 0 })} />
        <input className={filterInputClass} placeholder={t('facilities.city')} value={filters.city ?? ''}
          onChange={(e) => setFilters({ ...filters, city: e.target.value, page: 0 })} />
        <select className={filterInputClass} value={filters.activity ?? ''}
          onChange={(e) => setFilters({ ...filters, activity: e.target.value, page: 0 })}>
          <option value="">{t('facilities.activity')}: {t('common.all')}</option>
          {ACTIVITIES.map((activity) => (
            <option key={activity} value={activity}>{t(`facilities.activities.${activity}`)}</option>
          ))}
        </select>
      </FilterBar>

      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={(f) => f.id}
        loading={isLoading}
        page={data?.page ?? 0}
        size={data?.size ?? 20}
        totalElements={data?.totalElements}
        onPageChange={(page) => setFilters({ ...filters, page })}
        emptyMessage={<CycleEmptyMessage what={t('facilities.title').toLowerCase()} />}
        onRowClick={(f) => navigate(`/facilities/${f.id}`)}
      />

      <FacilityFormModal open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  );
}
