import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { SelectField, TextField } from '../../components/fields';
import { Spinner } from '../../components/Spinner';
import { useAuth } from '../../lib/auth';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import { useFacilities } from '../facilities/api';
import {
  TERNA_TYPES, useCoordinatorOptions, useSaveTerna, useTerna, type SaveTernaPayload, type TernaType,
} from './api';

/** Terna builder (create + edit): 3 facilities, coordinators by type rule, 5 key dates. */
export function TernaBuilderPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const isEdit = id !== undefined && id !== 'new';
  const ternaId = isEdit ? Number(id) : null;
  const navigate = useNavigate();

  const { user, hasAnyRole } = useAuth();
  const { readOnly } = useCycle();
  const canWrite = !readOnly && hasAnyRole('ADMIN', 'SUPER_ADMIN', 'COORDINATOR');

  const { data: existing, isLoading } = useTerna(ternaId);
  const facilities = useFacilities({ page: 0 });
  const coordinators = useCoordinatorOptions();
  const save = useSaveTerna();

  const [ternaCode, setTernaCode] = useState('');
  const [ternaType, setTernaType] = useState<TernaType>('TCS');
  const [facilityIds, setFacilityIds] = useState<(number | '')[]>(['', '', '']);
  const [coordinatorIds, setCoordinatorIds] = useState<number[]>([]);
  const [founding, setFounding] = useState('');
  const [finalForum, setFinalForum] = useState('');
  const [visits, setVisits] = useState<string[]>(['', '', '']);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existing) {
      setTernaCode(existing.ternaCode);
      setTernaType(existing.ternaType);
      const ids: (number | '')[] = ['', '', ''];
      existing.facilities.forEach((facility, i) => {
        ids[(facility.position ?? i + 1) - 1] = facility.facilityId;
      });
      setFacilityIds(ids);
      setCoordinatorIds(existing.coordinators.map((c) => c.userId));
      setFounding(existing.foundingMeetingDate ?? '');
      setFinalForum(existing.finalForumDate ?? '');
      const visitDates = ['', '', ''];
      existing.visitDates.forEach((v) => {
        visitDates[v.visitOrder - 1] = v.visitDate;
      });
      setVisits(visitDates);
    } else if (!isEdit && user && hasAnyRole('COORDINATOR')) {
      // coordinators start with themselves assigned
      setCoordinatorIds([user.id]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existing, isEdit]);

  const requiredCoordinators = ternaType === 'TCS' ? 2 : 1;
  const facilityOptions = facilities.data?.content ?? [];
  const facilitatorOf = useMemo(
    () => new Map(facilityOptions.map((f) => [f.id, f.internalFacilitatorName])),
    [facilityOptions],
  );

  if (isEdit && (isLoading || !existing)) {
    return <Spinner label={t('common.loading')} />;
  }

  const toggleCoordinator = (userId: number) => {
    setCoordinatorIds((current) =>
      current.includes(userId) ? current.filter((v) => v !== userId) : [...current, userId],
    );
  };

  const handleSave = async () => {
    const chosen = facilityIds.filter((v): v is number => v !== '');
    if (chosen.length !== 3 || new Set(chosen).size !== 3) {
      setError(t('ternas.facilityCountRule'));
      return;
    }
    if (coordinatorIds.length !== requiredCoordinators) {
      setError(t('ternas.coordinatorRule'));
      return;
    }
    if (!ternaCode.trim()) {
      setError(`${t('ternas.code')}: ${t('common.required')}`);
      return;
    }
    const payload: SaveTernaPayload = {
      ternaCode: ternaCode.trim(),
      ternaType,
      facilityIds: chosen,
      coordinatorIds,
      foundingMeetingDate: founding || null,
      finalForumDate: finalForum || null,
      visitDates: visits
        .map((date, i) => ({ visitOrder: i + 1, visitDate: date }))
        .filter((v) => v.visitDate !== ''),
    };
    try {
      const saved = await save.mutateAsync({ id: ternaId, payload });
      navigate(`/ternas/${saved.id}`, { replace: true });
      setError(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <div className="max-w-3xl">
      <div className="mb-2">
        <Link to="/ternas" className="text-sm font-medium text-azure-500 hover:underline">
          ← {t('ternas.title')}
        </Link>
      </div>
      <PageHeader title={isEdit ? `${existing?.ternaCode}` : t('ternas.new')} />

      <div className="space-y-6 rounded-xl border border-neutral-200 bg-white p-6 shadow-sm">
        <div className="grid grid-cols-2 gap-4">
          <TextField label={t('ternas.code')} required value={ternaCode} disabled={!canWrite || isEdit}
            placeholder="T1" onChange={(e) => setTernaCode(e.target.value)} />
          <SelectField label={t('ternas.type')} required value={ternaType} disabled={!canWrite}
            onChange={(e) => setTernaType(e.target.value as TernaType)}>
            {TERNA_TYPES.map((type) => (
              <option key={type} value={type}>{type} — {t(`ternas.types.${type}`)}</option>
            ))}
          </SelectField>
        </div>

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">
            {t('ternas.facilities')} <span className="font-normal text-neutral-500">— {t('ternas.facilityCountRule')}</span>
          </legend>
          <div className="space-y-2">
            {[0, 1, 2].map((slot) => (
              <div key={slot} className="flex items-center gap-3">
                <span className="w-6 text-sm font-bold text-blue-600">{slot + 1}.</span>
                <select
                  className="flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm
                    focus:outline-2 focus:outline-offset-1 focus:outline-gold-500 disabled:bg-neutral-100"
                  value={facilityIds[slot]}
                  disabled={!canWrite}
                  onChange={(e) => {
                    const next = [...facilityIds];
                    next[slot] = e.target.value === '' ? '' : Number(e.target.value);
                    setFacilityIds(next);
                  }}
                >
                  <option value="">—</option>
                  {facilityOptions.map((facility) => (
                    <option key={facility.id} value={facility.id}
                      disabled={facilityIds.includes(facility.id) && facilityIds[slot] !== facility.id}>
                      {facility.facilityCode} — {facility.name}
                    </option>
                  ))}
                </select>
                <span className="w-56 truncate text-xs text-neutral-500">
                  {t('facilities.internalFacilitator')}:{' '}
                  {facilityIds[slot] !== '' ? facilitatorOf.get(facilityIds[slot] as number) ?? '—' : '—'}
                </span>
              </div>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">
            {t('ternas.coordinators')}{' '}
            <span className="font-normal text-neutral-500">
              — {t('ternas.coordinatorRule')} ({coordinatorIds.length}/{requiredCoordinators})
            </span>
          </legend>
          <div className="grid max-h-40 grid-cols-2 gap-1.5 overflow-y-auto rounded-lg border border-neutral-200 p-2">
            {(coordinators.data ?? []).map((option) => (
              <label key={option.id} className="flex items-center gap-2 text-sm text-neutral-700">
                <input type="checkbox" disabled={!canWrite} checked={coordinatorIds.includes(option.id)}
                  onChange={() => toggleCoordinator(option.id)} />
                {option.fullName} ({option.username})
              </label>
            ))}
            {coordinators.data?.length === 0 && (
              <p className="col-span-2 text-xs text-neutral-400">{t('common.empty')}</p>
            )}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-1 text-sm font-semibold text-neutral-700">{t('ternas.dates')}</legend>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-5">
            <TextField label={t('ternas.foundingMeeting')} type="date" value={founding} disabled={!canWrite}
              onChange={(e) => setFounding(e.target.value)} />
            {[0, 1, 2].map((i) => (
              <TextField key={i} label={t('ternas.visit', { n: i + 1 })} type="date" value={visits[i]}
                disabled={!canWrite}
                onChange={(e) => {
                  const next = [...visits];
                  next[i] = e.target.value;
                  setVisits(next);
                }} />
            ))}
            <TextField label={t('ternas.finalForum')} type="date" value={finalForum} disabled={!canWrite}
              onChange={(e) => setFinalForum(e.target.value)} />
          </div>
        </fieldset>

        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}

        {canWrite && (
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => navigate('/ternas')}>{t('common.cancel')}</Button>
            <Button onClick={handleSave} loading={save.isPending}>{t('common.save')}</Button>
          </div>
        )}
      </div>
    </div>
  );
}
