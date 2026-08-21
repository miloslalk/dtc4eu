export function SectionHeading({ children }: { children: string }) {
  return (
    <div className="mb-6">
      <div className="mb-3 h-1 w-12 rounded-full bg-gradient-to-r from-gold-500 to-gold-300" aria-hidden />
      <h2 className="text-2xl font-extrabold tracking-tight text-blue-700 sm:text-3xl">{children}</h2>
    </div>
  );
}
