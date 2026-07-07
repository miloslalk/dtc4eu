import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { FilterBar } from '../../components/FilterBar';
import { filterInputClass } from '../../components/fields';
import { DataTable, type Column } from '../../components/DataTable';
import { StatusTag } from '../../components/StatusTag';
import { ALL_ROLES, useUsers, type UserFilters, type UserRow } from './api';
import { UserFormModal } from './UserFormModal';

export function UsersPage() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<UserFilters>({ page: 0 });
  const { data, isLoading } = useUsers(filters);
  const [modal, setModal] = useState<{ open: boolean; userId: number | null }>({ open: false, userId: null });

  const columns: Column<UserRow>[] = [
    { key: 'username', header: t('auth.username'), render: (u) => <span className="font-semibold">{u.username}</span> },
    { key: 'fullName', header: t('admin.fullName'), render: (u) => u.fullName },
    { key: 'email', header: t('admin.email'), render: (u) => u.email ?? '—' },
    {
      key: 'roles',
      header: t('admin.roles'),
      render: (u) => (
        <div className="flex flex-wrap gap-1">
          {u.roles.map((role) => (
            <StatusTag key={role} tone="gold">{t(`admin.roleNames.${role}`)}</StatusTag>
          ))}
        </div>
      ),
    },
    {
      key: 'active',
      header: t('common.active'),
      render: (u) => (
        <StatusTag tone={u.active ? 'success' : 'neutral'}>
          {u.active ? t('common.active') : t('common.inactive')}
        </StatusTag>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title={t('admin.usersTitle')}
        actions={<Button onClick={() => setModal({ open: true, userId: null })}>{t('admin.newUser')}</Button>}
      />

      <FilterBar onClear={() => setFilters({ page: 0 })}>
        <input
          className={filterInputClass}
          placeholder={t('common.search')}
          value={filters.q ?? ''}
          onChange={(e) => setFilters({ ...filters, q: e.target.value, page: 0 })}
        />
        <select
          className={filterInputClass}
          value={filters.role ?? ''}
          onChange={(e) => setFilters({ ...filters, role: e.target.value, page: 0 })}
        >
          <option value="">{t('admin.roles')}: {t('common.all')}</option>
          {ALL_ROLES.map((role) => (
            <option key={role} value={role}>{t(`admin.roleNames.${role}`)}</option>
          ))}
        </select>
        <select
          className={filterInputClass}
          value={filters.active ?? ''}
          onChange={(e) => setFilters({ ...filters, active: e.target.value, page: 0 })}
        >
          <option value="">{t('common.active')}: {t('common.all')}</option>
          <option value="true">{t('common.active')}</option>
          <option value="false">{t('common.inactive')}</option>
        </select>
      </FilterBar>

      <DataTable
        columns={columns}
        rows={data?.content ?? []}
        rowKey={(u) => u.id}
        loading={isLoading}
        page={data?.page ?? 0}
        size={data?.size ?? 20}
        totalElements={data?.totalElements}
        onPageChange={(page) => setFilters({ ...filters, page })}
        onRowClick={(u) => setModal({ open: true, userId: u.id })}
      />

      <UserFormModal open={modal.open} userId={modal.userId} onClose={() => setModal({ open: false, userId: null })} />
    </div>
  );
}
