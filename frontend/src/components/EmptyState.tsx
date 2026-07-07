import type { ReactNode } from 'react';

export function EmptyState({ message, action }: { message: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-neutral-300 bg-white py-12 text-center">
      <p className="text-sm text-neutral-500">{message}</p>
      {action}
    </div>
  );
}
