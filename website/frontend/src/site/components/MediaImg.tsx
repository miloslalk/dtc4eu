import { mediaUrl } from '../../lib/types';
import { PlaceholderImage } from './PlaceholderImage';

export function MediaImg({ mediaId, alt = '', className = '' }: {
  mediaId: number | null | undefined;
  alt?: string;
  className?: string;
}) {
  const url = mediaUrl(mediaId);
  if (!url) {
    return <PlaceholderImage className={className} />;
  }
  return <img src={url} alt={alt} loading="lazy" className={`w-full rounded-xl object-cover ${className}`} />;
}
