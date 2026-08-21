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
  | 'UPPER_COORDINATOR'
  | 'COORDINATOR'
  | 'AUDIT_COORDINATOR'
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

/** Cycles overlap (v0.2 register §8) — several can be OPEN at once; sequenceNumber feeds "1st 2026-27" labels. */
export interface CycleInfo {
  id: number;
  code: string;
  startDate?: string | null;
  endDate?: string | null;
  status: 'OPEN' | 'CLOSED' | 'ARCHIVED';
  sequenceNumber: number | null;
}

/** "1st 2026-27"-style display label from the register (§8). */
export function cycleLabel(cycle: Pick<CycleInfo, 'code' | 'sequenceNumber'>): string {
  const shortSpan = cycle.code.replace(/^(\d{4})-\d{2}(\d{2})$/, '$1-$2');
  if (cycle.sequenceNumber == null) return shortSpan;
  const n = cycle.sequenceNumber;
  const suffix = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10 > 3 ? 0 : n % 10];
  return `${n}${suffix} ${shortSpan}`;
}

export interface ScopeInfo {
  facilityIds: number[];
  ternaIds: number[];
}

export interface MeResponse {
  user: UserInfo;
  scope: ScopeInfo;
  defaultCycle: CycleInfo | null;
}
