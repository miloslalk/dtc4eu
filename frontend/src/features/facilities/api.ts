import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../lib/types';

export type FacilityType = 'TC' | 'GA' | 'SH';
export type Activity = 'VISITING_TERNA' | 'QUALITY_AUDIT' | 'TRAINING_PAUSE';
export type MemberCategory = 'USERS' | 'FAMILIARS' | 'PRACTITIONERS' | 'MANAGERS';

export const FACILITY_TYPES: FacilityType[] = ['TC', 'GA', 'SH'];
export const ACTIVITIES: Activity[] = ['VISITING_TERNA', 'QUALITY_AUDIT', 'TRAINING_PAUSE'];
export const MEMBER_CATEGORIES: MemberCategory[] = ['USERS', 'FAMILIARS', 'PRACTITIONERS', 'MANAGERS'];

export interface Facility {
  id: number;
  facilityCode: string;
  facilityType: FacilityType;
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
  country?: string;
  city?: string;
  activity?: string;
  page: number;
}

export interface Participation {
  activity: Activity;
  internalFacilitatorId: number | null;
  internalFacilitatorName: string | null;
}

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
          params: { ...filterParams(filters), page: filters.page, size: 20, sort: 'facilityCode,asc' },
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
    mutationFn: async (payload: { activity: Activity; internalFacilitatorId?: number | null }) =>
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
