import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { cycleStore, tokenStore } from './tokenStore';
import type { ApiErrorBody, TokenResponse } from './types';

/**
 * Axios instance for the DTC4EU API. Attaches the Bearer token and the
 * selected cycle (X-Cycle-Id, spec §6) to every request, and transparently
 * retries once after refreshing an expired access token.
 */
export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccess();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  const cycleId = cycleStore.get();
  if (cycleId !== null) {
    config.headers['X-Cycle-Id'] = String(cycleId);
  }
  return config;
});

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) {
    throw new Error('No refresh token');
  }
  // plain axios: must not run through the instance interceptors
  const { data } = await axios.post<TokenResponse>('/api/v1/auth/refresh', { refreshToken });
  tokenStore.set(data.accessToken, data.refreshToken);
  return data.accessToken;
}

api.interceptors.response.use(undefined, async (error: AxiosError<ApiErrorBody>) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const isAuthCall = original?.url?.startsWith('/auth/login') || original?.url?.startsWith('/auth/refresh');
  if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
    original._retried = true;
    try {
      refreshPromise = refreshPromise ?? refreshAccessToken();
      const newToken = await refreshPromise;
      refreshPromise = null;
      original.headers.Authorization = `Bearer ${newToken}`;
      return api.request(original);
    } catch {
      refreshPromise = null;
      tokenStore.clear();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    }
  }
  throw error;
});

/** Human-readable message from an API error, falling back to a generic one. */
export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data;
    if (body?.details?.length) {
      return body.details.map((d) => `${d.field}: ${d.message}`).join('; ');
    }
    if (body?.message) {
      return body.message;
    }
    if (error.code === 'ERR_NETWORK') {
      return 'Cannot reach the server';
    }
  }
  return 'Something went wrong';
}

/** Triggers a browser download for a blob response (CSV exports). */
export async function downloadFile(url: string, params: Record<string, unknown>, filename: string) {
  const response = await api.get(url, { params, responseType: 'blob' });
  const href = URL.createObjectURL(response.data as Blob);
  const link = document.createElement('a');
  link.href = href;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(href);
}
