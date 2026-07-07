// Shared API types mirroring the backend DTOs.

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: { field: string; message: string }[];
}

export type RoleCode =
  | 'SUPER_ADMIN'
  | 'ADMIN'
  | 'RESEARCH_TEAM'
  | 'COORDINATOR'
  | 'INTERNAL_FACILITATOR'
  | 'EVALUATOR'
  | 'RESPONDENT'
  | 'TRAINER'
  | 'DTC_REPRESENTATIVE'
  | 'POLICYMAKER';

export interface UserInfo {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  preferredLang: string;
  roles: RoleCode[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
}

export interface CycleInfo {
  id: number;
  code: string;
  status: 'OPEN' | 'CLOSED' | 'ARCHIVED';
  current: boolean;
}

export interface ScopeInfo {
  facilityIds: number[];
  ternaIds: number[];
}

export interface MeResponse {
  user: UserInfo;
  scope: ScopeInfo;
  currentCycle: CycleInfo | null;
}
