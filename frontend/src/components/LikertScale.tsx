/**
 * The shared Likert control (spec T6: built first). Generic over the
 * questionnaire's scale bounds — DTCRO uses 1–4 with the v0.2 anchors
 * (1 Not at all … 4 Very much) passed via `anchors`. Sentence-long anchors
 * (ViVaCom 0–5) do not fit under the buttons: the chosen one is spelled out
 * below the row instead, and every button keeps its anchor as a tooltip.
 */
const SHORT_ANCHOR = 24;

export function LikertScale({ min, max, value, onChange, disabled, anchors }: {
  min: number;
  max: number;
  value: number | null;
  onChange: (value: number) => void;
  disabled?: boolean;
  anchors?: Record<number, string>;
}) {
  const steps: number[] = [];
  for (let v = min; v <= max; v++) {
    steps.push(v);
  }
  const short = Object.values(anchors ?? {}).every((label) => label.length <= SHORT_ANCHOR);
  const chosen = value !== null ? anchors?.[value] : undefined;
  return (
    <div>
    <div role="radiogroup" className="flex flex-wrap gap-1.5">
      {steps.map((step) => {
        const selected = value === step;
        const anchor = anchors?.[step];
        return (
          <div key={step} className="flex w-12 flex-col items-center gap-0.5">
            <button
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={anchor ? `${step} — ${anchor}` : String(step)}
              title={anchor}
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
            {anchor && short && (
              <span className={`text-center text-[10px] leading-tight ${
                selected ? 'font-semibold text-blue-700' : 'text-neutral-400'
              }`}>
                {anchor}
              </span>
            )}
          </div>
        );
      })}
    </div>
    {!short && chosen && (
      <p className="mt-1 text-xs font-medium text-blue-700">{value} = {chosen}</p>
    )}
    </div>
  );
}
