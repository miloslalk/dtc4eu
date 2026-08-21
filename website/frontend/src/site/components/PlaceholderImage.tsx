/**
 * Brand-styled stand-in rendered whenever a block has no media attached yet.
 * Keeps seeded placeholder pages presentable before real photos are uploaded.
 */
export function PlaceholderImage({ label = 'Image coming soon', className = '' }: {
  label?: string;
  className?: string;
}) {
  return (
    <div
      role="img"
      aria-label={label}
      className={`relative flex min-h-44 w-full items-center justify-center overflow-hidden rounded-2xl
        bg-gradient-to-br from-blue-100 via-blue-50 to-gold-100 ${className}`}
    >
      <div aria-hidden className="absolute -left-8 -top-8 size-36 rounded-full bg-azure-200/40 blur-2xl" />
      <div aria-hidden className="absolute -bottom-10 -right-6 size-40 rounded-full bg-gold-200/50 blur-2xl" />
      <span className="relative rounded-full border border-white/60 bg-white/70 px-4 py-1.5 text-xs font-bold text-blue-400 backdrop-blur">
        {label}
      </span>
    </div>
  );
}
