import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAdminUsers, useUserMutations, type AdminUserRow, type Role } from './adminApi';
import { apiErrorMessage } from '../lib/api';
import { Button, EmptyState, FieldLabel, Modal, Select, TextInput } from './components/ui';
import { Spinner } from '../site/components/Spinner';

export function UsersPage() {
  const { t } = useTranslation();
  const users = useAdminUsers();
  const { create, update } = useUserMutations();
  const [editing, setEditing] = useState<AdminUserRow | 'new' | null>(null);
  const [draft, setDraft] = useState({ username: '', fullName: '', role: 'EDITOR' as Role, active: true, password: '' });
  const [error, setError] = useState<string | null>(null);

  const open = (user: AdminUserRow | 'new') => {
    setEditing(user);
    setError(null);
    setDraft(user === 'new'
      ? { username: '', fullName: '', role: 'EDITOR', active: true, password: '' }
      : { username: user.username, fullName: user.fullName, role: user.role, active: user.active, password: '' });
  };

  const save = async () => {
    setError(null);
    try {
      if (editing === 'new') {
        await create.mutateAsync({
          username: draft.username, password: draft.password, fullName: draft.fullName, role: draft.role,
        });
      } else if (editing) {
        await update.mutateAsync({
          id: editing.id,
          body: {
            fullName: draft.fullName, role: draft.role, active: draft.active,
            ...(draft.password ? { password: draft.password } : {}),
          },
        });
      }
      setEditing(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  if (users.isLoading) return <Spinner />;

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.users')}</h1>
        <Button onClick={() => open('new')}>+ {t('admin.common.create')}</Button>
      </div>

      {(users.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="bg-blue-600 text-white">
                  <th className="px-4 py-3 font-semibold">Username</th>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Role</th>
                  <th className="px-4 py-3 font-semibold">Active</th>
                  <th className="px-4 py-3 font-semibold" />
                </tr>
              </thead>
              <tbody>
                {(users.data ?? []).map((user, index) => (
                  <tr key={user.id} className={`border-t border-neutral-200 ${index % 2 === 1 ? 'bg-blue-50/60' : ''}`}>
                    <td className="px-4 py-2.5 font-mono text-xs">{user.username}</td>
                    <td className="px-4 py-2.5">{user.fullName}</td>
                    <td className="px-4 py-2.5">{user.role}</td>
                    <td className="px-4 py-2.5">{user.active ? '✓' : '—'}</td>
                    <td className="px-4 py-2.5 text-right">
                      <Button variant="ghost" onClick={() => open(user)}>{t('admin.common.edit')}</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal title={editing === 'new' ? t('admin.common.create') : t('admin.common.edit')} open={editing !== null}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t('admin.common.cancel')}</Button>
            <Button onClick={save} loading={create.isPending || update.isPending}>{t('admin.common.save')}</Button>
          </>
        }>
        <div className="space-y-4">
          <FieldLabel label="Username">
            <TextInput value={draft.username} disabled={editing !== 'new'}
              onChange={(e) => setDraft({ ...draft, username: e.target.value })} />
          </FieldLabel>
          <FieldLabel label="Full name">
            <TextInput value={draft.fullName} onChange={(e) => setDraft({ ...draft, fullName: e.target.value })} />
          </FieldLabel>
          <FieldLabel label="Role">
            <Select value={draft.role} onChange={(e) => setDraft({ ...draft, role: e.target.value as Role })}>
              <option value="EDITOR">Editor</option>
              <option value="ADMIN">Admin</option>
            </Select>
          </FieldLabel>
          {editing !== 'new' && (
            <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
              <input type="checkbox" checked={draft.active}
                onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              Active
            </label>
          )}
          <FieldLabel label={editing === 'new' ? 'Password (min 8 chars)' : 'New password (leave empty to keep)'}>
            <TextInput type="password" value={draft.password}
              onChange={(e) => setDraft({ ...draft, password: e.target.value })} />
          </FieldLabel>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </div>
  );
}
