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
import { FacilityScoresTab } from '../scoring/FacilityScoresTab';
import {
  ACTIVITIES, FACILITY_TYPES, MEMBER_CATEGORIES, STANDARD_TYPES, SUBTYPES_BY_TYPE,
  useAddMember, useDeleteDocumentApplication, useDocumentApplications, useFacilitatorPicker, useFacility,
  useFacilityHistory, useMembers, useParticipation, usePutSag, useRemoveMember, useSags,
  useSaveDocumentApplication, useSaveFacility, useSetParticipation,
  type Activity, type DocumentApplication, type Facility, type FacilityType, type MemberCategory,
  type StandardType,
  PARTICIPATION_CHECKLIST,
  type ChecklistKey,
  QCNC_ADMINISTRATIONS,
  type QcncAdministrationType,
  type QcncStandard,
  useQcncBoard,
  useSaveQcncResults,
} from './api';

type Tab = 'overview' | 'participation' | 'members' | 'scores' | 'sag' | 'qcnc';

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
    { key: 'scores', label: t('facilities.scores') },
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
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {facility.facilityCode}
            <StatusTag tone="info">{t(`facilities.types.${facility.facilityType}`)}</StatusTag>
            {facility.facilitySubtype && (
              <StatusTag tone="gold">{t(`facilities.subtypes.${facility.facilitySubtype}`)}</StatusTag>
            )}
            {!facility.active && <StatusTag tone="neutral">{t('common.inactive')}</StatusTag>}
          </span>
        }
        subtitle={facility.name}
      />

      <div className="mb-5 flex gap-1 overflow-x-auto border-b border-neutral-200">
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
      {tab === 'scores' && <FacilityScoresTab facilityId={facilityId} />}
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
          facilitySubtype: form.facilitySubtype ?? null,
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
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField label={t('facilities.type')} value={form.facilityType} disabled={!canEdit}
          onChange={(e) =>
            setForm({ ...form, facilityType: e.target.value as FacilityType, facilitySubtype: null })}>
          {FACILITY_TYPES.map((type) => (
            <option key={type} value={type}>{t(`facilities.typeNames.${type}`)}</option>
          ))}
        </SelectField>
        <SelectField label={t('facilities.subtype')} value={form.facilitySubtype ?? ''} disabled={!canEdit}
          onChange={(e) =>
            setForm({ ...form, facilitySubtype: e.target.value === '' ? null : e.target.value as Facility['facilitySubtype'] })}>
          <option value="">—</option>
          {SUBTYPES_BY_TYPE[form.facilityType].map((subtype) => (
            <option key={subtype} value={subtype}>{t(`facilities.subtypes.${subtype}`)}</option>
          ))}
        </SelectField>
      </div>
      <TextField label={t('facilities.name')} value={form.name} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, name: e.target.value })} />
      <TextField label={t('facilities.provider')} value={form.provider ?? ''} disabled={!canEdit}
        onChange={(e) => setForm({ ...form, provider: e.target.value })} />
      <div className="grid gap-3 sm:grid-cols-2">
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
  const [checklist, setChecklist] = useState<Record<ChecklistKey, boolean | null>>({
    sagDone: null, qcncDone: null, dtcroLongFormDone: null, foundationMeetingDone: null, finalForumDone: null,
  });
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (participation) {
      setActivity(participation.activity);
      setFacilitatorId(participation.internalFacilitatorId?.toString() ?? '');
      setChecklist({
        sagDone: participation.sagDone,
        qcncDone: participation.qcncDone,
        dtcroLongFormDone: participation.dtcroLongFormDone,
        foundationMeetingDone: participation.foundationMeetingDone,
        finalForumDone: participation.finalForumDone,
      });
    }
  }, [participation]);

  if (isLoading) return <Spinner label={t('common.loading')} />;

  const handleSave = async () => {
    try {
      await setParticipation.mutateAsync({
        activity,
        internalFacilitatorId: facilitatorId ? Number(facilitatorId) : null,
        ...checklist,
      });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-3xl space-y-6">
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

        {/* Expert-feedback activity checklist: OK / NO / unset per cycle */}
        <fieldset className="rounded-lg border border-neutral-200 p-3">
          <legend className="px-1 text-xs font-semibold text-neutral-600">{t('facilities.checklist.title')}</legend>
          <div className="space-y-2">
            {PARTICIPATION_CHECKLIST.map((key) => (
              <div key={key} className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm text-neutral-700">{t(`facilities.checklist.${key}`)}</span>
                <span className="flex gap-1">
                  {([true, false, null] as const).map((value) => (
                    <button key={String(value)} type="button" disabled={!canWrite}
                      onClick={() => setChecklist({ ...checklist, [key]: value })}
                      className={`rounded-md border px-2.5 py-1 text-xs font-bold transition-colors disabled:opacity-60 ${
                        checklist[key] === value
                          ? value === true
                            ? 'border-success bg-success-surface text-success'
                            : value === false
                              ? 'border-error bg-error-surface text-error'
                              : 'border-neutral-400 bg-neutral-100 text-neutral-600'
                          : 'border-neutral-200 text-neutral-400 hover:bg-neutral-50'
                      }`}>
                      {value === true ? 'OK' : value === false ? 'NO' : '—'}
                    </button>
                  ))}
                </span>
              </div>
            ))}
          </div>
        </fieldset>

        {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}
        {canWrite && (
          <Button onClick={handleSave} loading={setParticipation.isPending}>
            {t('facilities.setParticipation')}
          </Button>
        )}
      </div>

      <DocumentsSection facilityId={facilityId} />
      <HistorySection facilityId={facilityId} />
    </div>
  );
}

/** Quality documents applied in the selected cycle (v0.2 register §6, GPP/CGS/EEN). */
function DocumentsSection({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COORDINATOR', 'AUDIT_COORDINATOR');

  const { data: documents } = useDocumentApplications(facilityId);
  const save = useSaveDocumentApplication(facilityId);
  const remove = useDeleteDocumentApplication(facilityId);

  const [editing, setEditing] = useState<DocumentApplication | 'new' | null>(null);
  const [form, setForm] = useState({ standardType: 'GPP' as StandardType, standardCode: '', serviceName: '', level: '' });
  const [error, setError] = useState<string | null>(null);

  const openEditor = (doc: DocumentApplication | 'new') => {
    setEditing(doc);
    setError(null);
    setForm(doc === 'new'
      ? { standardType: 'GPP', standardCode: '', serviceName: '', level: '' }
      : {
          standardType: doc.standardType,
          standardCode: doc.standardCode ?? '',
          serviceName: doc.serviceName ?? '',
          level: doc.applicationLevelPct != null ? String(Number(doc.applicationLevelPct)) : '',
        });
  };

  const handleSave = async () => {
    try {
      await save.mutateAsync({
        id: editing === 'new' || editing === null ? null : editing.id,
        payload: {
          standardType: form.standardType,
          standardCode: form.standardCode.trim() || null,
          serviceName: form.serviceName.trim() || null,
          applicationLevelPct: form.level === '' ? null : Number(form.level),
        },
      });
      setEditing(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="flex items-center justify-between border-b border-neutral-200 bg-blue-50 px-5 py-3">
        <h2 className="text-sm font-bold text-blue-700">{t('facilities.documents.title')}</h2>
        {canWrite && (
          <Button variant="ghost" className="!px-2.5 !py-1 text-xs" onClick={() => openEditor('new')}>
            {t('facilities.documents.add')}
          </Button>
        )}
      </header>
      <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
            <th className="px-4 py-2">{t('facilities.documents.type')}</th>
            <th className="px-4 py-2">{t('facilities.documents.code')}</th>
            <th className="px-4 py-2">{t('facilities.documents.serviceName')}</th>
            <th className="px-4 py-2 text-right">{t('facilities.documents.applicationLevel')}</th>
            {canWrite && <th className="w-28 px-4 py-2" />}
          </tr>
        </thead>
        <tbody>
          {(documents ?? []).map((doc) => (
            <tr key={doc.id} className="border-b border-neutral-100 last:border-0">
              <td className="px-4 py-2"><StatusTag tone="info">{doc.standardType}</StatusTag></td>
              <td className="px-4 py-2 font-mono text-xs">{doc.standardCode ?? '—'}</td>
              <td className="px-4 py-2">{doc.serviceName ?? '—'}</td>
              <td className="px-4 py-2 text-right font-mono">
                {doc.applicationLevelPct != null ? `${Number(doc.applicationLevelPct)} %` : '—'}
              </td>
              {canWrite && (
                <td className="px-4 py-2 text-right">
                  <button type="button" className="mr-2 text-xs font-medium text-azure-500 hover:underline"
                    onClick={() => openEditor(doc)}>
                    {t('common.edit')}
                  </button>
                  <button type="button" className="text-xs font-medium text-error hover:underline"
                    onClick={() => remove.mutate(doc.id, { onError: (e) => alert(apiErrorMessage(e)) })}>
                    {t('common.delete')}
                  </button>
                </td>
              )}
            </tr>
          ))}
          {(documents ?? []).length === 0 && (
            <tr><td colSpan={canWrite ? 5 : 4} className="px-4 py-4 text-sm text-neutral-400">{t('common.empty')}</td></tr>
          )}
        </tbody>
      </table>
      </div>

      {editing !== null && (
        <div className="space-y-3 border-t border-neutral-200 bg-neutral-50 px-5 py-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <SelectField label={t('facilities.documents.type')} value={form.standardType}
              onChange={(e) => setForm({ ...form, standardType: e.target.value as StandardType })}>
              {STANDARD_TYPES.map((type) => (
                <option key={type} value={type}>{t(`facilities.documents.types.${type}`)}</option>
              ))}
            </SelectField>
            <TextField label={t('facilities.documents.code')} placeholder="GPP CT4-LC" value={form.standardCode}
              onChange={(e) => setForm({ ...form, standardCode: e.target.value })} />
            <TextField label={t('facilities.documents.serviceName')} value={form.serviceName}
              onChange={(e) => setForm({ ...form, serviceName: e.target.value })} />
            <TextField label={t('facilities.documents.applicationLevel')} type="number" value={form.level}
              onChange={(e) => setForm({ ...form, level: e.target.value })} />
          </div>
          <p className="text-xs text-neutral-500">{t('facilities.documents.codeHint')}</p>
          {error && <p className="text-sm font-medium text-error">{error}</p>}
          <div className="flex gap-2">
            <Button onClick={handleSave} loading={save.isPending}>{t('common.save')}</Button>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
          </div>
        </div>
      )}
    </section>
  );
}

/** Accreditation history across cycles (v0.2 register §6): EP lists + document application levels. */
function HistorySection({ facilityId }: { facilityId: number }) {
  const { t } = useTranslation();
  const { data: history } = useFacilityHistory(facilityId);
  if (!history) return null;

  const cycleName = (cycle: { cycleCode: string; sequenceNumber: number | null }) => {
    const span = cycle.cycleCode.replace(/^(\d{4})-\d{2}(\d{2})$/, '$1-$2');
    if (cycle.sequenceNumber == null) return span;
    const n = cycle.sequenceNumber;
    const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10 > 3 ? 0 : n % 10];
    return `${n}${suffix} ${span}`;
  };

  return (
    <section className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
        <h2 className="text-sm font-bold text-blue-700">{t('facilities.history.title')}</h2>
        <p className="mt-0.5 text-xs text-neutral-600">
          {t('facilities.history.cyclesInProgramme', { count: history.cyclesInProgramme })}
        </p>
      </header>
      {history.cycles.length === 0 ? (
        <p className="px-5 py-4 text-sm text-neutral-400">{t('common.empty')}</p>
      ) : (
        <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
              <th className="px-4 py-2">{t('facilities.history.cycle')}</th>
              <th className="px-4 py-2">{t('facilities.history.activity')}</th>
              <th className="px-4 py-2">{t('facilities.history.excellencePoints')}</th>
              <th className="px-4 py-2">{t('facilities.history.documents')}</th>
            </tr>
          </thead>
          <tbody>
            {history.cycles.map((cycle) => (
              <tr key={cycle.cycleId} className="border-b border-neutral-100 align-top last:border-0">
                <td className="whitespace-nowrap px-4 py-2 font-semibold">{cycleName(cycle)}</td>
                <td className="px-4 py-2">
                  {cycle.activity ? t(`facilities.activities.${cycle.activity}`) : '—'}
                </td>
                <td className="px-4 py-2 font-mono text-xs">
                  {Object.keys(cycle.excellencePointsByQuestionnaire).length === 0
                    ? t('facilities.history.noEp')
                    : Object.entries(cycle.excellencePointsByQuestionnaire).map(([code, points]) => (
                        <div key={code}>
                          <span className="font-bold text-blue-700">{code}:</span> {points.join('; ')}
                        </div>
                      ))}
                </td>
                <td className="px-4 py-2 text-xs">
                  {cycle.documentApplications.length === 0 ? '—'
                    : cycle.documentApplications.map((doc) => (
                        <div key={doc.id} className="font-mono">
                          {doc.standardCode ?? doc.standardType}
                          {doc.applicationLevelPct != null && ` = ${Number(doc.applicationLevelPct)} %`}
                        </div>
                      ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </section>
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
  const [category, setCategory] = useState<MemberCategory>('USER');
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
          <div className="overflow-x-auto">
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

  const board = useQcncBoard(facilityId);
  const save = useSaveQcncResults(facilityId);
  // local edits per administration column, applied on top of the server marks
  const [edits, setEdits] = useState<Record<string, Record<number, 'C' | 'NC'>>>({});
  const [message, setMessage] = useState<string | null>(null);

  if (board.isLoading) return <Spinner label={t('common.loading')} />;
  if (!board.data) return null;

  const canWriteColumn = (type: QcncAdministrationType) =>
    !readOnly && (type === 'INTERNAL_FACILITATOR'
      ? hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_FACILITATOR')
      : hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COORDINATOR', 'AUDIT_COORDINATOR'));

  const serverMarks = (type: QcncAdministrationType): Record<number, 'C' | 'NC'> =>
    board.data!.administrations.find((a) => a.administrationType === type)?.conformityByStandard ?? {};
  const markOf = (type: QcncAdministrationType, standardId: number): 'C' | 'NC' | undefined =>
    edits[type]?.[standardId] ?? serverMarks(type)[standardId];
  const administeredBy = (type: QcncAdministrationType) =>
    board.data!.administrations.find((a) => a.administrationType === type)?.administeredByName ?? null;

  const setMark = (type: QcncAdministrationType, standardId: number, mark: 'C' | 'NC') =>
    setEdits((prev) => ({ ...prev, [type]: { ...prev[type], [standardId]: mark } }));

  const saveColumn = async (type: QcncAdministrationType) => {
    const merged = { ...serverMarks(type), ...edits[type] };
    try {
      await save.mutateAsync({
        administrationType: type,
        results: Object.entries(merged).map(([standardId, conformity]) => ({
          standardId: Number(standardId), conformity,
        })),
      });
      setEdits((prev) => ({ ...prev, [type]: {} }));
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  const clusters: QcncStandard['clusterCode'][] = ['Q1', 'Q2', 'Q3', 'Q4', 'Q5'];
  const idsToCodes = (ids: number[]) =>
    ids.map((id) => board.data!.standards.find((s) => s.id === id)?.letterCode ?? String(id));

  return (
    <div className="space-y-6">
      <p className="max-w-3xl text-xs text-neutral-500">{t('facilities.qcncBoard.intro')}</p>

      {/* meta lists — standards C/NC in all three administrations */}
      <div className="grid gap-3 sm:grid-cols-2">
        {([['strength', board.data.strengthStandardIds], ['weakness', board.data.weaknessStandardIds]] as const)
          .map(([tone, ids]) => (
            <section key={tone} className="rounded-xl border border-neutral-200 bg-white p-4 shadow-sm">
              <h3 className={`mb-2 text-xs font-bold uppercase tracking-wide ${
                tone === 'strength' ? 'text-success' : 'text-error'
              }`}>
                {t(`facilities.qcncBoard.${tone}Meta`)}
              </h3>
              {ids.length === 0 ? (
                <p className="text-sm text-neutral-400">{t('facilities.qcncBoard.metaEmpty')}</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {idsToCodes(ids).map((code) => (
                    <span key={code} className={`rounded px-2 py-0.5 font-mono text-xs font-semibold ${
                      tone === 'strength' ? 'bg-success-surface text-success' : 'bg-error-surface text-error'
                    }`}>
                      {code}
                    </span>
                  ))}
                </div>
              )}
            </section>
          ))}
      </div>

      {message && <p className="text-sm font-medium text-neutral-600">{message}</p>}

      {clusters.map((cluster) => (
        <section key={cluster} className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
          <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
            <h2 className="text-sm font-bold text-blue-700">
              <span className="mr-2 font-mono text-xs">{cluster}</span>
              {t(`facilities.qcncBoard.clusters.${cluster}`)}
            </h2>
          </header>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 text-left text-xs font-semibold text-neutral-500">
                  <th className="px-4 py-2">{t('facilities.qcncBoard.standard')}</th>
                  {QCNC_ADMINISTRATIONS.map((type) => (
                    <th key={type} className="min-w-32 px-3 py-2 text-center">
                      {t(`facilities.qcncBoard.administrations.${type}`)}
                      <span className="mt-0.5 block font-normal normal-case text-neutral-400">
                        {administeredBy(type) ?? t('facilities.qcncBoard.notAdministered')}
                      </span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {board.data.standards.filter((s) => s.clusterCode === cluster).map((standard) => (
                  <tr key={standard.id} className="border-b border-neutral-100 align-top last:border-0 odd:bg-blue-50/40">
                    <td className="max-w-md px-4 py-2">
                      <span className="mr-2 font-mono text-xs font-bold text-blue-600">{standard.letterCode}</span>
                      {standard.mandatory && (
                        <span title={t('facilities.qcncBoard.mandatory')} className="mr-1 text-gold-600">★</span>
                      )}
                      <span className="text-neutral-800">{standard.description}</span>
                      <span className="mt-0.5 block text-[11px] uppercase tracking-wide text-neutral-400">
                        {t(`facilities.qcncBoard.types.${standard.standardType}`)}
                      </span>
                      {standard.settingParameters && (
                        <span className="mt-0.5 block text-[11px] text-neutral-500">
                          {Object.entries(standard.settingParameters).map(([k, v]) => `${k}: ${v}`).join(' · ')}
                        </span>
                      )}
                    </td>
                    {QCNC_ADMINISTRATIONS.map((type) => {
                      const mark = markOf(type, standard.id);
                      const writable = canWriteColumn(type);
                      return (
                        <td key={type} className="px-3 py-2 text-center">
                          <span className="inline-flex gap-1">
                            {(['C', 'NC'] as const).map((value) => (
                              <button key={value} type="button" disabled={!writable}
                                onClick={() => setMark(type, standard.id, value)}
                                className={`rounded-md border px-2 py-1 text-xs font-bold transition-colors disabled:opacity-50 ${
                                  mark === value
                                    ? value === 'C'
                                      ? 'border-success bg-success-surface text-success'
                                      : 'border-error bg-error-surface text-error'
                                    : 'border-neutral-200 text-neutral-400 hover:bg-neutral-50'
                                }`}>
                                {value}
                              </button>
                            ))}
                          </span>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      <div className="flex flex-wrap gap-2">
        {QCNC_ADMINISTRATIONS.filter(canWriteColumn).map((type) => (
          <Button key={type} loading={save.isPending} onClick={() => saveColumn(type)}>
            {t('facilities.qcncBoard.saveColumn', { column: t(`facilities.qcncBoard.administrations.${type}`) })}
          </Button>
        ))}
      </div>
    </div>
  );
}
