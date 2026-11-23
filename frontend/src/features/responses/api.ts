import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../lib/types';
import type { AdministrationMode } from '../questionnaires/api';

export type RespondentCategory =
  | 'USER' | 'FAMILY' | 'STAFF' | 'MANAGER' | 'LONG_FORM' | 'DELEGATION';
export type ResponseStatus = 'DRAFT' | 'SUBMITTED';

export const RESPONDENT_CATEGORIES: RespondentCategory[] = [
  'USER', 'FAMILY', 'STAFF', 'MANAGER', 'LONG_FORM', 'DELEGATION',
];

export interface ResponseRow {
  id: number;
  questionnaireId: number;
  questionnaireCode: string;
  facilityId: number | null;
  facilityCode: string | null;
  subjectCode: string;
  respondentCategory: RespondentCategory;
  status: ResponseStatus;
  submittedAt: string | null;
  createdAt: string;
}

export interface ResponseItem {
  itemId: number;
  numericValue: number | null;
  textValue: string | null;
}

export interface ResponseDetail {
  id: number;
  questionnaireId: number;
  questionnaireCode: string;
  formId: number | null;
  facilityId: number | null;
  facilityCode: string | null;
  subjectCode: string;
  respondentCategory: RespondentCategory;
  administrationMode: AdministrationMode | null;
  status: ResponseStatus;
  submittedAt: string | null;
  profileSnapshot: Record<string, unknown> | null;
  items: ResponseItem[];
  answeredLikert: number;
  totalLikert: number;
}

export interface AreaScore {
  areaId: number;
  areaCode: string;
  areaName: string;
  meanValue: string;
  computedAt: string;
}

export interface ResponseFilters {
  facilityId?: string;
  questionnaireId?: string;
  category?: string;
  status?: string;
  page: number;
}

export function useResponses(filters: ResponseFilters) {
  return useQuery({
    queryKey: ['responses', filters],
    queryFn: async () =>
      (
        await api.get<PageResponse<ResponseRow>>('/responses', {
          params: {
            facilityId: filters.facilityId || undefined,
            questionnaireId: filters.questionnaireId || undefined,
            category: filters.category || undefined,
            status: filters.status || undefined,
            page: filters.page,
            size: 20,
            sort: 'id,desc',
          },
        })
      ).data,
    placeholderData: keepPreviousData,
  });
}

export function useResponse(id: number) {
  return useQuery({
    queryKey: ['responses', 'detail', id],
    queryFn: async () => (await api.get<ResponseDetail>(`/responses/${id}`)).data,
  });
}

export function useCreateResponse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      questionnaireId: number;
      formId?: number | null;
      facilityId?: number | null;
      subjectCode: string;
      respondentCategory?: RespondentCategory;
    }) => (await api.post<ResponseDetail>('/responses', payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['responses'] }),
  });
}

/** Header update — used by the profile step to persist the anagraphic snapshot. */
export function useUpdateResponse(responseId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      subjectCode: string;
      facilityId?: number | null;
      profileSnapshot?: Record<string, unknown>;
    }) => (await api.put<ResponseDetail>(`/responses/${responseId}`, payload)).data,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['responses', 'detail', responseId] }),
  });
}

export function useSaveItems(responseId: number) {
  return useMutation({
    mutationFn: async (items: ResponseItem[]) =>
      (
        await api.put<{ answeredLikert: number; totalLikert: number; status: ResponseStatus }>(
          `/responses/${responseId}/items`,
          { items },
        )
      ).data,
  });
}

export function useSubmitResponse(responseId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => (await api.post<AreaScore[]>(`/responses/${responseId}/submit`)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['responses'] });
      queryClient.invalidateQueries({ queryKey: ['responses', 'detail', responseId] });
      queryClient.invalidateQueries({ queryKey: ['responses', responseId, 'scores'] });
    },
  });
}

export function useScores(responseId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['responses', responseId, 'scores'],
    queryFn: async () => (await api.get<AreaScore[]>(`/responses/${responseId}/scores`)).data,
    enabled,
  });
}

/* ------------------------- paper import (spec COL-06) ------------------------- */

export type ImportRowOutcome = 'VALID' | 'INVALID' | 'SUBMITTED' | 'DRAFT';

export interface ImportRow {
  row: number;
  subjectCode: string | null;
  answered: number;
  expected: number;
  errors: string[];
  warnings: string[];
  outcome: ImportRowOutcome;
  responseId: number | null;
}

export interface ImportReport {
  facilityCode: string;
  formCode: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  committed: boolean;
  submitted: number;
  drafts: number;
  fileErrors: string[];
  rows: ImportRow[];
}

export interface ImportTarget {
  questionnaireId: number;
  formId: number;
  facilityId: number;
}

/** Validates (commit=false) or imports (commit=true) a filled XLSX template. */
export function useImportResponses() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ target, file, commit }: { target: ImportTarget; file: File; commit: boolean }) => {
      const body = new FormData();
      body.append('file', file);
      return (await api.post<ImportReport>('/responses/import', body, {
        params: { ...target, commit },
        headers: { 'Content-Type': 'multipart/form-data' },
      })).data;
    },
    onSuccess: (report) => {
      if (report.committed) queryClient.invalidateQueries({ queryKey: ['responses'] });
    },
  });
}
