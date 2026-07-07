import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Spinner } from './Spinner';
import { EmptyState } from './EmptyState';

export interface Column<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  sortable?: boolean;
  className?: string;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  loading?: boolean;
  emptyMessage?: ReactNode;
  onRowClick?: (row: T) => void;
  /* server-side pagination (spec §4 envelope) */
  page?: number;
  size?: number;
  totalElements?: number;
  onPageChange?: (page: number) => void;
  /* server-side sort: "field,asc" | "field,desc" */
  sort?: string;
  onSortChange?: (sort: string) => void;
}

export function DataTable<T>({
  columns, rows, rowKey, loading, emptyMessage, onRowClick,
  page = 0, size = rows.length, totalElements, onPageChange, sort, onSortChange,
}: Props<T>) {
  const { t } = useTranslation();

  if (loading) {
    return <Spinner label={t('common.loading')} />;
  }
  if (rows.length === 0) {
    return <EmptyState message={emptyMessage ?? t('common.noResults')} />;
  }

  const [sortField, sortDir] = (sort ?? '').split(',');
  const toggleSort = (key: string) => {
    if (!onSortChange) return;
    onSortChange(sortField === key && sortDir === 'asc' ? `${key},desc` : `${key},asc`);
  };

  const total = totalElements ?? rows.length;
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  const lastPage = Math.max(0, Math.ceil(total / Math.max(size, 1)) - 1);
  const showPagination = onPageChange !== undefined && total > size;

  return (
    <div className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="bg-blue-600 text-white">
              {columns.map((col) => (
                <th key={col.key} className={`px-4 py-3 font-semibold ${col.className ?? ''}`}>
                  {col.sortable && onSortChange ? (
                    <button type="button" onClick={() => toggleSort(col.key)}
                      className="inline-flex items-center gap-1 hover:text-gold-300">
                      {col.header}
                      {sortField === col.key && <span aria-hidden>{sortDir === 'desc' ? '▾' : '▴'}</span>}
                    </button>
                  ) : (
                    col.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={`border-t border-neutral-200 ${i % 2 === 1 ? 'bg-blue-50/60' : 'bg-white'}
                  ${onRowClick ? 'cursor-pointer hover:bg-gold-50' : ''}`}
              >
                {columns.map((col) => (
                  <td key={col.key} className={`px-4 py-2.5 text-neutral-800 ${col.className ?? ''}`}>
                    {col.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {showPagination && (
        <div className="flex items-center justify-between border-t border-neutral-200 px-4 py-2 text-sm text-neutral-600">
          <span>{t('common.pageOf', { from, to, total })}</span>
          <div className="flex gap-2">
            <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}
              className="rounded-md border border-neutral-300 px-3 py-1 font-medium hover:bg-blue-50 disabled:opacity-40">
              {t('common.previous')}
            </button>
            <button type="button" disabled={page >= lastPage} onClick={() => onPageChange(page + 1)}
              className="rounded-md border border-neutral-300 px-3 py-1 font-medium hover:bg-blue-50 disabled:opacity-40">
              {t('common.next')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
