import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { SelectField } from '../../components/fields';
import { StatusTag } from '../../components/StatusTag';
import { apiErrorMessage, downloadFile } from '../../lib/api';
import { useFacilities } from '../facilities/api';
import { useQuestionnaire, useQuestionnaires } from '../questionnaires/api';
import { useImportResponses, type ImportReport, type ImportRowOutcome } from './api';

const OUTCOME_TONE: Record<ImportRowOutcome, 'success' | 'error' | 'gold' | 'warning'> = {
  VALID: 'success', INVALID: 'error', SUBMITTED: 'gold', DRAFT: 'warning',
};

/**
 * Paper questionnaires → platform (spec COL-06): download the form's XLSX
 * template, fill it, upload it for a row-by-row check, then import. Nothing is
 * imported while any row is invalid.
 */
export function ImportResponsesModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const questionnaires = useQuestionnaires();
  const facilities = useFacilities({ page: 0, size: 200 });
  const importResponses = useImportResponses();

  const [target, setTarget] = useState({ questionnaireId: '', formId: '', facilityId: '' });
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: questionnaireDetail } = useQuestionnaire(Number(target.questionnaireId) || 0);
  const forms = questionnaireDetail?.forms ?? [];
  const ready = target.questionnaireId && target.formId && target.facilityId;
  const canCommit = report !== null && !report.committed && report.invalidRows === 0
    && report.fileErrors.length === 0 && report.totalRows > 0;

  const update = (patch: Partial<typeof target>) => {
    setTarget({ ...target, ...patch });
    setReport(null);
  };

  const close = () => {
    setTarget({ questionnaireId: '', formId: '', facilityId: '' });
    setFile(null);
    setReport(null);
    setError(null);
    onClose();
  };

  const run = async (commit: boolean) => {
    if (!ready || !file) {
      setError(t('common.required'));
      return;
    }
    setError(null);
    try {
      setReport(await importResponses.mutateAsync({
        target: {
          questionnaireId: Number(target.questionnaireId),
          formId: Number(target.formId),
          facilityId: Number(target.facilityId),
        },
        file,
        commit,
      }));
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  const formCode = forms.find((f) => f.id === Number(target.formId))?.formCode ?? 'form';

  return (
    <Modal
      title={t('responses.import.title')}
      open={open}
      onClose={close}
      footer={
        report?.committed ? (
          <Button onClick={close}>{t('common.close')}</Button>
        ) : (
          <>
            <Button variant="ghost" onClick={close}>{t('common.cancel')}</Button>
            <Button variant="secondary" onClick={() => run(false)} loading={importResponses.isPending}
              disabled={!ready || !file}>
              {t('responses.import.validate')}
            </Button>
            <Button onClick={() => run(true)} loading={importResponses.isPending} disabled={!canCommit}>
              {t('responses.import.commit')}
            </Button>
          </>
        )
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-neutral-600">{t('responses.import.hint')}</p>
        <SelectField label={t('responses.questionnaire')} required value={target.questionnaireId}
          onChange={(e) => update({ questionnaireId: e.target.value, formId: '' })}>
          <option value="">—</option>
          {(questionnaires.data ?? []).filter((q) => q.active && q.formCount > 0).map((q) => (
            <option key={q.id} value={q.id}>{q.code} — {q.name}</option>
          ))}
        </SelectField>
        <SelectField label={t('responses.form')} required value={target.formId}
          onChange={(e) => update({ formId: e.target.value })}>
          <option value="">—</option>
          {forms.map((f) => (
            <option key={f.id} value={f.id}>{f.name ?? f.formCode ?? f.administrationMode}</option>
          ))}
        </SelectField>
        <SelectField label={t('responses.facility')} required value={target.facilityId}
          onChange={(e) => update({ facilityId: e.target.value })}>
          <option value="">—</option>
          {(facilities.data?.content ?? []).map((facility) => (
            <option key={facility.id} value={facility.id}>{facility.facilityCode} — {facility.name}</option>
          ))}
        </SelectField>

        <div className="flex flex-wrap items-center gap-3">
          <Button variant="ghost" disabled={!target.questionnaireId || !target.formId}
            onClick={() => downloadFile('/responses/import/template', {
              questionnaireId: target.questionnaireId, formId: target.formId,
            }, `${formCode}-template.xlsx`)}>
            {t('responses.import.template')}
          </Button>
          <label className="text-sm">
            <span className="sr-only">{t('responses.import.file')}</span>
            <input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null);
                setReport(null);
              }} />
          </label>
        </div>

        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        {report && <ImportReportView report={report} />}
      </div>
    </Modal>
  );
}

function ImportReportView({ report }: { report: ImportReport }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-2">
      <p className={`rounded-lg px-3 py-2 text-sm font-medium ${report.committed
        ? 'bg-success-surface text-success' : report.invalidRows > 0 || report.fileErrors.length > 0
          ? 'bg-error-surface text-error' : 'bg-blue-50 text-blue-700'}`}>
        {report.committed
          ? t('responses.import.done', { submitted: report.submitted, drafts: report.drafts })
          : t('responses.import.summary', { valid: report.validRows, invalid: report.invalidRows, total: report.totalRows })}
      </p>
      {report.fileErrors.map((message) => (
        <p key={message} className="text-sm text-error">{message}</p>
      ))}
      {report.rows.length > 0 && (
        <div className="max-h-72 overflow-auto rounded-lg border border-neutral-200">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-neutral-50">
              <tr className="text-left text-xs font-semibold text-neutral-500">
                <th className="px-3 py-2">{t('responses.import.row')}</th>
                <th className="px-3 py-2">{t('responses.subjectCode')}</th>
                <th className="px-3 py-2">{t('responses.import.answered')}</th>
                <th className="px-3 py-2">{t('responses.import.outcome')}</th>
                <th className="px-3 py-2">{t('responses.import.notes')}</th>
              </tr>
            </thead>
            <tbody>
              {report.rows.map((row) => (
                <tr key={row.row} className="border-t border-neutral-100 align-top">
                  <td className="px-3 py-2 font-mono text-xs">{row.row}</td>
                  <td className="px-3 py-2 font-mono">{row.subjectCode ?? '—'}</td>
                  <td className="px-3 py-2 font-mono text-xs">{row.answered} / {row.expected}</td>
                  <td className="px-3 py-2">
                    <StatusTag tone={OUTCOME_TONE[row.outcome]}>{t(`responses.import.outcomes.${row.outcome}`)}</StatusTag>
                  </td>
                  <td className="px-3 py-2 text-xs">
                    {row.errors.map((message) => <div key={message} className="text-error">{message}</div>)}
                    {row.warnings.map((message) => <div key={message} className="text-neutral-500">{message}</div>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
