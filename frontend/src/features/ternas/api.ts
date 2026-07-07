import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../lib/types';

export type TernaType = 'TCS' | 'GAS' | 'SHS';
export const TERNA_TYPES: TernaType[] = ['TCS', 'GAS', 'SHS'];

export interface TernaRow {
  id: number;
  ternaCode: string;
  ternaType: TernaType;
  foundingMeetingDate: string | null;
  finalForumDate: string | null;
  facilityCodes: string[];
  coordinatorNames: string[];
}

export interface TernaFacility {
  facilityId: number;
  facilityCode: string;
  facilityName: string;
  internalFacilitatorId: number | null;
  internalFacilitatorName: string | null;
  position: number | null;
}

export interface VisitDate {
  visitOrder: number;
  visitDate: string;
}

export interface Terna {
  id: number;
  ternaCode: string;
  ternaType: TernaType;
  foundingMeetingDate: string | null;
  finalForumDate: string | null;
  facilities: TernaFacility[];
  coordinators: { userId: number; fullName: string }[];
  visitDates: VisitDate[];
}

export interface SaveTernaPayload {
  ternaCode: string;
  ternaType: TernaType;
  facilityIds: number[];
  coordinatorIds: number[];
  foundingMeetingDate?: string | null;
  finalForumDate?: string | null;
  visitDates: VisitDate[];
}

export interface TernaFilters {
  type?: string;
  facilityId?: string;
  dateFrom?: string;
  dateTo?: string;
  page: number;
}

export function useTernas(filters: TernaFilters) {
  return useQuery({
    queryKey: ['ternas', filters],
    queryFn: async () =>
      (
        await api.get<PageResponse<TernaRow>>('/ternas', {
          params: {
            type: filters.type || undefined,
            facilityId: filters.facilityId || undefined,
            dateFrom: filters.dateFrom || undefined,
            dateTo: filters.dateTo || undefined,
            page: filters.page,
            size: 20,
            sort: 'ternaCode,asc',
          },
        })
      ).data,
    placeholderData: keepPreviousData,
  });
}

export function useTerna(id: number | null) {
  return useQuery({
    queryKey: ['ternas', 'detail', id],
    queryFn: async () => (await api.get<Terna>(`/ternas/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveTerna() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: number | null; payload: SaveTernaPayload }) =>
      id === null
        ? (await api.post<Terna>('/ternas', payload)).data
        : (await api.put<Terna>(`/ternas/${id}`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ternas'] }),
  });
}

export interface CoordinatorOption {
  id: number;
  username: string;
  fullName: string;
}

export function useCoordinatorOptions() {
  return useQuery({
    queryKey: ['users', 'coordinators'],
    queryFn: async () => (await api.get<CoordinatorOption[]>('/users/coordinators')).data,
  });
}
