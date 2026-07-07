export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-neutral-500">
      <span className="size-5 animate-spin rounded-full border-2 border-blue-300 border-t-blue-600" />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
