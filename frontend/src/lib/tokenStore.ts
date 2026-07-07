// Token + cycle selection storage, framework-free so the axios layer can use
// it without importing React modules (avoids circular imports).

const ACCESS_KEY = 'dtc4eu.accessToken';
const REFRESH_KEY = 'dtc4eu.refreshToken';
const CYCLE_KEY = 'dtc4eu.cycleId';

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export const cycleStore = {
  get(): number | null {
    const raw = localStorage.getItem(CYCLE_KEY);
    return raw ? Number(raw) : null;
  },
  set(cycleId: number | null) {
    if (cycleId === null) {
      localStorage.removeItem(CYCLE_KEY);
    } else {
      localStorage.setItem(CYCLE_KEY, String(cycleId));
    }
  },
};
