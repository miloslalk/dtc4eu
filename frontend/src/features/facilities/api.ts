import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../lib/types';

export type FacilityType = 'TC' | 'GA' | 'SH';
export type FacilitySubtype =
  | 'RTC' | 'STC' | 'DAY_TC'
  | 'DOUBLE_GA' | 'SIMPLE_GA' | 'COUPLE_GA'
  | 'SUPPORTED_APARTMENTS' | 'HOME_CARE';
export type Activity = 'VISITING_TERNA' | 'QUALITY_AUDIT' | 'TRAINING_PAUSE';
export type MemberCategory = 'USER' | 'FAMILY' | 'STAFF' | 'MANAGER';

export const FACILITY_TYPES: FacilityType[] = ['TC', 'GA', 'SH'];
/** v0.2 register §7 taxonomy: 3 settings × 8 types. */
export const SUBTYPES_BY_TYPE: Record<FacilityType, FacilitySubtype[]> = {
  TC: ['RTC', 'STC', 'DAY_TC'],
  GA: ['DOUBLE_GA', 'SIMPLE_GA', 'COUPLE_GA'],
  SH: ['SUPPORTED_APARTMENTS', 'HOME_CARE'],
};
export const FACILITY_SUBTYPES: FacilitySubtype[] = FACILITY_TYPES.flatMap((t) => SUBTYPES_BY_TYPE[t]);
export const ACTIVITIES: Activity[] = ['VISITING_TERNA', 'QUALITY_AUDIT', 'TRAINING_PAUSE'];
export const MEMBER_CATEGORIES: MemberCategory[] = ['USER', 'FAMILY', 'STAFF', 'MANAGER'];

export interface Facility {
  id: number;
  facilityCode: string;
  facilityType: FacilityType;
  facilitySubtype: FacilitySubtype | null;
  name: string;
  provider: string | null;
  place: string | null;
  country: string | null;
  city: string | null;
  address: string | null;
  active: boolean;
  activity: Activity | null;
  internalFacilitatorId: number | null;
  internalFacilitatorName: string | null;
}

export interface FacilityPayload {
  facilityCode?: string;
  facilityType: FacilityType;
  facilitySubtype?: FacilitySubtype | null;
  name: string;
  provider?: string | null;
  place?: string | null;
  country?: string | null;
  city?: string | null;
  address?: string | null;
  active?: boolean;
}

export interface FacilityFilters {
  q?: string;
  type?: string;
  subtype?: string;
  country?: string;
  city?: string;
  activity?: string;
  page: number;
  /** pickers pass a large size; the list keeps the default 20 */
  size?: number;
}

export interface Participation {
  activity: Activity;
  internalFacilitatorId: number | null;
  internalFacilitatorName: string | null;
  sagDone: boolean | null;
  qcncDone: boolean | null;
  dtcroLongFormDone: boolean | null;
  foundationMeetingDone: boolean | null;
  finalForumDone: boolean | null;
}

/** The expert-feedback OK/NO activity checklist keys, in display order. */
export const PARTICIPATION_CHECKLIST = [
  'sagDone', 'qcncDone', 'dtcroLongFormDone', 'foundationMeetingDone', 'finalForumDone',
] as const;
export type ChecklistKey = (typeof PARTICIPATION_CHECKLIST)[number];

export interface Member {
  id: number;
  memberCode: string;
  category: MemberCategory;
}

export interface Sag {
  id: number;
  gridVariant: string;
  compiledBy: number | null;
  payload: Record<string, unknown> | null;
}

export interface Qcnc {
  id: number;
  payload: Record<string, unknown> | null;
}

function filterParams(filters: FacilityFilters) {
  return {
    q: filters.q || undefined,
    type: filters.type || undefined,
    subtype: filters.subtype || undefined,
    country: filters.country || undefined,
    city: filters.city || undefined,
    activity: filters.activity || undefined,
  };
}

export function useFacilities(filters: FacilityFilters) {
  return useQuery({
    queryKey: ['facilities', filters],
    queryFn: async () =>
      (
        await api.get<PageResponse<Facility>>('/facilities', {
          params: { ...filterParams(filters), page: filters.page, size: filters.size ?? 20, sort: 'facilityCode,asc' },
        })
      ).data,
    placeholderData: keepPreviousData,
  });
}

export function useFacility(id: number) {
  return useQuery({
    queryKey: ['facilities', 'detail', id],
    queryFn: async () => (await api.get<Facility>(`/facilities/${id}`)).data,
  });
}

export function useSaveFacility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: number | null; payload: FacilityPayload }) =>
      id === null
        ? (await api.post<Facility>('/facilities', payload)).data
        : (await api.put<Facility>(`/facilities/${id}`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities'] }),
  });
}

/** 404 means "not recorded for this cycle yet" → null. */
async function nullOn404<T>(request: Promise<{ data: T }>): Promise<T | null> {
  try {
    return (await request).data;
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 404) return null;
    throw error;
  }
}

export function useParticipation(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'participation'],
    queryFn: () => nullOn404(api.get<Participation>(`/facilities/${facilityId}/participation`)),
  });
}

export function useSetParticipation(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      activity: Activity;
      internalFacilitatorId?: number | null;
      sagDone?: boolean | null;
      qcncDone?: boolean | null;
      dtcroLongFormDone?: boolean | null;
      foundationMeetingDone?: boolean | null;
      finalForumDone?: boolean | null;
    }) =>
      (await api.post<Participation>(`/facilities/${facilityId}/participation`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities'] }),
  });
}

export function useMembers(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'members'],
    queryFn: async () => (await api.get<Member[]>(`/facilities/${facilityId}/members`)).data,
  });
}

export function useAddMember(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { memberCode: string; category: MemberCategory }) =>
      (await api.post<Member>(`/facilities/${facilityId}/members`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'members'] }),
  });
}

export function useRemoveMember(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (memberId: number) => api.delete(`/facilities/${facilityId}/members/${memberId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'members'] }),
  });
}

export function useSagSchema(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'sag-schema'],
    queryFn: async () =>
      (await api.get<import('../questionnaires/api').ProfileSchema>(`/facilities/${facilityId}/set-analysis-grid/schema`)).data,
  });
}

export function useSags(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'sag'],
    queryFn: async () => (await api.get<Sag[]>(`/facilities/${facilityId}/set-analysis-grid`)).data,
  });
}

export function usePutSag(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { gridVariant: string; payload: Record<string, unknown> }) =>
      (await api.put<Sag>(`/facilities/${facilityId}/set-analysis-grid`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'sag'] }),
  });
}

export type QcncAdministrationType =
  | 'INTERNAL_FACILITATOR' | 'TERNA_COORDINATOR_OWN' | 'TERNA_COORDINATOR_DELEGATION';
export const QCNC_ADMINISTRATIONS: QcncAdministrationType[] =
  ['INTERNAL_FACILITATOR', 'TERNA_COORDINATOR_OWN', 'TERNA_COORDINATOR_DELEGATION'];

export interface QcncStandard {
  id: number;
  clusterCode: 'Q1' | 'Q2' | 'Q3' | 'Q4' | 'Q5';
  letterCode: string;
  standardType: 'ORGANIZATIONAL_STRUCTURAL' | 'COMMUNITY_FUNCTIONAL';
  mandatory: boolean;
  settingParameters: Record<string, string> | null;
  description: string | null;
  sortOrder: number | null;
}

export interface QcncAdministration {
  administrationType: QcncAdministrationType;
  administeredById: number | null;
  administeredByName: string | null;
  updatedAt: string | null;
  conformityByStandard: Record<number, 'C' | 'NC'>;
  scoreByStandard: Record<number, number>;
}

/** Mean of the administrations' 1–4 scores for one standard, classified per TAB.5 (expert feedback). */
export interface QcncStandardScore {
  standardId: number;
  meanScore: number;
  classification: 'CRITICAL' | 'INTERMEDIATE' | 'EFFICACY' | null;
}

export interface QcncBoard {
  standards: QcncStandard[];
  administrations: QcncAdministration[];
  standardScores: QcncStandardScore[];
  strengthStandardIds: number[];
  weaknessStandardIds: number[];
}

export function useQcncBoard(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'qcnc-board'],
    queryFn: async () => (await api.get<QcncBoard>(`/facilities/${facilityId}/qcnc-standards`)).data,
  });
}

export function useSaveQcncResults(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ administrationType, results }: {
      administrationType: QcncAdministrationType;
      results: { standardId: number; conformity: 'C' | 'NC'; score?: number | null }[];
    }) =>
      (await api.put<QcncBoard>(`/facilities/${facilityId}/qcnc-standards/${administrationType}`, { results })).data,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'qcnc-board'] }),
  });
}

export function useQcnc(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'qcnc'],
    queryFn: () => nullOn404(api.get<Qcnc>(`/facilities/${facilityId}/qcnc`)),
  });
}

/** Users holding the INTERNAL_FACILITATOR role (admin-only endpoint — enable only for admins). */
export function useFacilitatorPicker(enabled: boolean) {
  return useQuery({
    queryKey: ['users', 'facilitator-picker'],
    queryFn: async () =>
      (
        await api.get<PageResponse<{ id: number; fullName: string; username: string }>>('/users', {
          params: { role: 'INTERNAL_FACILITATOR', size: 200, sort: 'fullName,asc' },
        })
      ).data.content,
    enabled,
  });
}

export function usePutQcnc(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { payload: Record<string, unknown> }) =>
      (await api.put<Qcnc>(`/facilities/${facilityId}/qcnc`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'qcnc'] }),
  });
}

/* -------- quality documents + accreditation history (v0.2 register §6) -------- */

export type StandardType = 'GPP' | 'CGS' | 'EEN';
export const STANDARD_TYPES: StandardType[] = ['GPP', 'CGS', 'EEN'];

export interface DocumentApplication {
  id: number;
  standardType: StandardType;
  standardCode: string | null;
  serviceName: string | null;
  serviceType: string | null;
  numUsers: number | null;
  numStaff: number | null;
  origin: 'DEVELOPED' | 'IMPLEMENTED' | null;
  applicationCyclesCount: number | null;
  applicationLevelPct: string | null;
  assessedBy: number | null;
  assessedByName: string | null;
  assessedAt: string | null;
}

export interface ManualEp {
  id: number;
  cycleId: number;
  questionnaireCode: string;
  label: string;
}

export interface Effectiveness {
  questionnaireItemId: number | null;
  itemCode: string | null;
  individualEffectiveness: string | null;
  meanItemEffectiveness: string | null;
  meanAreaEffectiveness: string | null;
  meanQuestEffectiveness: string | null;
  areasWithScore: number | null;
  areasTotal: number | null;
  computedAt: string | null;
}

export interface DocumentApplicationPayload {
  standardType: StandardType;
  serviceType?: string | null;
  numUsers?: number | null;
  numStaff?: number | null;
  origin?: 'DEVELOPED' | 'IMPLEMENTED' | null;
  applicationCyclesCount?: number | null;
  standardCode?: string | null;
  serviceName?: string | null;
  applicationLevelPct?: number | null;
}

export interface HistoryCycle {
  cycleId: number;
  cycleCode: string;
  sequenceNumber: number | null;
  activity: string | null;
  excellencePointsByQuestionnaire: Record<string, string[]>;
  documentApplications: DocumentApplication[];
}

export interface FacilityHistory {
  facilityId: number;
  facilityCode: string;
  cyclesInProgramme: number;
  cycles: HistoryCycle[];
}

export function useManualEps(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'manual-eps'],
    queryFn: async () => (await api.get<ManualEp[]>(`/facilities/${facilityId}/manual-excellence-points`)).data,
  });
}

export function usePutManualEps(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: { cycleId: number; questionnaireCode: string; labels: string[] }) =>
      (await api.put<ManualEp[]>(`/facilities/${facilityId}/manual-excellence-points`, body)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId] }),
  });
}

export function useEffectiveness(facilityId: number, applicationId: number | null) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'effectiveness', applicationId],
    queryFn: async () =>
      (await api.get<Effectiveness | null>(`/facilities/${facilityId}/document-applications/${applicationId}/effectiveness`)).data,
    enabled: applicationId !== null,
  });
}

export function useLinkEffectiveness(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ applicationId, questionnaireItemId }: { applicationId: number; questionnaireItemId: number }) =>
      (await api.put<Effectiveness>(`/facilities/${facilityId}/document-applications/${applicationId}/effectiveness`,
        { questionnaireItemId })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'effectiveness'] }),
  });
}

export function useDocumentApplications(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'document-applications'],
    queryFn: async () =>
      (await api.get<DocumentApplication[]>(`/facilities/${facilityId}/document-applications`)).data,
  });
}

export function useSaveDocumentApplication(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: number | null; payload: DocumentApplicationPayload }) =>
      id === null
        ? (await api.post<DocumentApplication>(`/facilities/${facilityId}/document-applications`, payload)).data
        : (await api.put<DocumentApplication>(`/facilities/${facilityId}/document-applications/${id}`, payload)).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'document-applications'] });
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'history'] });
    },
  });
}

export function useDeleteDocumentApplication(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => api.delete(`/facilities/${facilityId}/document-applications/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'document-applications'] });
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'history'] });
    },
  });
}

export function useFacilityHistory(facilityId: number) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'history'],
    queryFn: async () => (await api.get<FacilityHistory>(`/facilities/${facilityId}/history`)).data,
  });
}
