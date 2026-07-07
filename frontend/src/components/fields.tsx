import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

const inputClass =
  'w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 ' +
  'placeholder:text-neutral-400 focus:border-blue-400 focus:outline-2 focus:outline-offset-1 focus:outline-gold-500 ' +
  'disabled:bg-neutral-100 disabled:text-neutral-500';

function FieldShell({ label, error, required, children }: { label: ReactNode; error?: string; required?: boolean; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-neutral-700">
        {label}
        {required && <span className="ml-0.5 text-error">*</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs font-medium text-error">{error}</span>}
    </label>
  );
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  error?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, required, className = '', ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} required={required}>
      <input ref={ref} className={`${inputClass} ${className}`} {...rest} />
    </FieldShell>
  );
});

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: ReactNode;
  error?: string;
  children: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, error, required, children, className = '', ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} required={required}>
      <select ref={ref} className={`${inputClass} ${className}`} {...rest}>
        {children}
      </select>
    </FieldShell>
  );
});

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode;
  error?: string;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, required, className = '', ...rest },
  ref,
) {
  return (
    <FieldShell label={label} error={error} required={required}>
      <textarea ref={ref} rows={3} className={`${inputClass} ${className}`} {...rest} />
    </FieldShell>
  );
});

/** Bare select for filter bars (no label shell). */
export const filterInputClass =
  'rounded-lg border border-neutral-300 bg-white px-3 py-1.5 text-sm text-neutral-800 ' +
  'focus:outline-2 focus:outline-offset-1 focus:outline-gold-500';
