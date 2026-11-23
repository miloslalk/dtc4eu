import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Modal } from '../../components/Modal';
import { Button } from '../../components/Button';
import { TextField } from '../../components/fields';
import { Link } from 'react-router-dom';
import { apiErrorMessage } from '../../lib/api';
import { useCycle } from '../../lib/cycle';
import { cycleLabel } from '../../lib/types';
import type { RoleCode } from '../../lib/types';
import {
  ALL_ROLES, useFacilityPicker, useSaveUser, useTernaPicker, useUserDetail, type UserPayload,
} from './api';

interface Props {
  open: boolean;
  userId: number | null; // null = create
  onClose: () => void;
}

const emptyForm = {
  username: '',
  fullName: '',
  email: '',
  preferredLang: 'en',
  password: '',
  temporaryPassword: true,
  active: true,
  roles: [] as RoleCode[],
  facilityIds: [] as number[],
  ternaIds: [] as number[],
};

export function UserFormModal({ open, userId, onClose }: Props) {
  const { t } = useTranslation();
  const isEdit = userId !== null;
  const { data: detail } = useUserDetail(open ? userId : null);
  const save = useSaveUser();

  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    if (isEdit && detail) {
      setForm({
        username: detail.username,
        fullName: detail.fullName,
        email: detail.email ?? '',
        preferredLang: detail.preferredLang,
        password: '',
        temporaryPassword: true,
        active: detail.active,
        roles: detail.roles,
        facilityIds: detail.facilityIds,
        ternaIds: detail.ternaIds,
      });
    } else if (!isEdit) {
      setForm(emptyForm);
    }
  }, [open, isEdit, detail]);

  const needsFacilityScope = form.roles.includes('INTERNAL_FACILITATOR') || form.roles.includes('EVALUATOR')
    || form.roles.includes('AUDIT_COORDINATOR');
  const needsTernaScope = form.roles.includes('COORDINATOR');
  const facilities = useFacilityPicker(open && needsFacilityScope);
  const ternas = useTernaPicker(open && needsTernaScope);

  const toggle = <T,>(list: T[], value: T): T[] =>
    list.includes(value) ? list.filter((v) => v !== value) : [...list, value];

  const validate = (): string | null => {
    if (!isEdit && form.username.trim().length < 3) return t('auth.usernameRequired');
    if (!form.fullName.trim()) return `${t('admin.fullName')}: ${t('common.required')}`;
    if (!isEdit && form.password.length < 8) return `${t('admin.password')}: min 8`;
    if (isEdit && form.password.length > 0 && form.password.length < 8) return `${t('admin.password')}: min 8`;
    if (form.roles.length === 0) return `${t('admin.roles')}: ${t('common.required')}`;
    return null;
  };

  const handleSubmit = async () => {
    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }
    const payload: UserPayload = {
      ...(isEdit ? {} : { username: form.username.trim() }),
      email: form.email.trim() || null,
      fullName: form.fullName.trim(),
      preferredLang: form.preferredLang || 'en',
      ...(form.password ? { password: form.password, mustChangePassword: form.temporaryPassword }
        : isEdit ? {} : { password: form.password }),
      ...(isEdit ? { active: form.active } : {}),
      roles: form.roles,
      facilityIds: needsFacilityScope ? form.facilityIds : [],
      ternaIds: needsTernaScope ? form.ternaIds : [],
    };
    try {
      await save.mutateAsync({ id: userId, payload });
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal
      title={isEdit ? t('admin.editUser') : t('admin.newUser')}
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleSubmit} loading={save.isPending}>{t('common.save')}</Button>
        </>
      }
    >
      <div className="max-h-[65vh] space-y-4 overflow-y-auto pr-1">
        {!isEdit && (
          <TextField label={t('auth.username')} required value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })} />
        )}
        <TextField label={t('admin.fullName')} required value={form.fullName}
          onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
        <TextField label={t('admin.email')} type="email" value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <TextField
          label={t('admin.password')}
          type="password"
          required={!isEdit}
          placeholder={isEdit ? t('admin.passwordHint') : undefined}
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
        {form.password && (
          <label className="-mt-2 flex items-center gap-2 text-xs text-neutral-600">
            <input type="checkbox" checked={form.temporaryPassword}
              onChange={(e) => setForm({ ...form, temporaryPassword: e.target.checked })} />
            {t('admin.temporaryPassword')}
          </label>
        )}
        {isEdit && (
          <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
            <input type="checkbox" checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            {t('common.active')}
          </label>
        )}

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('admin.roles')} *</legend>
          <div className="grid gap-1.5 sm:grid-cols-2">
            {ALL_ROLES.map((role) => (
              <label key={role} className="flex items-center gap-2 text-sm text-neutral-700">
                <input
                  type="checkbox"
                  checked={form.roles.includes(role)}
                  onChange={() => setForm({ ...form, roles: toggle(form.roles, role) })}
                />
                {t(`admin.roleNames.${role}`)}
              </label>
            ))}
          </div>
        </fieldset>

        {isEdit && form.roles.includes('INTERNAL_FACILITATOR') && (
          <FacilitatedFacilities userId={userId as number} facilities={facilities.data ?? []} onNavigate={onClose} />
        )}

        {needsFacilityScope && (
          <fieldset>
            <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('admin.scopeFacilities')}</legend>
            <p className="mb-1.5 text-xs text-neutral-500">{t('admin.scopeHint')}</p>
            <div className="max-h-36 space-y-1 overflow-y-auto rounded-lg border border-neutral-200 p-2">
              {(facilities.data ?? []).map((f) => (
                <label key={f.id} className="flex items-center gap-2 text-sm text-neutral-700">
                  <input
                    type="checkbox"
                    checked={form.facilityIds.includes(f.id)}
                    onChange={() => setForm({ ...form, facilityIds: toggle(form.facilityIds, f.id) })}
                  />
                  {f.facilityCode} — {f.name}
                </label>
              ))}
              {facilities.data?.length === 0 && (
                <p className="text-xs text-neutral-400">{t('common.empty')}</p>
              )}
            </div>
          </fieldset>
        )}

        {needsTernaScope && (
          <fieldset>
            <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('admin.scopeTernas')}</legend>
            <div className="max-h-36 space-y-1 overflow-y-auto rounded-lg border border-neutral-200 p-2">
              {(ternas.data ?? []).map((terna) => (
                <label key={terna.id} className="flex items-center gap-2 text-sm text-neutral-700">
                  <input
                    type="checkbox"
                    checked={form.ternaIds.includes(terna.id)}
                    onChange={() => setForm({ ...form, ternaIds: toggle(form.ternaIds, terna.id) })}
                  />
                  {terna.ternaCode} ({terna.ternaType})
                </label>
              ))}
              {ternas.data?.length === 0 && <p className="text-xs text-neutral-400">{t('common.empty')}</p>}
            </div>
          </fieldset>
        )}

        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}

/** Facilities this user facilitates in the selected cycle, with the way to assign more (Simone's feedback #4). */
function FacilitatedFacilities({ userId, facilities, onNavigate }: {
  userId: number;
  facilities: { id: number; facilityCode: string; name: string; internalFacilitatorId: number | null }[];
  onNavigate: () => void;
}) {
  const { t } = useTranslation();
  const { selected } = useCycle();
  const mine = facilities.filter((f) => f.internalFacilitatorId === userId);
  return (
    <div className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-sm">
      <p className="text-xs font-semibold text-blue-700">
        {t('admin.facilitates', { cycle: selected ? cycleLabel(selected) : '—' })}
      </p>
      {mine.length === 0
        ? <p className="text-xs text-neutral-600">{t('admin.facilitatesNone')}</p>
        : (
          <ul className="mt-1 flex flex-wrap gap-1.5">
            {mine.map((f) => (
              <li key={f.id}>
                <Link to={`/facilities/${f.id}?tab=participation`} onClick={onNavigate}
                  className="rounded bg-white px-1.5 py-0.5 font-mono text-xs text-blue-700 hover:underline">
                  {f.facilityCode}
                </Link>
              </li>
            ))}
          </ul>
        )}
      <p className="mt-1 text-[11px] text-neutral-500">{t('admin.facilitatesHint')}</p>
    </div>
  );
}
