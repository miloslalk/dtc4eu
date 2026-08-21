/** Shared types for the public site and the admin CMS. */

export type BlockType =
  | 'HERO' | 'RICH_TEXT' | 'IMAGE_TEXT' | 'CARDS' | 'FACTS' | 'PARTNERS'
  | 'GALLERY' | 'NEWS_LIST' | 'DOWNLOADS' | 'CTA' | 'CONTACT_FORM';

export interface Block {
  id?: number;
  blockType: BlockType;
  payload: Record<string, unknown>;
}

export interface NavItem {
  slug: string;
  label: string;
}

export interface PublicPage {
  slug: string;
  title: string;
  seoTitle: string | null;
  seoDescription: string | null;
  blocks: Block[];
}

export interface NewsItem {
  slug: string;
  title: string;
  excerpt: string | null;
  coverMediaId: number | null;
  publishedAt: string | null;
}

export interface NewsDetail extends NewsItem {
  bodyHtml: string;
}

export interface GalleryImage {
  id: number;
  mediaId: number;
  caption: string | null;
  url: string;
  altText: string;
}

export interface Gallery {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  images: GalleryImage[];
}

export type SiteSettings = Record<string, Record<string, unknown>>;

export function mediaUrl(mediaId: number | null | undefined): string | null {
  return mediaId == null ? null : `/api/public/media/${mediaId}`;
}

export function settingText(settings: SiteSettings | undefined, key: string, field: string): string {
  const value = settings?.[key]?.[field];
  return typeof value === 'string' ? value : '';
}
