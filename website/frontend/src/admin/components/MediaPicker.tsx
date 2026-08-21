import { useRef, useState } from 'react';
import { useMedia, useMediaMutations, type Media } from '../adminApi';
import { apiErrorMessage } from '../../lib/api';
import { Button, Modal } from './ui';

/** Grid picker over the media library with inline upload. */
export function MediaPickerModal({ open, onClose, onPick, imagesOnly = true }: {
  open: boolean;
  onClose: () => void;
  onPick: (media: Media) => void;
  imagesOnly?: boolean;
}) {
  const media = useMedia();
  const { upload } = useMediaMutations();
  const fileInput = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const assets = (media.data ?? []).filter((m) => !imagesOnly || m.contentType.startsWith('image/'));

  const handleUpload = async (file: File | undefined) => {
    if (!file) return;
    setError(null);
    try {
      const created = await upload.mutateAsync({ file });
      onPick(created);
      onClose();
    } catch (e) {
      setError(apiErrorMessage(e));
    }
  };

  return (
    <Modal title="Media library" open={open} onClose={onClose} wide
      footer={<Button variant="ghost" onClick={onClose}>Cancel</Button>}>
      <div className="mb-3 flex items-center gap-3">
        <Button variant="secondary" loading={upload.isPending} onClick={() => fileInput.current?.click()}>
          Upload new
        </Button>
        <input ref={fileInput} type="file" hidden accept={imagesOnly ? 'image/*' : undefined}
          onChange={(e) => handleUpload(e.target.files?.[0])} />
        {error && <span className="text-sm font-medium text-error">{error}</span>}
      </div>
      {assets.length === 0 ? (
        <p className="py-8 text-center text-sm text-neutral-500">No media yet — upload the first file.</p>
      ) : (
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {assets.map((asset) => (
            <button key={asset.id} type="button" onClick={() => { onPick(asset); onClose(); }}
              className="group overflow-hidden rounded-lg border border-neutral-200 text-left hover:border-blue-400">
              {asset.contentType.startsWith('image/') ? (
                <img src={asset.url} alt={asset.altText ?? ''} className="aspect-square w-full object-cover" />
              ) : (
                <span className="flex aspect-square items-center justify-center bg-neutral-100 text-2xl">📄</span>
              )}
              <span className="block truncate px-2 py-1 text-[11px] text-neutral-600">{asset.filename}</span>
            </button>
          ))}
        </div>
      )}
    </Modal>
  );
}

export function MediaField({ value, onChange, label = 'Image' }: {
  value: number | null;
  onChange: (mediaId: number | null) => void;
  label?: string;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);
  return (
    <div>
      <span className="mb-1 block text-xs font-semibold text-neutral-600">{label}</span>
      <div className="flex items-center gap-3">
        {value !== null ? (
          <img src={`/api/public/media/${value}`} alt="" className="size-14 rounded-lg border border-neutral-200 object-cover" />
        ) : (
          <span className="flex size-14 items-center justify-center rounded-lg border border-dashed border-neutral-300 text-xs text-neutral-400">
            none
          </span>
        )}
        <Button variant="ghost" onClick={() => setPickerOpen(true)}>Choose…</Button>
        {value !== null && <Button variant="ghost" onClick={() => onChange(null)}>Clear</Button>}
      </div>
      <MediaPickerModal open={pickerOpen} onClose={() => setPickerOpen(false)} onPick={(m) => onChange(m.id)} />
    </div>
  );
}
