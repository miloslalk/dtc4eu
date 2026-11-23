import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { CycleInfo, PageResponse, RoleCode } from '../../lib/types';

export interface UserRow {
  id: number;
  username: string;
  email: string | null;
  fullName: string;
  preferredLang: string;
  active: boolean;
  roles: RoleCode[];
  createdAt: string;
}

export interface UserDetail extends UserRow {
  facilityIds: number[];
  ternaIds: number[];
}

export interface UserPayload {
  username?: string;
  email?: string | null;
  fullName: string;
  preferredLang?: string;
  password?: string;
  /** with a password: temporary, the user must replace it at first login (X4) */
  mustChangePassword?: boolean;
  active?: boolean;
  roles: RoleCode[];
  facilityIds: number[];
  ternaIds: number[];
}

export interface UserFilters {
  q?: string;
  role?: string;
  active?: string;
  page: number;
}

export const ALL_ROLES: RoleCode[] = [
  'SUPER_ADMIN', 'ADMIN', 'RESEARCH_TEAM', 'UPPER_COORDINATOR', 'COORDINATOR', 'AUDIT_COORDINATOR',
  'INTERNAL_FACILITATOR', 'EVALUATOR', 'RESPONDENT', 'TRAINER', 'DTC_REPRESENTATIVE', 'POLICYMAKER',
];

export function useUsers(filters: UserFilters) {
  return useQuery({
    queryKey: ['users', filters],
    queryFn: async () =>
      (
        await api.get<PageResponse<UserRow>>('/users', {
          params: {
            q: filters.q || undefined,
            role: filters.role || undefined,
            active: filters.active || undefined,
            page: filters.page,
            size: 20,
            sort: 'username,asc',
          },
        })
      ).data,
    placeholderData: keepPreviousData,
  });
}

export function useUserDetail(id: number | null) {
  return useQuery({
    queryKey: ['users', 'detail', id],
    queryFn: async () => (await api.get<UserDetail>(`/users/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: number | null; payload: UserPayload }) =>
      id === null
        ? (await api.post<UserDetail>('/users', payload)).data
        : (await api.put<UserDetail>(`/users/${id}`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });
}

/* --- cycles (admin) --- */

export function useCreateCycle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { code: string; startDate?: string; endDate?: string }) =>
      (await api.post<CycleInfo>('/cycles', payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cycles'] }),
  });
}

export function useUpdateCycle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: {
      id: number;
      payload: { startDate?: string; endDate?: string; status?: string; isDefault?: boolean };
    }) =>
      (await api.put<CycleInfo>(`/cycles/${id}`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cycles'] }),
  });
}

/* --- pickers (facility / terna scope) --- */

export interface PickerFacility {
  id: number;
  facilityCode: string;
  name: string;
  /** the selected cycle's Internal Facilitator */
  internalFacilitatorId: number | null;
}

export interface PickerTerna {
  id: number;
  ternaCode: string;
  ternaType: string;
}

export function useFacilityPicker(enabled: boolean) {
  return useQuery({
    queryKey: ['facilities', 'picker'],
    queryFn: async () =>
      (await api.get<PageResponse<PickerFacility>>('/facilities', { params: { size: 200, sort: 'facilityCode,asc' } }))
        .data.content,
    enabled,
  });
}

export function useTernaPicker(enabled: boolean) {
  return useQuery({
    queryKey: ['ternas', 'picker'],
    queryFn: async () =>
      (await api.get<PageResponse<PickerTerna>>('/ternas', { params: { size: 200, sort: 'ternaCode,asc' } })).data
        .content,
    enabled,
  });
}
