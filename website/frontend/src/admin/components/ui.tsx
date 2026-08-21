import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { forwardRef } from 'react';

/* Shared CMS UI primitives, mirroring the platform's component idiom. */

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const buttonStyles: Record<Variant, string> = {
  primary: 'bg-blue-600 text-white hover:bg-blue-700',
  secondary: 'bg-gold-500 text-[#3A2C00] hover:bg-gold-600',
  ghost: 'border border-neutral-300 text-neutral-700 hover:bg-neutral-100',
  danger: 'bg-error text-white hover:opacity-90',
};

export function Button({ variant = 'primary', loading, className = '', children, disabled, ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled || loading}
      className={`rounded-lg px-4 py-2 text-sm font-semibold transition-colors
        focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500
        disabled:cursor-not-allowed disabled:opacity-60 ${buttonStyles[variant]} ${className}`}
      {...rest}
    >
      {loading ? '…' : children}
    </button>
  );
}

export const inputClass =
  'w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 ' +
  'focus:outline-2 focus:outline-offset-1 focus:outline-gold-500 disabled:bg-neutral-100';

export function FieldLabel({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-semibold text-neutral-600">{label}</span>
      {children}
    </label>
  );
}

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function TextInput({ className = '', ...rest }, ref) {
    return <input ref={ref} className={`${inputClass} ${className}`} {...rest} />;
  },
);

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function TextArea({ className = '', ...rest }, ref) {
    return <textarea ref={ref} className={`${inputClass} ${className}`} {...rest} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className = '', children, ...rest }, ref) {
    return (
      <select ref={ref} className={`${inputClass} ${className}`} {...rest}>
        {children}
      </select>
    );
  },
);

export function StatusChip({ published }: { published: boolean }) {
  return (
    <span className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold ${
      published
        ? 'border-success/30 bg-success-surface text-success'
        : 'border-neutral-300 bg-neutral-100 text-neutral-600'
    }`}>
      {published ? 'Published' : 'Draft'}
    </span>
  );
}

export function Modal({ title, open, onClose, children, footer, wide }: {
  title: ReactNode;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-neutral-900/40 p-4" onClick={onClose}>
      <div
        className={`flex max-h-[calc(100dvh-2rem)] w-full flex-col rounded-xl bg-white shadow-xl ${
          wide ? 'max-w-3xl' : 'max-w-lg'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex shrink-0 items-center justify-between border-b border-neutral-200 px-5 py-3.5">
          <h2 className="text-base font-bold text-blue-600">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close"
            className="rounded-md px-2 text-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700">
            ×
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex shrink-0 justify-end gap-2 border-t border-neutral-200 px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <p className="rounded-xl border border-dashed border-neutral-300 bg-white px-6 py-10 text-center text-sm text-neutral-500">
      {message}
    </p>
  );
}
