import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { StatusTag } from '../../components/StatusTag';
import { Spinner } from '../../components/Spinner';
import { ConfirmDialog } from '../../components/Modal';
import { QuestionnaireContainer, type Answer } from '../../components/QuestionnaireContainer';
import { useCycle } from '../../lib/cycle';
import { apiErrorMessage } from '../../lib/api';
import { useQuestionnaire } from '../questionnaires/api';
import { useResponse, useSaveItems, useScores, useSubmitResponse, type AreaScore } from './api';

/** Fill/read screen for one response: autosave while DRAFT, scores once SUBMITTED. */
export function ResponseFillPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const responseId = Number(id);

  const { data: response, isLoading } = useResponse(responseId);
  const { data: questionnaire } = useQuestionnaire(response?.questionnaireId ?? 0);
  const { readOnly: cycleReadOnly } = useCycle();
  const saveItems = useSaveItems(responseId);
  const submit = useSubmitResponse(responseId);

  const submitted = response?.status === 'SUBMITTED';
  const readOnly = submitted || cycleReadOnly;
  const { data: scores } = useScores(responseId, submitted === true);

  const [answers, setAnswers] = useState<Map<number, Answer>>(new Map());
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // seed local answers from the server once
  const seeded = useRef(false);
  useEffect(() => {
    if (response && !seeded.current) {
      const initial = new Map<number, Answer>();
      response.items.forEach((item) =>
        initial.set(item.itemId, { numericValue: item.numericValue, textValue: item.textValue }));
      setAnswers(initial);
      seeded.current = true;
    }
  }, [response]);

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

  const progress = useMemo(() => {
    if (!questionnaire) return { answered: 0, total: 0 };
    const likertIds = questionnaire.items.filter((i) => i.responseType === 'LIKERT').map((i) => i.id);
    const answered = likertIds.filter((itemId) => answers.get(itemId)?.numericValue != null).length;
    return { answered, total: likertIds.length };
  }, [questionnaire, answers]);

  if (isLoading || !response || !questionnaire) {
    return <Spinner label={t('common.loading')} />;
  }

  const handleSubmit = async () => {
    if (timer.current) clearTimeout(timer.current);
    await flush();
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

  return (
    <div className="max-w-4xl">
      <div className="mb-2">
        <Link to="/responses" className="text-sm font-medium text-azure-500 hover:underline">
          ← {t('responses.title')}
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
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
            <span>{t(`responses.categories.${response.respondentCategory}`)}</span>
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

      {!submitted && (
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

      {submitted && scores && scores.length > 0 && <ScoresPanel scores={scores} scaleMax={questionnaire.scaleMax} />}

      {error && !submitted && saveState !== 'error' && (
        <p className="mb-4 rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>
      )}

      <QuestionnaireContainer
        questionnaire={questionnaire}
        answers={answers}
        onAnswer={handleAnswer}
        readOnly={readOnly}
      />

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

/** Area means as horizontal bars on the blue scale (S-33). */
function ScoresPanel({ scores, scaleMax }: { scores: AreaScore[]; scaleMax: number }) {
  const { t } = useTranslation();
  return (
    <section className="mb-6 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-blue-600">{t('responses.scores')}</h2>
      <ul className="space-y-2.5">
        {scores.map((score) => {
          const mean = Number(score.meanValue);
          return (
            <li key={score.areaId} className="flex items-center gap-3">
              <span className="w-10 shrink-0 font-mono text-xs font-bold text-blue-600">{score.areaCode}</span>
              <span className="w-56 shrink-0 truncate text-sm text-neutral-700" title={score.areaName}>
                {score.areaName}
              </span>
              <div className="h-4 flex-1 overflow-hidden rounded-full bg-blue-50">
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
