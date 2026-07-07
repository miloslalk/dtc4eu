import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';

export function Modal({ title, open, onClose, children, footer }: {
  title: ReactNode;
  open: boolean;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-neutral-900/40 p-4" onClick={onClose}>
      <div className="w-full max-w-lg rounded-xl bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-neutral-200 px-5 py-3.5">
          <h2 className="text-base font-bold text-blue-600">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close"
            className="rounded-md px-2 text-lg text-neutral-400 hover:bg-neutral-100 hover:text-neutral-700">
            ×
          </button>
        </div>
        <div className="px-5 py-4">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-neutral-200 px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmDialog({ title, message, open, onConfirm, onCancel, confirming }: {
  title: ReactNode;
  message: ReactNode;
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  confirming?: boolean;
}) {
  const { t } = useTranslation();
  return (
    <Modal
      title={title}
      open={open}
      onClose={onCancel}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel}>{t('common.cancel')}</Button>
          <Button onClick={onConfirm} loading={confirming}>{t('common.confirm')}</Button>
        </>
      }
    >
      <p className="text-sm text-neutral-700">{message}</p>
    </Modal>
  );
}
