import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

/** Profile/context fields an assignment rule can test (spec COL-01; mirrors RuleConditions.Field). */
export const RULE_FIELDS = [
  'CATEGORY', 'AGE', 'COUNTRY', 'PROJECT_PHASE', 'FACILITY_SETTING', 'FACILITY_SUBTYPE', 'TERNA_TYPE', 'CYCLE', 'ROLE',
] as const;
export type RuleField = (typeof RULE_FIELDS)[number];
export type RuleOperator = 'IN' | 'NOT_IN' | 'GTE' | 'LTE';

/** Fixed vocabularies offered as pick-lists; the other fields are free text. */
export const FIELD_OPTIONS: Partial<Record<RuleField, string[]>> = {
  CATEGORY: ['USER', 'FAMILY', 'STAFF', 'MANAGER'],
  PROJECT_PHASE: ['VISITING_TERNA', 'QUALITY_AUDIT', 'TRAINING_PAUSE'],
  FACILITY_SETTING: ['TC', 'GA', 'SH'],
  TERNA_TYPE: ['TCS', 'GAS', 'SHS'],
};
export const NUMERIC_FIELDS: RuleField[] = ['AGE'];

export interface RuleCondition {
  type: 'condition';
  field: RuleField;
  operator: RuleOperator;
  values: string[];
}

export interface RuleGroup {
  type: 'group';
  op: 'AND' | 'OR';
  children: RuleNode[];
}

export type RuleNode = RuleCondition | RuleGroup;

export interface AssignmentRule {
  id: number;
  name: string;
  description: string | null;
  conditions: RuleGroup | RuleCondition;
  targetQuestionnaireId: number;
  targetFormId: number;
  targetFormCode: string | null;
  targetFormName: string | null;
  priority: number;
  active: boolean;
  validFrom: string | null;
  validTo: string | null;
  updatedAt: string | null;
}

export interface SaveRulePayload {
  name: string;
  description?: string | null;
  conditions: RuleNode;
  targetFormId: number;
  priority: number;
  active: boolean;
  validFrom?: string | null;
  validTo?: string | null;
}

export interface AssignedForm {
  formId: number;
  questionnaireId: number;
  questionnaireCode: string;
  questionnaireName: string;
  formCode: string | null;
  formName: string;
  administrationMode: string | null;
  ruleId: number;
  ruleName: string;
}

export type RuleContext = Partial<Record<RuleField, string[]>>;

export function useAssignmentRules() {
  return useQuery({
    queryKey: ['assignment-rules'],
    queryFn: async () => (await api.get<AssignmentRule[]>('/assignment-rules')).data,
  });
}

export function useSaveRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, payload }: { id: number | null; payload: SaveRulePayload }) =>
      (id === null
        ? await api.post<AssignmentRule>('/assignment-rules', payload)
        : await api.put<AssignmentRule>(`/assignment-rules/${id}`, payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['assignment-rules'] }),
  });
}

export function useDeleteRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => api.delete(`/assignment-rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['assignment-rules'] }),
  });
}

export function useTestRules() {
  return useMutation({
    mutationFn: async (payload: { context: RuleContext; onDate?: string | null }) =>
      (await api.post<{ forms: AssignedForm[] }>('/assignment-rules/test', payload)).data,
  });
}

/* ------------------------------- respondent side ------------------------------- */

export interface FacilityContext {
  facilityId: number;
  facilityCode: string;
  setting: string | null;
  subtype: string | null;
  country: string | null;
  activity: string | null;
}

export interface MyProfile {
  cycleId: number;
  age: number | null;
  roleCategory: string | null;
  ternaId: number | null;
  projectPhase: string | null;
  country: string | null;
  city: string | null;
  updatedAt: string | null;
  facilities: FacilityContext[];
}

export interface SaveProfilePayload {
  age: number | null;
  roleCategory: string | null;
  projectPhase: string | null;
  country: string | null;
  city: string | null;
}

export interface MyTask {
  form: AssignedForm;
  status: 'NOT_STARTED' | 'DRAFT' | 'SUBMITTED';
  responseId: number | null;
}

export interface MyAssignments {
  profileComplete: boolean;
  context: RuleContext;
  tasks: MyTask[];
}

export function useMyProfile() {
  return useQuery({
    queryKey: ['me', 'profile'],
    queryFn: async () => (await api.get<MyProfile>('/me/profile')).data,
  });
}

export function useSaveMyProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: SaveProfilePayload) => (await api.put<MyProfile>('/me/profile', payload)).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useMyAssignments() {
  return useQuery({
    queryKey: ['me', 'assignments'],
    queryFn: async () => (await api.get<MyAssignments>('/me/assignments')).data,
  });
}
