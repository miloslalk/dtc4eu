import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { SelectField, TextField } from '../../components/fields';
import { EmptyState } from '../../components/EmptyState';
import { Spinner } from '../../components/Spinner';
import { StatusTag } from '../../components/StatusTag';
import { apiErrorMessage } from '../../lib/api';
import { useCycle } from '../../lib/cycle';
import { useCreateResponse } from '../responses/api';
import { useMyAssignments, useMyProfile, useSaveMyProfile, type MyProfile, type MyTask } from './api';

/** Per-session confirmation flag: the profile is shown at each login (spec CORE-04). */
const confirmedKey = (cycleId: number) => `dtc4eu.profileConfirmed.${cycleId}`;

function readConfirmed(cycleId: number): boolean {
  try {
    return sessionStorage.getItem(confirmedKey(cycleId)) === '1';
  } catch {
    return false;
  }
}

function writeConfirmed(cycleId: number) {
  try {
    sessionStorage.setItem(confirmedKey(cycleId), '1');
  } catch {
    // storage unavailable (private mode): the profile is simply asked again
  }
}

/**
 * Respondent home (spec §7.4, COL-03): confirm or update the profile, then
 * "Your questionnaires" — only the forms the assignment rules give this
 * person, each with its status. Starting a form opens the normal fill page.
 */
export function MyQuestionnairesPage() {
  const { t } = useTranslation();
  const { selected } = useCycle();
  const profile = useMyProfile();
  const assignments = useMyAssignments();
  const [confirmed, setConfirmed] = useState(false);

  useEffect(() => {
    setConfirmed(selected ? readConfirmed(selected.id) : false);
  }, [selected]);

  if (profile.isLoading || assignments.isLoading) return <Spinner />;
  const loadError = profile.error ?? assignments.error;
  if (loadError) {
    return <p className="rounded-lg bg-error-surface px-3 py-2 text-sm text-error">{apiErrorMessage(loadError)}</p>;
  }

  if (!confirmed || !assignments.data?.profileComplete) {
    return (
      <ProfileConfirmation
        profile={profile.data!}
        onConfirmed={() => {
          if (selected) writeConfirmed(selected.id);
          setConfirmed(true);
        }}
      />
    );
  }

  const tasks = assignments.data.tasks;
  return (
    <div className="space-y-4">
      <PageHeader
        title={t('my.title')}
        subtitle={t('my.subtitle')}
        actions={<Button variant="ghost" onClick={() => setConfirmed(false)}>{t('my.editProfile')}</Button>}
      />
      {tasks.length === 0
        ? <EmptyState message={t('my.none')} />
        : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {tasks.map((task) => (
              <TaskCard key={task.form.formId} task={task} facilities={profile.data!.facilities} />
            ))}
          </ul>
        )}
    </div>
  );
}

function ProfileConfirmation({ profile, onConfirmed }: { profile: MyProfile; onConfirmed: () => void }) {
  const { t } = useTranslation();
  const save = useSaveMyProfile();
  const [form, setForm] = useState({
    roleCategory: profile.roleCategory ?? '',
    age: profile.age != null ? String(profile.age) : '',
    projectPhase: profile.projectPhase ?? '',
    country: profile.country ?? '',
    city: profile.city ?? '',
  });
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = async () => {
    if (!form.roleCategory) {
      setError(t('my.categoryRequired'));
      return;
    }
    try {
      await save.mutateAsync({
        roleCategory: form.roleCategory,
        age: form.age === '' ? null : Number(form.age),
        projectPhase: form.projectPhase || null,
        country: form.country || null,
        city: form.city || null,
      });
      onConfirmed();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <PageHeader title={t('my.profileTitle')} subtitle={t('my.profileHint')} />
      <div className="space-y-4 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
        <SelectField label={t('my.category')} required value={form.roleCategory}
          onChange={(e) => setForm({ ...form, roleCategory: e.target.value })}>
          <option value="">—</option>
          {(['USER', 'FAMILY', 'STAFF', 'MANAGER'] as const).map((category) => (
            <option key={category} value={category}>{t(`my.categories.${category}`)}</option>
          ))}
        </SelectField>
        <TextField label={t('my.age')} type="number" min={0} max={120} value={form.age}
          onChange={(e) => setForm({ ...form, age: e.target.value })} />
        <SelectField label={t('my.phase')} value={form.projectPhase}
          onChange={(e) => setForm({ ...form, projectPhase: e.target.value })}>
          <option value="">{t('my.phaseFromFacility')}</option>
          {(['VISITING_TERNA', 'QUALITY_AUDIT', 'TRAINING_PAUSE'] as const).map((phase) => (
            <option key={phase} value={phase}>{t(`my.phases.${phase}`)}</option>
          ))}
        </SelectField>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label={t('my.country')} value={form.country}
            onChange={(e) => setForm({ ...form, country: e.target.value })} />
          <TextField label={t('my.city')} value={form.city}
            onChange={(e) => setForm({ ...form, city: e.target.value })} />
        </div>
        {profile.facilities.length > 0 && (
          <p className="text-xs text-neutral-500">
            {t('my.facilities')}: {profile.facilities.map((f) => f.facilityCode).join(', ')}
          </p>
        )}
        <p className="text-xs text-neutral-500">{t('my.privacyNote')}</p>
        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        <Button className="w-full py-3 text-base" onClick={handleConfirm} loading={save.isPending}>
          {t('my.confirmProfile')}
        </Button>
      </div>
    </div>
  );
}

function TaskCard({ task, facilities }: { task: MyTask; facilities: MyProfile['facilities'] }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const createResponse = useCreateResponse();
  const [starting, setStarting] = useState(false);
  const [subjectCode, setSubjectCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const tone = task.status === 'SUBMITTED' ? 'success' : task.status === 'DRAFT' ? 'warning' : 'neutral';

  const start = async () => {
    if (!subjectCode.trim()) {
      setError(t('my.subjectCodeRequired'));
      return;
    }
    try {
      const created = await createResponse.mutateAsync({
        questionnaireId: task.form.questionnaireId,
        formId: task.form.formId,
        facilityId: facilities.length === 1 ? facilities[0].facilityId : null,
        subjectCode: subjectCode.trim(),
      });
      navigate(`/responses/${created.id}`);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <li className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide text-neutral-500">{task.form.questionnaireCode}</div>
          <div className="text-base font-bold text-blue-700">{task.form.formName}</div>
        </div>
        <StatusTag tone={tone}>{t(`my.statuses.${task.status}`)}</StatusTag>
      </div>
      {task.responseId !== null ? (
        <Button className="py-3 text-base" variant={task.status === 'SUBMITTED' ? 'secondary' : 'primary'}
          onClick={() => navigate(`/responses/${task.responseId}`)}>
          {t(task.status === 'SUBMITTED' ? 'my.view' : 'my.resume')}
        </Button>
      ) : (
        <Button className="py-3 text-base" onClick={() => setStarting(true)}>{t('my.start')}</Button>
      )}
      <Modal
        title={task.form.formName}
        open={starting}
        onClose={() => setStarting(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setStarting(false)}>{t('common.cancel')}</Button>
            <Button onClick={start} loading={createResponse.isPending}>{t('my.start')}</Button>
          </>
        }
      >
        <div className="space-y-3">
          <TextField label={t('my.subjectCode')} required value={subjectCode} autoFocus
            onChange={(e) => setSubjectCode(e.target.value)} />
          <p className="text-xs text-neutral-500">{t('my.subjectCodeHint')}</p>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </li>
  );
}
