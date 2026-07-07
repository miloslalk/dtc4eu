import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Modal } from '../../components/Modal';
import { Button } from '../../components/Button';
import { SelectField, TextAreaField, TextField } from '../../components/fields';
import { apiErrorMessage } from '../../lib/api';
import { FACILITY_TYPES, useSaveFacility, type FacilityType } from './api';

const empty = {
  facilityCode: '',
  facilityType: 'TC' as FacilityType,
  name: '',
  provider: '',
  place: '',
  country: '',
  city: '',
  address: '',
};

/** Create-only modal; editing happens on the detail page's Overview tab. */
export function FacilityFormModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const save = useSaveFacility();
  const [form, setForm] = useState(empty);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!form.facilityCode.trim() || !form.name.trim()) {
      setError(`${t('facilities.code')} / ${t('facilities.name')}: ${t('common.required')}`);
      return;
    }
    try {
      const created = await save.mutateAsync({
        id: null,
        payload: {
          facilityCode: form.facilityCode.trim(),
          facilityType: form.facilityType,
          name: form.name.trim(),
          provider: form.provider || null,
          place: form.place || null,
          country: form.country || null,
          city: form.city || null,
          address: form.address || null,
        },
      });
      setForm(empty);
      setError(null);
      onClose();
      navigate(`/facilities/${created.id}`);
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal
      title={t('facilities.new')}
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleSubmit} loading={save.isPending}>{t('common.create')}</Button>
        </>
      }
    >
      <div className="max-h-[65vh] space-y-4 overflow-y-auto pr-1">
        <TextField label={t('facilities.code')} required value={form.facilityCode}
          onChange={(e) => setForm({ ...form, facilityCode: e.target.value })} />
        <SelectField label={t('facilities.type')} required value={form.facilityType}
          onChange={(e) => setForm({ ...form, facilityType: e.target.value as FacilityType })}>
          {FACILITY_TYPES.map((type) => (
            <option key={type} value={type}>{type} — {t(`facilities.types.${type}`)}</option>
          ))}
        </SelectField>
        <TextField label={t('facilities.name')} required value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <TextField label={t('facilities.provider')} value={form.provider}
          onChange={(e) => setForm({ ...form, provider: e.target.value })} />
        <div className="grid grid-cols-2 gap-3">
          <TextField label={t('facilities.country')} value={form.country}
            onChange={(e) => setForm({ ...form, country: e.target.value })} />
          <TextField label={t('facilities.city')} value={form.city}
            onChange={(e) => setForm({ ...form, city: e.target.value })} />
        </div>
        <TextField label={t('facilities.place')} value={form.place}
          onChange={(e) => setForm({ ...form, place: e.target.value })} />
        <TextAreaField label={t('facilities.address')} value={form.address}
          onChange={(e) => setForm({ ...form, address: e.target.value })} />
        {error && <p className="rounded-lg bg-error-surface px-3 py-2 text-sm font-medium text-error">{error}</p>}
      </div>
    </Modal>
  );
}
