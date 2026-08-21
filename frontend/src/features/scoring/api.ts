import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

/** Effectiveness classification per the v0.2 register §3.3 (TAB.5). */
export type Classification = 'CRITICAL' | 'INTERMEDIATE' | 'EFFICACY';

export interface ItemScoreRow {
  areaId: number;
  areaCode: string | null;
  areaNo: number | null;
  itemLetter: string;
  coordinate: string;
  userMean: string | null;
  familyMean: string | null;
  staffMean: string | null;
  managerMean: string | null;
  averI: string | null;
  comValue: string | null;
  delValue: string | null;
  finalScore: string | null;
  classification: Classification | null;
}

export interface AreaScoreRow {
  areaId: number;
  areaCode: string | null;
  areaName: string | null;
  areaNo: number | null;
  userMean: string | null;
  familyMean: string | null;
  staffMean: string | null;
  managerMean: string | null;
  averI: string | null;
  comValue: string | null;
  delValue: string | null;
  finalScore: string | null;
  classification: Classification | null;
}

export interface FacilityScores {
  facilityId: number;
  facilityCode: string;
  questionnaireId: number;
  questionnaireCode: string;
  computedAt: string | null;
  overallScore: string | null;
  overallClassification: Classification | null;
  areas: AreaScoreRow[];
  items: ItemScoreRow[];
  strengthPoints: string[];
  weaknessPoints: string[];
  strengthAreas: string[];
  weaknessAreas: string[];
}

export interface SummaryTable {
  qualWeaknessAreas: string[] | null;
  qualWeaknessPoints: string[] | null;
  quantWeaknessAreas: string[];
  quantWeaknessPoints: string[];
  qualStrengthAreas: string[] | null;
  qualStrengthPoints: string[] | null;
  quantStrengthAreas: string[];
  quantStrengthPoints: string[];
  benchmarkAreas: string[];
  excellencePoints: string[];
  epBench: string[];
}

export interface TernaBenchmarkRow {
  areaNo: number | null;
  areaCode: string | null;
  areaName: string | null;
  bestFacilityId: number | null;
  bestFacilityCode: string | null;
  bestScore: string | null;
  classification: Classification | null;
}

export interface PopulationCell {
  collected: number;
  roster: number;
}

export interface PopulationRow {
  facilityId: number | null;
  facilityCode: string | null;
  byCategory: Record<string, PopulationCell>;
  collectedTotal: number;
  rosterTotal: number;
}

export interface TernaPopulation {
  ternaId: number;
  ternaCode: string;
  facilities: PopulationRow[];
  totals: PopulationRow;
}

export function useFacilityScores(facilityId: number, questionnaireId: number | null) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'scores', questionnaireId],
    queryFn: async () =>
      (await api.get<FacilityScores>(`/facilities/${facilityId}/scores`, { params: { questionnaireId } })).data,
    enabled: facilityId > 0 && questionnaireId !== null,
  });
}

export function useScoreSummary(facilityId: number, questionnaireId: number | null) {
  return useQuery({
    queryKey: ['facilities', facilityId, 'scores-summary', questionnaireId],
    queryFn: async () =>
      (await api.get<SummaryTable>(`/facilities/${facilityId}/scores/summary`, { params: { questionnaireId } })).data,
    enabled: facilityId > 0 && questionnaireId !== null,
  });
}

export function useRecomputeScores(facilityId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (questionnaireId: number) =>
      (await api.post<FacilityScores>(`/facilities/${facilityId}/scores/recompute`, null,
        { params: { questionnaireId } })).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'scores'] });
      queryClient.invalidateQueries({ queryKey: ['facilities', facilityId, 'scores-summary'] });
    },
  });
}

export function useTernaBenchmarks(ternaId: number, questionnaireId: number | null) {
  return useQuery({
    queryKey: ['ternas', ternaId, 'benchmarks', questionnaireId],
    queryFn: async () =>
      (await api.get<TernaBenchmarkRow[]>(`/ternas/${ternaId}/analytics/benchmarks`,
        { params: { questionnaireId } })).data,
    enabled: ternaId > 0 && questionnaireId !== null,
  });
}

export function useTernaPopulation(ternaId: number, questionnaireId: number | null) {
  return useQuery({
    queryKey: ['ternas', ternaId, 'population', questionnaireId],
    queryFn: async () =>
      (await api.get<TernaPopulation>(`/ternas/${ternaId}/analytics/population`,
        { params: { questionnaireId: questionnaireId ?? undefined } })).data,
    enabled: ternaId > 0,
  });
}
