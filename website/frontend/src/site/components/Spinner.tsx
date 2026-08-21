export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-neutral-500">
      <span className="size-5 animate-spin rounded-full border-2 border-blue-200 border-t-blue-600" aria-hidden />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
