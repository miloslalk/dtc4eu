import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export type ResponseType = 'LIKERT' | 'FREE_TEXT';
export type AdministrationMode = 'INDIVIDUAL' | 'LONG_FORM' | 'DELEGATION';
export type StakeholderCategory = 'USER' | 'FAMILY' | 'STAFF' | 'MANAGER';

export const ADMINISTRATION_MODES: AdministrationMode[] = ['INDIVIDUAL', 'LONG_FORM', 'DELEGATION'];
export const STAKEHOLDER_CATEGORIES: StakeholderCategory[] = ['USER', 'FAMILY', 'STAFF', 'MANAGER'];

export interface QuestionnaireRow {
  id: number;
  code: string;
  name: string;
  scaleMin: number;
  scaleMax: number;
  active: boolean;
  areaCount: number;
  itemCount: number;
  formCount: number;
}

export interface Area {
  id: number;
  areaCode: string;
  name: string;
  sortOrder: number | null;
}

export interface Item {
  id: number;
  areaId: number | null;
  formId: number | null;
  itemLetter: string | null;
  itemCode: string;
  itemText: string;
  responseType: ResponseType;
  sortOrder: number | null;
}

export interface Form {
  id: number;
  formCode: string | null;
  administrationMode: AdministrationMode;
  stakeholderCategory: StakeholderCategory | null;
  facilityType: 'TC' | 'GA' | 'SH' | null;
  name: string | null;
  sortOrder: number | null;
}

export interface Anchor {
  value: number;
  label: string;
  /** False for an answer outside the scale that is recorded but not scored (ViVaCom 8, 9). */
  countsInScore: boolean;
}

export interface FormAreaLabel {
  formId: number;
  areaId: number;
  displayName: string;
}

export interface QuestionnaireDetail extends Omit<QuestionnaireRow, 'areaCount' | 'itemCount' | 'formCount'> {
  areas: Area[];
  items: Item[];
  forms: Form[];
  anchors: Anchor[];
  formAreaLabels: FormAreaLabel[];
  /** TAB.5 bands (V15); null = scores stay unclassified */
  bandCriticalMax: number | null;
  bandEfficacyMin: number | null;
  /** once answered, the scale, bands and items are locked */
  hasResponses: boolean;
}

/* Per-form profile-header schema (v0.2 register §2.4) */

export interface ProfileFieldDef {
  key: string;
  label: string;
  type: 'integer' | 'decimal' | 'text' | 'boolean' | 'enum' | 'group' | 'multiselect' | 'date';
  required?: boolean;
  min?: number;
  max?: number;
  options?: string[];
  multiline?: boolean;
  maxLength?: number;
  repeat?: { mode: 'fixedRows'; rows: { key: string; label: string }[] } | { mode: 'list'; maxRows?: number };
  fields?: ProfileFieldDef[];
  /** filled from another field of the same row, still editable (GAS-Set L/M area from role) */
  derivedFrom?: { field: string; map: Record<string, string>; none?: string[] };
}

export interface ProfileSchema {
  form: string;
  version: number;
  sections: { key: string; label: string; restricted?: boolean; fields: ProfileFieldDef[] }[];
}

export function useProfileSchema(questionnaireId: number, formId: number | null) {
  return useQuery({
    queryKey: ['questionnaires', questionnaireId, 'profile-schema', formId],
    queryFn: async () =>
      (await api.get<ProfileSchema>(`/questionnaires/${questionnaireId}/forms/${formId}/profile-schema`)).data,
    enabled: questionnaireId > 0 && formId !== null,
    retry: false, // 404 simply means the form has no profile step
  });
}

export function useQuestionnaires() {
  return useQuery({
    queryKey: ['questionnaires'],
    queryFn: async () => (await api.get<QuestionnaireRow[]>('/questionnaires')).data,
  });
}

export function useQuestionnaire(id: number) {
  return useQuery({
    queryKey: ['questionnaires', id],
    queryFn: async () => (await api.get<QuestionnaireDetail>(`/questionnaires/${id}`)).data,
    enabled: id > 0,
  });
}

function useInvalidate(id?: number) {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['questionnaires'] });
    if (id !== undefined) {
      queryClient.invalidateQueries({ queryKey: ['questionnaires', id] });
    }
  };
}

export function useCreateQuestionnaire() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: async (payload: { code: string; name: string; scaleMin: number; scaleMax: number }) =>
      (await api.post<QuestionnaireDetail>('/questionnaires', payload)).data,
    onSuccess: invalidate,
  });
}

export function useUpdateQuestionnaire(id: number) {
  const invalidate = useInvalidate(id);
  return useMutation({
    mutationFn: async (payload: {
      name: string; scaleMin?: number; scaleMax?: number; active?: boolean;
      bandCriticalMax?: number | null; bandEfficacyMin?: number | null;
    }) =>
      (await api.put<QuestionnaireDetail>(`/questionnaires/${id}`, payload)).data,
    onSuccess: invalidate,
  });
}

export function useSaveArea(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async ({ areaId, payload }: {
      areaId: number | null;
      payload: { areaCode: string; name: string; sortOrder: number | null };
    }) =>
      areaId === null
        ? (await api.post(`/questionnaires/${questionnaireId}/areas`, payload)).data
        : (await api.put(`/questionnaires/${questionnaireId}/areas/${areaId}`, payload)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteArea(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async (areaId: number) => api.delete(`/questionnaires/${questionnaireId}/areas/${areaId}`),
    onSuccess: invalidate,
  });
}

export function useSaveItem(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async ({ itemId, payload }: {
      itemId: number | null;
      payload: {
        areaId: number | null; formId: number | null; itemLetter: string | null;
        itemCode: string; itemText: string; responseType: ResponseType; sortOrder: number | null;
      };
    }) =>
      itemId === null
        ? (await api.post(`/questionnaires/${questionnaireId}/items`, payload)).data
        : (await api.put(`/questionnaires/${questionnaireId}/items/${itemId}`, payload)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteItem(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async (itemId: number) => api.delete(`/questionnaires/${questionnaireId}/items/${itemId}`),
    onSuccess: invalidate,
  });
}

export function useAddForm(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async (payload: {
      formCode: string | null;
      administrationMode: AdministrationMode;
      stakeholderCategory: StakeholderCategory | null;
      facilityType: string | null;
      name: string | null;
      sortOrder: number | null;
    }) => (await api.post(`/questionnaires/${questionnaireId}/forms`, payload)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteForm(questionnaireId: number) {
  const invalidate = useInvalidate(questionnaireId);
  return useMutation({
    mutationFn: async (formId: number) => api.delete(`/questionnaires/${questionnaireId}/forms/${formId}`),
    onSuccess: invalidate,
  });
}
