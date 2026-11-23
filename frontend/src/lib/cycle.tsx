import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api } from './api';
import { cycleStore } from './tokenStore';
import { useAuth } from './auth';
import type { CycleInfo } from './types';

/**
 * The selected annual cycle is an implicit filter on everything (spec §6):
 * it rides along as the X-Cycle-Id header via the api interceptor. Cycles
 * overlap (v0.2 register §8) so there is no platform-wide "current" cycle.
 *
 * Selection order (Simone's feedback #1): a `?cycleId=` link, then the
 * browser's last choice, then — on a first visit — the admin-chosen default
 * cycle if one is set (Q1), the most recent cycle that holds the user's data,
 * and finally the most recently started OPEN cycle.
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
  const [searchParams, setSearchParams] = useSearchParams();

  const { data: cycles = [] } = useQuery({
    queryKey: ['cycles'],
    queryFn: async () => (await api.get<CycleInfo[]>('/cycles')).data,
    enabled: user !== null,
    staleTime: 5 * 60 * 1000,
  });

  // decided once: the store is filled by the first effective selection below
  const [firstVisit] = useState(() => cycleStore.get() === null);
  const { data: cyclesWithData, isPending: cyclesWithDataPending } = useQuery({
    queryKey: ['me', 'cycles-with-data'],
    queryFn: async () => (await api.get<number[]>('/auth/me/cycles-with-data')).data,
    enabled: user !== null && firstVisit,
    staleTime: Infinity,
  });

  // Held in state so a selection re-renders the label even when the cycles
  // list keeps its reference (the stale-label bug, X5).
  const [selectedId, setSelectedId] = useState<number | null>(() => cycleStore.get());

  const urlCycleId = Number(searchParams.get('cycleId')) || null;

  const resolved = useMemo(() => {
    const byId = (id: number | null) => (id === null ? undefined : cycles.find((c) => c.id === id));
    const withData = (cyclesWithData ?? []).map(byId).find((c) => c !== undefined);
    // the list arrives most-recently-started first
    // an admin-chosen default (Q1, optional) outranks the per-user guess on a first visit
    return byId(urlCycleId) ?? byId(selectedId) ?? cycles.find((c) => c.isDefault) ?? withData
      ?? cycles.find((c) => c.status === 'OPEN') ?? cycles[0] ?? null;
  }, [cycles, cyclesWithData, selectedId, urlCycleId]);
  // on a first visit, wait for "cycles with data" instead of loading a fallback cycle first
  const selected = firstVisit && selectedId === null && urlCycleId === null && cyclesWithDataPending
    && user !== null ? null : resolved;

  // Keep the header store in sync with the effective selection.
  if (selected && cycleStore.get() !== selected.id) {
    cycleStore.set(selected.id);
  }

  // Whatever moved the selection (a click, a link, the first-visit default),
  // all cycle-scoped data fetched for the previous cycle is stale.
  const previousId = useRef<number | null>(null);
  useEffect(() => {
    const id = selected?.id ?? null;
    if (previousId.current !== null && id !== previousId.current) {
      queryClient.invalidateQueries({
        predicate: (q) => q.queryKey[0] !== 'cycles' && q.queryKey[1] !== 'cycles-with-data',
      });
    }
    previousId.current = id;
  }, [selected, queryClient]);

  // A ?cycleId= link selects once, then leaves the URL clean.
  useEffect(() => {
    if (urlCycleId === null || !cycles.some((c) => c.id === urlCycleId)) return;
    setSelectedId(urlCycleId);
    const next = new URLSearchParams(searchParams);
    next.delete('cycleId');
    setSearchParams(next, { replace: true });
  }, [urlCycleId, cycles, searchParams, setSearchParams]);

  const select = useCallback(
    (cycleId: number) => {
      cycleStore.set(cycleId);
      setSelectedId(cycleId);
    },
    [],
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
