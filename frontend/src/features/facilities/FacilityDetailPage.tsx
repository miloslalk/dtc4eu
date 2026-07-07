import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { SelectField, TextAreaField, TextField, filterInputClass } from '../../components/fields';
import { StatusTag } from '../../components/StatusTag';
import { Spinner } from '../../components/Spinner';
import { EmptyState } from '../../components/EmptyState';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import {
  ACTIVITIES, FACILITY_TYPES, MEMBER_CATEGORIES,
  useAddMember, useFacilitatorPicker, useFacility, useMembers, useParticipation, usePutQcnc, usePutSag,
  useQcnc, useRemoveMember, useSags, useSaveFacility, useSetParticipation,
  type Activity, type Facility, type FacilityType, type MemberCategory,
} from './api';

type Tab = 'overview' | 'participation' | 'members' | 'sag' | 'qcnc';

export function FacilityDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const facilityId = Number(id);
  const { data: facility, isLoading } = useFacility(facilityId);
  const [tab, setTab] = useState<Tab>('overview');

  if (isLoading || !facility) {
    return <Spinner label={t('common.loading')} />;
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'overview', label: t('facilities.overview') },
    { key: 'participation', label: t('facilities.participation') },
    { key: 'members', label: t('facilities.members') },
    { key: 'sag', label: t('facilities.sag') },
    { key: 'qcnc', label: t('facilities.qcnc') },
  ];

  return (
    <div>
      <div className="mb-2">
        <Link to="/facilities" className="text-sm font-medium text-azure-500 hover:underline">
          ← {t('facilities.title')}
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {facility.facilityCode}
            <StatusTag tone="info">{facility.facilityType}</StatusTag>
            {!facility.active && <StatusTag tone="neutral">{t('common.inactive')}</StatusTag>}
          </span>
        }
        subtitle={facility.name}
      />

      <div className="mb-5 flex gap-1 border-b border-neutral-200">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`-mb-px rounded-t-lg border-b-2 px-4 py-2 text-sm font-semibold transition-colors ${
              tab === key
                ? 'border-gold-500 text-blue-600'
                : 'border-transparent text-neutral-500 hover:text-blue-600'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab facility={facility} />}
      {tab === 'participation' && <ParticipationTab facilityId={facilityId} />}
      {tab === 'members' && <MembersTab facilityId={facilityId} />}
      {tab === 'sag' && <SagTab facilityId={facilityId} />}
      {tab === 'qcnc' && <QcncTab facilityId={facilityId} />}
    </div>
  );
}

/* ---------------------------------- tabs ---------------------------------- */

function OverviewTab({ facility }: { facility: Facility }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const canEdit = hasAnyRole('ADMIN', 'SUPER_ADMIN');
  const save = useSaveFacility();
  const [form, setForm] = useState({ ...facility });
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => setForm({ ...facility }), [facility]);

  const handleSave = async () => {
    try {
      await save.mutateAsync({
        id: facility.id,
        payload: {
          facilityType: form.facilityType,
          name: form.name,
          provider: form.provider || null,
          place: form.place || null,
          country: form.country || null,
          city: form.city || null,
          address: form.address || null,
          active: form.active,
        },
      });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-xl space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <SelectField label={t('facilities.type')} value={form.facilityType} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, facilityType: e.target.value as FacilityType })}>
        {FACILITY_TYPES.map((type) => (
          <option key={type} value={type}>{type} — {t(`facilities.types.${type}`)}</option>
        ))}
      </SelectField>
      <TextField label={t('facilities.name')} value={form.name} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, name: e.target.value })} />
      <TextField label={t('facilities.provider')} value={form.provider ?? ''} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, provider: e.target.value })} />
      <div className="grid grid-cols-2 gap-3">
        <TextField label={t('facilities.country')} value={form.country ?? ''} disabled={!canEdit}
          onChange={(e) => setForm({ ...form, country: e.target.value })} />
        <TextField label={t('facilities.city')} value={form.city ?? ''} disabled={!canEdit}
          onChange={(e) => setForm({ ...form, city: e.target.value })} />
      </div>
      <TextField label={t('facilities.place')} value={form.place ?? ''} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, place: e.target.value })} />
      <TextAreaField label={t('facilities.address')} value={form.address ?? ''} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, address: e.target.value })} />
      {canEdit && (
        <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
          <input type="checkbox" checked={form.active}
            onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          {t('common.active')}
        </label>
      )}
      {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}
      {canEdit && (
        <Button onClick={handleSave} loading={save.isPending}>{t('common.save')}</Button>
      )}
    </div>
  );
}

function ParticipationTab({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const isAdmin = hasAnyRole('ADMIN', 'SUPER_ADMIN');
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_FACILITATOR');

  const { data: participation, isLoading } = useParticipation(facilityId);
  const setParticipation = useSetParticipation(facilityId);
  const facilitators = useFacilitatorPicker(isAdmin);

  const [activity, setActivity] = useState<Activity>('VISITING_TERNA');
  const [facilitatorId, setFacilitatorId] = useState<string>('');
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (participation) {
      setActivity(participation.activity);
      setFacilitatorId(participation.internalFacilitatorId?.toString() ?? '');
    }
  }, [participation]);

  if (isLoading) return <Spinner label={t('common.loading')} />;

  const handleSave = async () => {
    try {
      await setParticipation.mutateAsync({
        activity,
        internalFacilitatorId: facilitatorId ? Number(facilitatorId) : null,
      });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-xl space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <SelectField label={t('facilities.activity')} value={activity} disabled={!canWrite}
        onChange={(e) => setActivity(e.target.value as Activity)}>
        {ACTIVITIES.map((a) => (
          <option key={a} value={a}>{t(`facilities.activities.${a}`)}</option>
        ))}
      </SelectField>

      {isAdmin ? (
        <SelectField label={t('facilities.internalFacilitator')} value={facilitatorId} disabled={!canWrite}
          onChange={(e) => setFacilitatorId(e.target.value)}>
          <option value="">—</option>
          {(facilitators.data ?? []).map((u) => (
            <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>
          ))}
        </SelectField>
      ) : (
        <p className="text-sm text-neutral-600">
          <span className="font-semibold">{t('facilities.internalFacilitator')}:</span>{' '}
          {participation?.internalFacilitatorName ?? '—'}
        </p>
      )}

      {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}
      {canWrite && (
        <Button onClick={handleSave} loading={setParticipation.isPending}>
          {t('facilities.setParticipation')}
        </Button>
      )}
    </div>
  );
}

function MembersTab({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_FACILITATOR');

  const { data: members, isLoading } = useMembers(facilityId);
  const addMember = useAddMember(facilityId);
  const removeMember = useRemoveMember(facilityId);

  const [code, setCode] = useState('');
  const [category, setCategory] = useState<MemberCategory>('USERS');
  const [error, setError] = useState<string | null>(null);

  if (isLoading) return <Spinner label={t('common.loading')} />;

  const handleAdd = async () => {
    if (!code.trim()) return;
    try {
      await addMember.mutateAsync({ memberCode: code.trim(), category });
      setCode('');
      setError(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-2xl space-y-4">
      {canWrite && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-neutral-200 bg-white p-3 shadow-sm">
          <input className={filterInputClass} placeholder={t('facilities.memberCode')} value={code}
            onChange={(e) => setCode(e.target.value)} />
          <select className={filterInputClass} value={category}
            onChange={(e) => setCategory(e.target.value as MemberCategory)}>
            {MEMBER_CATEGORIES.map((c) => (
              <option key={c} value={c}>{t(`facilities.categories.${c}`)}</option>
            ))}
          </select>
          <Button onClick={handleAdd} loading={addMember.isPending}>{t('facilities.addMember')}</Button>
          {error && <span className="text-sm font-medium text-error">{error}</span>}
        </div>
      )}

      {members && members.length > 0 ? (
        <div className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="bg-blue-600 text-white">
                <th className="px-4 py-3 font-semibold">{t('facilities.memberCode')}</th>
                <th className="px-4 py-3 font-semibold">{t('facilities.category')}</th>
                {canWrite && <th className="w-24 px-4 py-3" />}
              </tr>
            </thead>
            <tbody>
              {members.map((member, i) => (
                <tr key={member.id} className={`border-t border-neutral-200 ${i % 2 === 1 ? 'bg-blue-50/60' : ''}`}>
                  <td className="px-4 py-2.5 font-mono text-neutral-800">{member.memberCode}</td>
                  <td className="px-4 py-2.5">
                    <StatusTag tone="info">{t(`facilities.categories.${member.category}`)}</StatusTag>
                  </td>
                  {canWrite && (
                    <td className="px-4 py-2.5 text-right">
                      <button type="button" onClick={() => removeMember.mutate(member.id)}
                        className="text-sm font-medium text-error hover:underline">
                        {t('common.delete')}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState message={t('common.empty')} />
      )}
    </div>
  );
}

function SagTab({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_FACILITATOR');

  const { data: sags, isLoading } = useSags(facilityId);
  const putSag = usePutSag(facilityId);

  const [variant, setVariant] = useState('A');
  const [payloadText, setPayloadText] = useState('{}');
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const existing = sags?.find((s) => s.gridVariant === variant);
    setPayloadText(JSON.stringify(existing?.payload ?? {}, null, 2));
  }, [sags, variant]);

  if (isLoading) return <Spinner label={t('common.loading')} />;

  const handleSave = async () => {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(payloadText);
    } catch {
      setMessage('Invalid JSON');
      return;
    }
    try {
      await putSag.mutateAsync({ gridVariant: variant, payload: parsed });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-2xl space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <p className="text-xs text-neutral-500">
        The grid structure (variants A–D) is pending confirmation from the clinical specialist (spec §9);
        answers are stored as free-form JSON until then.
      </p>
      <SelectField label={t('facilities.gridVariant')} value={variant}
        onChange={(e) => setVariant(e.target.value)}>
        {['A', 'B', 'C', 'D'].map((v) => (
          <option key={v} value={v}>
            {v}{sags?.some((s) => s.gridVariant === v) ? ' ✓' : ''}
          </option>
        ))}
      </SelectField>
      <TextAreaField label="Payload (JSON)" rows={10} value={payloadText} disabled={!canWrite}
        onChange={(e) => setPayloadText(e.target.value)} className="font-mono text-xs" />
      {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}
      {canWrite && <Button onClick={handleSave} loading={putSag.isPending}>{t('common.save')}</Button>}
    </div>
  );
}

function QcncTab({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_FACILITATOR');

  const { data: qcnc, isLoading } = useQcnc(facilityId);
  const putQcnc = usePutQcnc(facilityId);

  const [answers, setAnswers] = useState<Record<string, string>>({ Q1: '', Q2: '', Q3: '', Q4: '', Q5: '' });
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (qcnc?.payload) {
      setAnswers({
        Q1: String(qcnc.payload.Q1 ?? ''),
        Q2: String(qcnc.payload.Q2 ?? ''),
        Q3: String(qcnc.payload.Q3 ?? ''),
        Q4: String(qcnc.payload.Q4 ?? ''),
        Q5: String(qcnc.payload.Q5 ?? ''),
      });
    }
  }, [qcnc]);

  if (isLoading) return <Spinner label={t('common.loading')} />;

  const handleSave = async () => {
    try {
      await putQcnc.mutateAsync({ payload: answers });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-xl space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <p className="text-xs text-neutral-500">
        The five QCNC quality areas are pending confirmation from the clinical specialist (spec §9).
      </p>
      {(['Q1', 'Q2', 'Q3', 'Q4', 'Q5'] as const).map((key) => (
        <TextField key={key} label={key} value={answers[key]} disabled={!canWrite}
          onChange={(e) => setAnswers({ ...answers, [key]: e.target.value })} />
      ))}
      {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}
      {canWrite && <Button onClick={handleSave} loading={putQcnc.isPending}>{t('common.save')}</Button>}
    </div>
  );
}
