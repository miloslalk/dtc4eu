import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/Button';
import { Modal } from '../../components/Modal';
import { SelectField, TextAreaField, TextField } from '../../components/fields';
import { StatusTag } from '../../components/StatusTag';
import { Spinner } from '../../components/Spinner';
import { apiErrorMessage } from '../../lib/api';
import {
  ADMINISTRATION_MODES, STAKEHOLDER_CATEGORIES,
  useAddForm, useDeleteArea, useDeleteForm, useDeleteItem, useQuestionnaire, useSaveArea, useSaveItem,
  useUpdateQuestionnaire,
  type Area, type Item, type QuestionnaireDetail, type ResponseType,
} from './api';

export function QuestionnaireDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const questionnaireId = Number(id);
  const { data, isLoading } = useQuestionnaire(questionnaireId);

  if (isLoading || !data) {
    return <Spinner label={t('common.loading')} />;
  }

  return (
    <div className="max-w-5xl">
      <div className="mb-2">
        <Link to="/questionnaires" className="text-sm font-medium text-azure-500 hover:underline">
          ← {t('questionnaires.title')}
        </Link>
      </div>
      <Header questionnaire={data} />
      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_1.6fr]">
        <div className="space-y-6">
          <AreasCard questionnaire={data} />
          <FormsCard questionnaire={data} />
        </div>
        <ItemsCard questionnaire={data} />
      </div>
    </div>
  );
}

function Header({ questionnaire }: { questionnaire: QuestionnaireDetail }) {
  const { t } = useTranslation();
  const update = useUpdateQuestionnaire(questionnaire.id);
  const [name, setName] = useState(questionnaire.name);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => setName(questionnaire.name), [questionnaire.name]);

  const toggleActive = async () => {
    try {
      await update.mutateAsync({ name: questionnaire.name, active: !questionnaire.active });
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  const rename = async () => {
    try {
      await update.mutateAsync({ name });
      setMessage(t('common.saved'));
    } catch (e) {
      setMessage(apiErrorMessage(e));
    }
  };

  return (
    <div>
      <PageHeader
        title={
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {questionnaire.code}
            <StatusTag tone="info">{t('questionnaires.scale')} {questionnaire.scaleMin}–{questionnaire.scaleMax}</StatusTag>
            <StatusTag tone={questionnaire.active ? 'success' : 'neutral'}>
              {questionnaire.active ? t('common.active') : t('common.inactive')}
            </StatusTag>
          </span>
        }
        actions={
          <Button variant={questionnaire.active ? 'ghost' : 'secondary'} onClick={toggleActive} loading={update.isPending}>
            {questionnaire.active ? t('questionnaires.deactivate') : t('questionnaires.activate')}
          </Button>
        }
      />
      <div className="flex max-w-xl items-end gap-2">
        <div className="flex-1">
          <TextField label={t('questionnaires.name')} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <Button variant="ghost" onClick={rename} loading={update.isPending}>{t('common.save')}</Button>
      </div>
      {message && <p className="mt-1 text-sm text-neutral-600">{message}</p>}
    </div>
  );
}

function AreasCard({ questionnaire }: { questionnaire: QuestionnaireDetail }) {
  const { t } = useTranslation();
  const saveArea = useSaveArea(questionnaire.id);
  const deleteArea = useDeleteArea(questionnaire.id);
  const [editing, setEditing] = useState<Area | null | 'new'>(null);
  const [form, setForm] = useState({ areaCode: '', name: '', sortOrder: '' });
  const [error, setError] = useState<string | null>(null);

  const openEditor = (area: Area | 'new') => {
    setEditing(area);
    setError(null);
    setForm(area === 'new'
      ? { areaCode: '', name: '', sortOrder: String(questionnaire.areas.length + 1) }
      : { areaCode: area.areaCode, name: area.name, sortOrder: area.sortOrder?.toString() ?? '' });
  };

  const handleSave = async () => {
    try {
      await saveArea.mutateAsync({
        areaId: editing === 'new' || editing === null ? null : editing.id,
        payload: {
          areaCode: form.areaCode.trim(),
          name: form.name.trim(),
          sortOrder: form.sortOrder === '' ? null : Number(form.sortOrder),
        },
      });
      setEditing(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <section className="rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
        <h2 className="text-sm font-bold uppercase tracking-wide text-blue-600">
          {t('questionnaires.areas')} ({questionnaire.areas.length})
        </h2>
        <Button variant="ghost" className="!px-2.5 !py-1 text-xs" onClick={() => openEditor('new')}>
          {t('questionnaires.addArea')}
        </Button>
      </header>
      <ul className="divide-y divide-neutral-100">
        {questionnaire.areas.map((area) => (
          <li key={area.id} className="flex items-center gap-2 px-4 py-2 text-sm">
            <span className="font-mono text-xs font-bold text-blue-600">{area.areaCode}</span>
            <span className="flex-1 truncate text-neutral-800" title={area.name}>{area.name}</span>
            <button type="button" className="text-xs font-medium text-azure-500 hover:underline"
              onClick={() => openEditor(area)}>
              {t('common.edit')}
            </button>
            <button type="button" className="text-xs font-medium text-error hover:underline"
              onClick={() => deleteArea.mutate(area.id, { onError: (e) => alert(apiErrorMessage(e)) })}>
              {t('common.delete')}
            </button>
          </li>
        ))}
        {questionnaire.areas.length === 0 && (
          <li className="px-4 py-4 text-sm text-neutral-400">{t('common.empty')}</li>
        )}
      </ul>

      <Modal
        title={editing === 'new' ? t('questionnaires.addArea') : t('common.edit')}
        open={editing !== null}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
            <Button onClick={handleSave} loading={saveArea.isPending}>{t('common.save')}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField label={t('questionnaires.code')} required value={form.areaCode}
            onChange={(e) => setForm({ ...form, areaCode: e.target.value })} />
          <TextField label={t('questionnaires.name')} required value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <TextField label={t('questionnaires.sortOrder')} type="number" value={form.sortOrder}
            onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} />
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </section>
  );
}

function ItemsCard({ questionnaire }: { questionnaire: QuestionnaireDetail }) {
  const { t } = useTranslation();
  const saveItem = useSaveItem(questionnaire.id);
  const deleteItem = useDeleteItem(questionnaire.id);
  const [editing, setEditing] = useState<Item | null | 'new'>(null);
  const [form, setForm] = useState({
    areaId: '', formId: '', itemLetter: '', itemCode: '', itemText: '',
    responseType: 'LIKERT' as ResponseType, sortOrder: '',
  });
  const [error, setError] = useState<string | null>(null);

  const areaCode = (areaId: number | null) =>
    questionnaire.areas.find((area) => area.id === areaId)?.areaCode ?? '—';
  const formCode = (formId: number | null) =>
    questionnaire.forms.find((f) => f.id === formId)?.formCode ?? null;

  const openEditor = (item: Item | 'new') => {
    setEditing(item);
    setError(null);
    setForm(item === 'new'
      ? {
          areaId: '', formId: '', itemLetter: '', itemCode: '', itemText: '',
          responseType: 'LIKERT', sortOrder: String(questionnaire.items.length + 1),
        }
      : {
          areaId: item.areaId?.toString() ?? '',
          formId: item.formId?.toString() ?? '',
          itemLetter: item.itemLetter ?? '',
          itemCode: item.itemCode,
          itemText: item.itemText,
          responseType: item.responseType,
          sortOrder: item.sortOrder?.toString() ?? '',
        });
  };

  const handleSave = async () => {
    try {
      await saveItem.mutateAsync({
        itemId: editing === 'new' || editing === null ? null : editing.id,
        payload: {
          areaId: form.areaId === '' ? null : Number(form.areaId),
          formId: form.formId === '' ? null : Number(form.formId),
          itemLetter: form.itemLetter === '' ? null : form.itemLetter,
          itemCode: form.itemCode.trim(),
          itemText: form.itemText.trim(),
          responseType: form.responseType,
          sortOrder: form.sortOrder === '' ? null : Number(form.sortOrder),
        },
      });
      setEditing(null);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <section className="rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
        <h2 className="text-sm font-bold uppercase tracking-wide text-blue-600">
          {t('questionnaires.items')} ({questionnaire.items.length})
        </h2>
        <Button variant="ghost" className="!px-2.5 !py-1 text-xs" onClick={() => openEditor('new')}>
          {t('questionnaires.addItem')}
        </Button>
      </header>
      <ul className="max-h-[560px] divide-y divide-neutral-100 overflow-y-auto">
        {questionnaire.items.map((item) => (
          <li key={item.id} className="flex items-start gap-2 px-4 py-2 text-sm">
            <span className="w-10 shrink-0 font-mono text-xs font-bold text-blue-600">{item.itemCode}</span>
            <span className="w-8 shrink-0 rounded bg-blue-50 px-1 text-center font-mono text-[10px] text-blue-700">
              {areaCode(item.areaId)}
            </span>
            {formCode(item.formId) && (
              <span className="w-20 shrink-0 truncate rounded bg-gold-50 px-1 text-center font-mono text-[10px] text-gold-900">
                {formCode(item.formId)}
              </span>
            )}
            <span className="flex-1 text-neutral-800">{item.itemText}</span>
            {item.responseType === 'FREE_TEXT' && <StatusTag tone="neutral">txt</StatusTag>}
            <button type="button" className="text-xs font-medium text-azure-500 hover:underline"
              onClick={() => openEditor(item)}>
              {t('common.edit')}
            </button>
            <button type="button" className="text-xs font-medium text-error hover:underline"
              onClick={() => deleteItem.mutate(item.id, { onError: (e) => alert(apiErrorMessage(e)) })}>
              {t('common.delete')}
            </button>
          </li>
        ))}
        {questionnaire.items.length === 0 && (
          <li className="px-4 py-4 text-sm text-neutral-400">{t('common.empty')}</li>
        )}
      </ul>

      <Modal
        title={editing === 'new' ? t('questionnaires.addItem') : t('common.edit')}
        open={editing !== null}
        onClose={() => setEditing(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
            <Button onClick={handleSave} loading={saveItem.isPending}>{t('common.save')}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label={t('questionnaires.code')} required value={form.itemCode}
              onChange={(e) => setForm({ ...form, itemCode: e.target.value })} />
            <SelectField label={t('questionnaires.area')} value={form.areaId}
              onChange={(e) => setForm({ ...form, areaId: e.target.value })}>
              <option value="">—</option>
              {questionnaire.areas.map((area) => (
                <option key={area.id} value={area.id}>{area.areaCode} — {area.name}</option>
              ))}
            </SelectField>
          </div>
          <TextAreaField label={t('questionnaires.itemText')} required value={form.itemText}
            onChange={(e) => setForm({ ...form, itemText: e.target.value })} />
          <div className="grid gap-3 sm:grid-cols-2">
            <SelectField label={t('questionnaires.form')} value={form.formId}
              onChange={(e) => setForm({ ...form, formId: e.target.value })}>
              <option value="">—</option>
              {questionnaire.forms.map((f) => (
                <option key={f.id} value={f.id}>{f.formCode ?? f.name ?? f.administrationMode}</option>
              ))}
            </SelectField>
            <SelectField label={t('questionnaires.itemLetter')} value={form.itemLetter}
              onChange={(e) => setForm({ ...form, itemLetter: e.target.value })}>
              <option value="">—</option>
              {['a', 'b', 'c', 'd', 'e'].map((letter) => (
                <option key={letter} value={letter}>{letter}</option>
              ))}
            </SelectField>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <SelectField label={t('questionnaires.responseType')} value={form.responseType}
              onChange={(e) => setForm({ ...form, responseType: e.target.value as ResponseType })}>
              <option value="LIKERT">LIKERT</option>
              <option value="FREE_TEXT">FREE_TEXT</option>
            </SelectField>
            <TextField label={t('questionnaires.sortOrder')} type="number" value={form.sortOrder}
              onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} />
          </div>
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </section>
  );
}

function FormsCard({ questionnaire }: { questionnaire: QuestionnaireDetail }) {
  const { t } = useTranslation();
  const addForm = useAddForm(questionnaire.id);
  const deleteForm = useDeleteForm(questionnaire.id);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    formCode: '', administrationMode: 'INDIVIDUAL', stakeholderCategory: '', facilityType: '', name: '', sortOrder: '',
  });
  const [error, setError] = useState<string | null>(null);

  const handleAdd = async () => {
    try {
      await addForm.mutateAsync({
        formCode: form.formCode.trim() || null,
        administrationMode: form.administrationMode as (typeof ADMINISTRATION_MODES)[number],
        stakeholderCategory: form.stakeholderCategory === ''
          ? null : (form.stakeholderCategory as (typeof STAKEHOLDER_CATEGORIES)[number]),
        facilityType: form.facilityType || null,
        name: form.name || null,
        sortOrder: form.sortOrder === '' ? null : Number(form.sortOrder),
      });
      setOpen(false);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <section className="rounded-xl border border-neutral-200 bg-white shadow-sm">
      <header className="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
        <h2 className="text-sm font-bold uppercase tracking-wide text-blue-600">
          {t('questionnaires.forms')} ({questionnaire.forms.length})
        </h2>
        <Button variant="ghost" className="!px-2.5 !py-1 text-xs" onClick={() => { setOpen(true); setError(null); }}>
          {t('questionnaires.addForm')}
        </Button>
      </header>
      <ul className="divide-y divide-neutral-100">
        {questionnaire.forms.map((f) => (
          <li key={f.id} className="flex items-center gap-2 px-4 py-2 text-sm">
            {f.formCode && <span className="font-mono text-xs font-bold text-blue-600">{f.formCode}</span>}
            <span className="flex-1 text-neutral-800">
              {f.name ?? t(`questionnaires.modes.${f.administrationMode}`)}
            </span>
            <StatusTag tone="info">{t(`questionnaires.modes.${f.administrationMode}`)}</StatusTag>
            {f.stakeholderCategory && <StatusTag tone="gold">{t(`facilities.categories.${f.stakeholderCategory}`)}</StatusTag>}
            {f.facilityType && <StatusTag tone="neutral">{f.facilityType}</StatusTag>}
            <button type="button" className="text-xs font-medium text-error hover:underline"
              onClick={() => deleteForm.mutate(f.id, { onError: (e) => alert(apiErrorMessage(e)) })}>
              {t('common.delete')}
            </button>
          </li>
        ))}
        {questionnaire.forms.length === 0 && (
          <li className="px-4 py-4 text-sm text-neutral-400">{t('common.empty')}</li>
        )}
      </ul>

      <Modal
        title={t('questionnaires.addForm')}
        open={open}
        onClose={() => setOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleAdd} loading={addForm.isPending}>{t('common.create')}</Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label={t('questionnaires.formCode')} placeholder="USER" value={form.formCode}
              onChange={(e) => setForm({ ...form, formCode: e.target.value })} />
            <TextField label={t('questionnaires.sortOrder')} type="number" value={form.sortOrder}
              onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} />
          </div>
          <SelectField label={t('questionnaires.administrationMode')} value={form.administrationMode}
            onChange={(e) => setForm({ ...form, administrationMode: e.target.value })}>
            {ADMINISTRATION_MODES.map((mode) => (
              <option key={mode} value={mode}>{t(`questionnaires.modes.${mode}`)}</option>
            ))}
          </SelectField>
          <SelectField label={t('questionnaires.stakeholderCategory')} value={form.stakeholderCategory}
            onChange={(e) => setForm({ ...form, stakeholderCategory: e.target.value })}>
            <option value="">—</option>
            {STAKEHOLDER_CATEGORIES.map((category) => (
              <option key={category} value={category}>{t(`facilities.categories.${category}`)}</option>
            ))}
          </SelectField>
          <SelectField label={t('questionnaires.facilityType')} value={form.facilityType}
            onChange={(e) => setForm({ ...form, facilityType: e.target.value })}>
            <option value="">—</option>
            {['TC', 'GA', 'SH'].map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </SelectField>
          <TextField label={t('questionnaires.name')} value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
        </div>
      </Modal>
    </section>
  );
}
