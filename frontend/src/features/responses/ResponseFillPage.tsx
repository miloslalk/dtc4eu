import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { StatusTag } from '../../components/StatusTag';
import { Spinner } from '../../components/Spinner';
import { ConfirmDialog } from '../../components/Modal';
import { QuestionnaireContainer, type Answer } from '../../components/QuestionnaireContainer';
import { ProfileForm } from '../../components/ProfileForm';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import { useProfileSchema, useQuestionnaire } from '../questionnaires/api';
import {
  useResponse, useSaveItems, useScores, useSubmitResponse, useUpdateResponse, type AreaScore,
} from './api';

/**
 * Fill/read screen for one response (S-32): a profile-header step (v0.2
 * register §2.4) followed by the form's Likert areas, autosaving while DRAFT;
 * scores once SUBMITTED.
 */
export function ResponseFillPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const responseId = Number(id);

  const { data: response, isLoading } = useResponse(responseId);
  const { data: questionnaire } = useQuestionnaire(response?.questionnaireId ?? 0);
  const { data: profileSchema } = useProfileSchema(response?.questionnaireId ?? 0, response?.formId ?? null);
  const { readOnly: cycleReadOnly } = useCycle();
  const saveItems = useSaveItems(responseId);
  const updateResponse = useUpdateResponse(responseId);
  const submit = useSubmitResponse(responseId);

  const submitted = response?.status === 'SUBMITTED';
  const readOnly = submitted || cycleReadOnly;
  const { data: scores } = useScores(responseId, submitted === true);

  const [step, setStep] = useState<'profile' | 'items'>('items');
  const [answers, setAnswers] = useState<Map<number, Answer>>(new Map());
  const [profile, setProfile] = useState<Record<string, unknown>>({});
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // seed local answers + profile from the server once
  const seeded = useRef(false);
  useEffect(() => {
    if (response && !seeded.current) {
      const initial = new Map<number, Answer>();
      response.items.forEach((item) =>
        initial.set(item.itemId, { numericValue: item.numericValue, textValue: item.textValue }));
      setAnswers(initial);
      setProfile((response.profileSnapshot ?? {}) as Record<string, unknown>);
      seeded.current = true;
    }
  }, [response]);

  // a DRAFT with a profile step opens on the profile step
  const stepInitialised = useRef(false);
  useEffect(() => {
    if (!stepInitialised.current && response && profileSchema && response.status === 'DRAFT') {
      setStep('profile');
      stepInitialised.current = true;
    }
  }, [response, profileSchema]);

  // debounced autosave of changed items (spec T5: autosave to DRAFT)
  const pending = useRef(new Map<number, Answer>());
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flush = useCallback(async () => {
    if (pending.current.size === 0) return;
    const batch = [...pending.current.entries()].map(([itemId, answer]) => ({
      itemId,
      numericValue: answer.numericValue,
      textValue: answer.textValue,
    }));
    pending.current.clear();
    setSaveState('saving');
    try {
      await saveItems.mutateAsync(batch);
      setSaveState('saved');
    } catch (e) {
      setSaveState('error');
      setError(apiErrorMessage(e));
    }
  }, [saveItems]);

  const handleAnswer = useCallback((itemId: number, answer: Answer) => {
    setAnswers((current) => {
      const next = new Map(current);
      next.set(itemId, answer);
      return next;
    });
    pending.current.set(itemId, answer);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(flush, 700);
  }, [flush]);

  // debounced autosave of the profile snapshot (own timer, same status chip)
  const profileTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const profileDirty = useRef<Record<string, unknown> | null>(null);

  const flushProfile = useCallback(async () => {
    if (!response || profileDirty.current === null) return;
    const snapshot = profileDirty.current;
    profileDirty.current = null;
    setSaveState('saving');
    try {
      await updateResponse.mutateAsync({
        subjectCode: response.subjectCode,
        facilityId: response.facilityId,
        profileSnapshot: snapshot,
      });
      setSaveState('saved');
    } catch (e) {
      setSaveState('error');
      setError(apiErrorMessage(e));
    }
  }, [response, updateResponse]);

  const handleProfileChange = useCallback((next: Record<string, unknown>) => {
    setProfile(next);
    profileDirty.current = next;
    if (profileTimer.current) clearTimeout(profileTimer.current);
    profileTimer.current = setTimeout(flushProfile, 700);
  }, [flushProfile]);

  const progress = useMemo(() => {
    if (!questionnaire || !response) return { answered: 0, total: 0 };
    const likertIds = questionnaire.items
      .filter((i) => i.responseType === 'LIKERT' && (response.formId == null || i.formId === response.formId))
      .map((i) => i.id);
    const answered = likertIds.filter((itemId) => answers.get(itemId)?.numericValue != null).length;
    return { answered, total: likertIds.length };
  }, [questionnaire, response, answers]);

  if (isLoading || !response || !questionnaire) {
    return <Spinner label={t('common.loading')} />;
  }

  const handleSubmit = async () => {
    if (timer.current) clearTimeout(timer.current);
    if (profileTimer.current) clearTimeout(profileTimer.current);
    await flush();
    await flushProfile();
    try {
      await submit.mutateAsync();
      setConfirmOpen(false);
      setError(null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setConfirmOpen(false);
      setError(apiErrorMessage(e));
    }
  };

  const form = questionnaire.forms.find((f) => f.id === response.formId);
  const hasProfileStep = Boolean(profileSchema);

  return (
    <div className="max-w-4xl">
      <div className="mb-2">
        <Link to="/responses" className="text-sm font-medium text-azure-500 hover:underline">
          ← {t('responses.title')}
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {response.questionnaireCode}
            <span className="font-mono text-lg text-neutral-600">{response.subjectCode}</span>
            <StatusTag tone={submitted ? 'gold' : 'neutral'}>
              {submitted ? t('responses.submitted') : t('responses.draft')}
            </StatusTag>
          </span>
        }
        subtitle={
          <>
            {response.facilityCode && <span className="mr-3">{t('responses.facility')}: {response.facilityCode}</span>}
            <span>{form?.name ?? t(`responses.categories.${response.respondentCategory}`)}</span>
          </>
        }
        actions={
          !readOnly && (
            <Button onClick={() => setConfirmOpen(true)} disabled={progress.answered < progress.total}
              loading={submit.isPending}>
              {t('responses.submit')}
            </Button>
          )
        }
      />

      {submitted && (
        <p className="mb-4 rounded-lg bg-gold-50 px-4 py-2 text-sm font-medium text-gold-900">
          {t('responses.readOnlySubmitted')}
        </p>
      )}

      {hasProfileStep && (
        <div className="mb-5 flex gap-2">
          <StepTab active={step === 'profile'} onClick={() => setStep('profile')}>
            {t('responses.stepProfile')}
          </StepTab>
          <StepTab active={step === 'items'} onClick={() => setStep('items')}>
            {t('responses.stepItems')}
          </StepTab>
        </div>
      )}

      {!submitted && step === 'items' && (
        <div className="mb-5 rounded-xl border border-neutral-200 bg-white px-4 py-3 shadow-sm">
          <div className="mb-1.5 flex items-center justify-between text-sm">
            <span className="font-semibold text-neutral-700">
              {t('responses.answered', { answered: progress.answered, total: progress.total })}
            </span>
            <span className="text-xs text-neutral-500">
              {saveState === 'saving' && t('common.saving')}
              {saveState === 'saved' && t('common.saved')}
              {saveState === 'error' && <span className="text-error">{error}</span>}
            </span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-neutral-200">
            <div
              className="h-full rounded-full bg-blue-600 transition-all"
              style={{ width: `${progress.total === 0 ? 0 : (progress.answered / progress.total) * 100}%` }}
            />
          </div>
          {progress.answered < progress.total && (
            <p className="mt-1.5 text-xs text-neutral-500">{t('responses.missingRequired')}</p>
          )}
        </div>
      )}

      {submitted && scores && scores.length > 0 && step === 'items' && (
        <ScoresPanel scores={scores} scaleMax={questionnaire.scaleMax} />
      )}

      {error && !submitted && saveState !== 'error' && (
        <p className="mb-4 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>
      )}

      {step === 'profile' && profileSchema ? (
        <>
          <ProfileForm schema={profileSchema} value={profile} onChange={handleProfileChange} readOnly={readOnly} />
          <div className="mt-5 flex justify-end">
            <Button onClick={() => { setStep('items'); window.scrollTo({ top: 0 }); }}>
              {t('responses.continueToItems')}
            </Button>
          </div>
        </>
      ) : (
        <QuestionnaireContainer
          questionnaire={questionnaire}
          formId={response.formId}
          answers={answers}
          onAnswer={handleAnswer}
          readOnly={readOnly}
        />
      )}

      <ConfirmDialog
        title={t('responses.submit')}
        message={t('responses.submitConfirm')}
        open={confirmOpen}
        onConfirm={handleSubmit}
        onCancel={() => setConfirmOpen(false)}
        confirming={submit.isPending}
      />
    </div>
  );
}

function StepTab({ active, onClick, children }: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${
        active ? 'bg-blue-600 text-white' : 'bg-white text-neutral-600 border border-neutral-200 hover:bg-blue-50'
      }`}
    >
      {children}
    </button>
  );
}

/** Area means as horizontal bars on the blue scale (per-response S-33 panel). */
function ScoresPanel({ scores, scaleMax }: { scores: AreaScore[]; scaleMax: number }) {
  const { t } = useTranslation();
  return (
    <section className="mb-6 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-blue-600">{t('responses.scores')}</h2>
      <ul className="space-y-2.5">
        {scores.map((score) => {
          const mean = Number(score.meanValue);
          return (
            <li key={score.areaId} className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <span className="w-10 shrink-0 font-mono text-xs font-bold text-blue-600">{score.areaCode}</span>
              <span className="min-w-0 flex-1 truncate text-sm text-neutral-700 sm:w-56 sm:flex-none" title={score.areaName}>
                {score.areaName}
              </span>
              <div className="order-last h-4 grow basis-full overflow-hidden rounded-full bg-blue-50 sm:order-none sm:basis-0">
                <div className="flex h-full items-center justify-end rounded-full bg-blue-600 pr-1.5"
                  style={{ width: `${(mean / scaleMax) * 100}%` }}>
                </div>
              </div>
              <span className="w-12 text-right font-mono text-sm font-bold text-blue-700">
                {mean.toFixed(2)}
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
