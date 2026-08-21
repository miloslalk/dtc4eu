import axios from 'axios';

/** Anonymous client for /api/public. */
export const publicApi = axios.create({ baseURL: '/api/public' });

/* ----------------------- admin client with JWT ----------------------- */

const ACCESS_KEY = 'dtc4eu-web.access';
const REFRESH_KEY = 'dtc4eu-web.refresh';

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export const adminApi = axios.create({ baseURL: '/api/admin' });

adminApi.interceptors.request.use((config) => {
  const token = tokenStore.access;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<void> | null = null;

adminApi.interceptors.response.use(undefined, async (error) => {
  const original = error.config;
  if (error.response?.status === 401 && tokenStore.refresh && !original._retried) {
    original._retried = true;
    refreshing ??= axios
      .post('/api/admin/auth/refresh', { refreshToken: tokenStore.refresh })
      .then(({ data }) => tokenStore.set(data.accessToken, data.refreshToken))
      .catch(() => {
        tokenStore.clear();
        throw error;
      })
      .finally(() => {
        refreshing = null;
      });
    await refreshing;
    return adminApi(original);
  }
  throw error;
});

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; details?: { field: string; message: string }[] };
    if (data?.details?.length) {
      return data.details.map((d) => `${d.field}: ${d.message}`).join('; ');
    }
    if (data?.message) {
      return data.message;
    }
    return error.message;
  }
  return String(error);
}
