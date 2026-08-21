import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import { cycleStore } from './tokenStore';
import { useAuth } from './auth';
import type { CycleInfo } from './types';

/**
 * The selected annual cycle is an implicit filter on everything (spec §6):
 * it rides along as the X-Cycle-Id header via the api interceptor. Cycles
 * overlap (v0.2 register §8) so there is no platform-wide "current" cycle —
 * selection defaults to the most recently started OPEN cycle and persists
 * per browser.
 */
interface CycleContextValue {
  cycles: CycleInfo[];
  selected: CycleInfo | null;
  select: (cycleId: number) => void;
  readOnly: boolean;
}

const CycleContext = createContext<CycleContextValue | null>(null);

export function CycleProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const { data: cycles = [] } = useQuery({
    queryKey: ['cycles'],
    queryFn: async () => (await api.get<CycleInfo[]>('/cycles')).data,
    enabled: user !== null,
    staleTime: 5 * 60 * 1000,
  });

  const selected = useMemo(() => {
    const storedId = cycleStore.get();
    const stored = cycles.find((c) => c.id === storedId);
    // the list arrives most-recently-started first
    return stored ?? cycles.find((c) => c.status === 'OPEN') ?? cycles[0] ?? null;
  }, [cycles]);

  // Keep the header store in sync with the effective selection.
  if (selected && cycleStore.get() !== selected.id) {
    cycleStore.set(selected.id);
  }

  const select = useCallback(
    (cycleId: number) => {
      cycleStore.set(cycleId);
      // all cycle-scoped data is stale once the cycle changes
      queryClient.invalidateQueries();
    },
    [queryClient],
  );

  const value = useMemo(
    () => ({ cycles, selected, select, readOnly: selected !== null && selected.status !== 'OPEN' }),
    [cycles, selected, select],
  );

  return <CycleContext.Provider value={value}>{children}</CycleContext.Provider>;
}

export function useCycle(): CycleContextValue {
  const ctx = useContext(CycleContext);
  if (!ctx) {
    throw new Error('useCycle must be used within CycleProvider');
  }
  return ctx;
}
