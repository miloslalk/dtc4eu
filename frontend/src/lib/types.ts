// Shared API types mirroring the backend DTOs.
import { shortCycleCode } from './ordinal';

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
  /** temporary or published password: only the change-password screen is usable (X4) */
  mustChangePassword: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
}

/** Cycles overlap (v0.2 register §8) — several can be OPEN at once; sequenceNumber is the derived programme order. */
export interface CycleInfo {
  id: number;
  code: string;
  startDate?: string | null;
  endDate?: string | null;
  status: 'OPEN' | 'CLOSED' | 'ARCHIVED';
  sequenceNumber: number | null;
  /** optional admin-chosen landing cycle for first visits (Q1) */
  isDefault?: boolean;
}

/**
 * Global cycle label: the code only ("2025-26"). Ordinals are per facility and
 * live in the facility history (see lib/ordinal.ts, Simone's feedback #1).
 */
export function cycleLabel(cycle: Pick<CycleInfo, 'code'>): string {
  return shortCycleCode(cycle.code);
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
