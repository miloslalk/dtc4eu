import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';

/** Mirrors ProgressDtos (Simone's feedback #5: task view per role, terna matrix). */
export type ProgressTask =
  | 'GAS_SET' | 'QCNC_INTERNAL_FACILITATOR' | 'DTCRO_LONG_FORM'
  | 'QCNC_TERNA_COORDINATOR_OWN' | 'QCNC_TERNA_COORDINATOR_DELEGATION' | 'DTCRO_DELEGATION'
  | 'QUALITY_DOCUMENTS' | 'PARTICIPATION_CHECKLIST';

export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE';

export interface FacilityProgress {
  facilityId: number;
  facilityCode: string;
  facilityName: string;
  ternaId: number | null;
  ternaCode: string | null;
  internalFacilitatorName: string | null;
  tasks: { task: ProgressTask; status: TaskStatus; count: number }[];
}

export function useMyProgress(enabled = true) {
  return useQuery({
    queryKey: ['progress', 'me'],
    queryFn: async () => (await api.get<FacilityProgress[]>('/progress/me')).data,
    enabled,
  });
}

export function useTernaProgress(ternaId: number | null) {
  return useQuery({
    queryKey: ['progress', 'ternas', ternaId],
    queryFn: async () => (await api.get<FacilityProgress[]>(`/progress/ternas/${ternaId}`)).data,
    enabled: ternaId !== null,
  });
}

/** Where each task is completed: a facility tab, or the responses list. */
export function taskLink(row: FacilityProgress, task: ProgressTask): string {
  switch (task) {
    case 'GAS_SET':
      return `/facilities/${row.facilityId}?tab=sag`;
    case 'QCNC_INTERNAL_FACILITATOR':
    case 'QCNC_TERNA_COORDINATOR_OWN':
    case 'QCNC_TERNA_COORDINATOR_DELEGATION':
      return `/facilities/${row.facilityId}?tab=qcnc`;
    case 'DTCRO_LONG_FORM':
    case 'DTCRO_DELEGATION':
      return '/responses';
    default:
      return `/facilities/${row.facilityId}?tab=participation`;
  }
}
