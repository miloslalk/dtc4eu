import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGalleryById } from '../../lib/publicApi';
import type { GalleryImage } from '../../lib/types';
import { Container } from '../components/Container';
import { SectionHeading } from '../components/SectionHeading';
import { num, str } from './helpers';

export function GalleryBlock({ payload }: { payload: Record<string, unknown> }) {
  const { t } = useTranslation();
  const heading = str(payload, 'heading');
  const gallery = useGalleryById(num(payload, 'galleryId'));
  const [lightbox, setLightbox] = useState<GalleryImage | null>(null);

  const images = gallery.data?.images ?? [];

  return (
    <section className="py-14">
      <Container>
        {heading && <SectionHeading>{heading}</SectionHeading>}
        {images.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-neutral-300 bg-white px-6 py-12 text-center text-sm text-neutral-500">
            {gallery.data?.description || t('site.galleryEmpty')}
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {images.map((image) => (
              <button
                key={image.id}
                type="button"
                onClick={() => setLightbox(image)}
                className="group relative overflow-hidden rounded-2xl shadow-lg shadow-blue-600/5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500"
              >
                <img
                  src={image.url}
                  alt={image.altText || image.caption || ''}
                  loading="lazy"
                  className="aspect-square w-full object-cover transition-transform duration-500 group-hover:scale-110"
                />
                <span aria-hidden className="absolute inset-0 bg-gradient-to-t from-blue-900/50 via-transparent to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
                {image.caption && (
                  <span className="absolute inset-x-0 bottom-0 translate-y-2 p-3 text-left text-xs font-semibold text-white opacity-0 transition-all duration-300 group-hover:translate-y-0 group-hover:opacity-100">
                    {image.caption}
                  </span>
                )}
              </button>
            ))}
          </div>
        )}
      </Container>

      {lightbox && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-neutral-900/80 p-4"
          role="dialog"
          aria-modal
          onClick={() => setLightbox(null)}
        >
          <figure className="max-h-full max-w-4xl" onClick={(e) => e.stopPropagation()}>
            <img
              src={lightbox.url}
              alt={lightbox.altText || lightbox.caption || ''}
              className="max-h-[80dvh] w-auto rounded-xl"
            />
            {lightbox.caption && (
              <figcaption className="mt-2 text-center text-sm text-blue-100">{lightbox.caption}</figcaption>
            )}
          </figure>
          <button
            type="button"
            onClick={() => setLightbox(null)}
            aria-label={t('site.close')}
            className="absolute right-4 top-4 rounded-full bg-white/10 px-3 py-1.5 text-xl text-white hover:bg-white/20"
          >
            ×
          </button>
        </div>
      )}
    </section>
  );
}
