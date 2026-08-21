import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { PageResponse } from '../../lib/types';

export type AuditType = 'SIMPLE' | 'BQ' | 'CGQ';
export const AUDIT_TYPES: AuditType[] = ['SIMPLE', 'BQ', 'CGQ'];

export interface AuditPerson {
  id: number;
  fullName: string | null;
}

export interface AccreditedArea {
  areaId: number;
  areaCode: string;
  name: string;
}

export interface AccreditedItem {
  itemId: number;
  itemCode: string;
  label: string;
  excellencePoints: string | null;
}

export interface Audit {
  id: number;
  auditCode: string;
  auditType: AuditType;
  renewalNumber: number | null;
  facilityId: number;
  facilityCode: string | null;
  facilityName: string | null;
  coordinators: AuditPerson[];
  evaluators: AuditPerson[];
  accreditedProcesses: string[];
  accreditedAreas: AccreditedArea[];
  accreditedItems: AccreditedItem[];
}

export interface SaveAudit {
  auditCode: string;
  facilityId: number;
  auditType: AuditType;
  renewalNumber: number | null;
  coordinatorIds: number[];
  evaluatorIds: number[];
}

export function useAudits() {
  return useQuery({
    queryKey: ['audits'],
    queryFn: async () => (await api.get<Audit[]>('/audits')).data,
  });
}

export function useAuditMutations() {
  const queryClient = useQueryClient();
  const done = () => queryClient.invalidateQueries({ queryKey: ['audits'] });
  return {
    create: useMutation({
      mutationFn: async (body: SaveAudit) => (await api.post<Audit>('/audits', body)).data,
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: async ({ id, body }: { id: number; body: SaveAudit }) =>
        (await api.put<Audit>(`/audits/${id}`, body)).data,
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => api.delete(`/audits/${id}`),
      onSuccess: done,
    }),
    saveOutcomes: useMutation({
      mutationFn: async ({ id, processCodes, areaIds, items }: {
        id: number;
        processCodes: string[];
        areaIds: number[];
        items: { itemId: number; excellencePoints: string | null }[];
      }) => (await api.put<Audit>(`/audits/${id}/outcomes`, { processCodes, areaIds, items })).data,
      onSuccess: done,
    }),
  };
}

/** Users holding a given role (admin-only endpoint). */
export function useRolePicker(role: string, enabled: boolean) {
  return useQuery({
    queryKey: ['users', 'role-picker', role],
    queryFn: async () =>
      (
        await api.get<PageResponse<{ id: number; fullName: string; username: string }>>('/users', {
          params: { role, size: 200, sort: 'fullName,asc' },
        })
      ).data.content,
    enabled,
  });
}
