import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export type ResponseType = 'LIKERT' | 'FREE_TEXT';
export type AdministrationMode = 'INDIVIDUAL' | 'SELFEVAL_COLLECTIVE' | 'HETEROEVAL_DELEGATION';
export type StakeholderCategory = 'USERS' | 'FAMILIARS' | 'PRACTITIONERS' | 'MANAGERS';

export const ADMINISTRATION_MODES: AdministrationMode[] = [
  'INDIVIDUAL', 'SELFEVAL_COLLECTIVE', 'HETEROEVAL_DELEGATION',
];
export const STAKEHOLDER_CATEGORIES: StakeholderCategory[] = ['USERS', 'FAMILIARS', 'PRACTITIONERS', 'MANAGERS'];

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
  itemCode: string;
  itemText: string;
  responseType: ResponseType;
  sortOrder: number | null;
}

export interface Form {
  id: number;
  administrationMode: AdministrationMode;
  stakeholderCategory: StakeholderCategory | null;
  facilityType: 'TC' | 'GA' | 'SH' | null;
  name: string | null;
}

export interface QuestionnaireDetail extends Omit<QuestionnaireRow, 'areaCount' | 'itemCount' | 'formCount'> {
  areas: Area[];
  items: Item[];
  forms: Form[];
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
    mutationFn: async (payload: { name: string; scaleMin?: number; scaleMax?: number; active?: boolean }) =>
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
      payload: { areaId: number | null; itemCode: string; itemText: string; responseType: ResponseType; sortOrder: number | null };
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
      administrationMode: AdministrationMode;
      stakeholderCategory: StakeholderCategory | null;
      facilityType: string | null;
      name: string | null;
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
