import type { BlockType } from '../../lib/types';

/**
 * Client-side mirror of the backend BlockRegistry: drives the per-type edit
 * forms in the page builder. Keep in sync when adding a block type.
 */

export type FieldKind = 'text' | 'textarea' | 'html' | 'number' | 'media' | 'gallery' | 'enum';

export interface FieldDef {
  key: string;
  label: string;
  kind: FieldKind;
  required?: boolean;
  options?: string[];
}

export interface ItemsDef {
  key: string;
  label: string;
  fields: FieldDef[];
}

export interface BlockFormDef {
  label: string;
  description: string;
  fields: FieldDef[];
  items?: ItemsDef;
}

export const BLOCK_FORMS: Record<BlockType, BlockFormDef> = {
  HERO: {
    label: 'Hero',
    description: 'Large page opener with heading, intro and optional call-to-action.',
    fields: [
      { key: 'kicker', label: 'Kicker (eyebrow line)', kind: 'text' },
      { key: 'heading', label: 'Heading', kind: 'text', required: true },
      { key: 'subheading', label: 'Subheading', kind: 'textarea' },
      { key: 'ctaLabel', label: 'Button label', kind: 'text' },
      { key: 'ctaHref', label: 'Button link (e.g. /the-project)', kind: 'text' },
      { key: 'imageMediaId', label: 'Image', kind: 'media' },
    ],
  },
  RICH_TEXT: {
    label: 'Rich text',
    description: 'Free text section with optional heading.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text' },
      { key: 'bodyHtml', label: 'Body', kind: 'html', required: true },
    ],
  },
  IMAGE_TEXT: {
    label: 'Image + text',
    description: 'Text beside an image.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text', required: true },
      { key: 'bodyHtml', label: 'Body', kind: 'html', required: true },
      { key: 'imageMediaId', label: 'Image', kind: 'media' },
      { key: 'imageSide', label: 'Image side', kind: 'enum', options: ['LEFT', 'RIGHT'] },
    ],
  },
  CARDS: {
    label: 'Cards',
    description: 'Grid of title + text cards.',
    fields: [{ key: 'heading', label: 'Heading', kind: 'text' }],
    items: {
      key: 'items',
      label: 'Cards',
      fields: [
        { key: 'title', label: 'Title', kind: 'text', required: true },
        { key: 'bodyHtml', label: 'Text', kind: 'html' },
      ],
    },
  },
  FACTS: {
    label: 'Facts panel',
    description: '"Project at a glance" label/value panel on blue.',
    fields: [{ key: 'heading', label: 'Heading', kind: 'text' }],
    items: {
      key: 'items',
      label: 'Facts',
      fields: [
        { key: 'label', label: 'Label', kind: 'text', required: true },
        { key: 'value', label: 'Value', kind: 'text', required: true },
      ],
    },
  },
  PARTNERS: {
    label: 'Partners grid',
    description: 'Partner organisations with logo and country.',
    fields: [{ key: 'heading', label: 'Heading', kind: 'text' }],
    items: {
      key: 'items',
      label: 'Partners',
      fields: [
        { key: 'name', label: 'Name', kind: 'text', required: true },
        { key: 'url', label: 'Website URL', kind: 'text' },
        { key: 'country', label: 'Country', kind: 'text' },
        { key: 'logoMediaId', label: 'Logo', kind: 'media' },
      ],
    },
  },
  GALLERY: {
    label: 'Photo gallery',
    description: 'Renders a gallery managed under Galleries.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text' },
      { key: 'galleryId', label: 'Gallery', kind: 'gallery', required: true },
    ],
  },
  NEWS_LIST: {
    label: 'News list',
    description: 'Latest published news posts.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text' },
      { key: 'limit', label: 'Number of posts', kind: 'number' },
    ],
  },
  DOWNLOADS: {
    label: 'Downloads',
    description: 'List of downloadable files from the media library.',
    fields: [{ key: 'heading', label: 'Heading', kind: 'text' }],
    items: {
      key: 'items',
      label: 'Files',
      fields: [
        { key: 'title', label: 'Title', kind: 'text', required: true },
        { key: 'description', label: 'Description', kind: 'text' },
        { key: 'mediaId', label: 'File', kind: 'media', required: true },
      ],
    },
  },
  CTA: {
    label: 'Call to action',
    description: 'Highlighted banner with a button.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text', required: true },
      { key: 'bodyHtml', label: 'Text', kind: 'html' },
      { key: 'buttonLabel', label: 'Button label', kind: 'text', required: true },
      { key: 'buttonHref', label: 'Button link', kind: 'text', required: true },
    ],
  },
  CONTACT_FORM: {
    label: 'Contact form',
    description: 'The visitor contact form; submissions land in the Inbox.',
    fields: [
      { key: 'heading', label: 'Heading', kind: 'text' },
      { key: 'intro', label: 'Intro text', kind: 'textarea' },
    ],
  },
};

export function emptyPayload(type: BlockType): Record<string, unknown> {
  const def = BLOCK_FORMS[type];
  return def.items ? { [def.items.key]: [] } : {};
}
