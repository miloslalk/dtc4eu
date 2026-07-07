/**
 * The shared Likert control (spec T6: built first). Generic over the
 * questionnaire's scale bounds — DTCRO uses 1–4. Anchor labels are an open
 * input (spec §9) and will attach here once supplied.
 */
export function LikertScale({ min, max, value, onChange, disabled }: {
  min: number;
  max: number;
  value: number | null;
  onChange: (value: number) => void;
  disabled?: boolean;
}) {
  const steps: number[] = [];
  for (let v = min; v <= max; v++) {
    steps.push(v);
  }
  return (
    <div role="radiogroup" className="flex gap-1.5">
      {steps.map((step) => {
        const selected = value === step;
        return (
          <button
            key={step}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={disabled}
            onClick={() => onChange(step)}
            className={`size-9 rounded-lg border text-sm font-bold transition-colors
              focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500
              disabled:cursor-not-allowed disabled:opacity-60 ${
                selected
                  ? 'border-blue-600 bg-blue-600 text-white'
                  : 'border-neutral-300 bg-white text-neutral-700 hover:border-blue-400 hover:bg-blue-50'
              }`}
          >
            {step}
          </button>
        );
      })}
    </div>
  );
}
