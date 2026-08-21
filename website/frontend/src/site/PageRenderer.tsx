import type { Block } from '../lib/types';
import { Hero } from './blocks/Hero';
import { RichText } from './blocks/RichText';
import { ImageText } from './blocks/ImageText';
import { Cards } from './blocks/Cards';
import { Facts } from './blocks/Facts';
import { Partners } from './blocks/Partners';
import { GalleryBlock } from './blocks/GalleryBlock';
import { NewsList } from './blocks/NewsList';
import { Downloads } from './blocks/Downloads';
import { Cta } from './blocks/Cta';
import { ContactForm } from './blocks/ContactForm';

/** Maps CMS block types to render components; unknown types render nothing. */
export function PageRenderer({ blocks }: { blocks: Block[] }) {
  return (
    <>
      {blocks.map((block, index) => {
        const key = block.id ?? index;
        switch (block.blockType) {
          case 'HERO':
            return <Hero key={key} payload={block.payload} />;
          case 'RICH_TEXT':
            return <RichText key={key} payload={block.payload} />;
          case 'IMAGE_TEXT':
            return <ImageText key={key} payload={block.payload} />;
          case 'CARDS':
            return <Cards key={key} payload={block.payload} />;
          case 'FACTS':
            return <Facts key={key} payload={block.payload} />;
          case 'PARTNERS':
            return <Partners key={key} payload={block.payload} />;
          case 'GALLERY':
            return <GalleryBlock key={key} payload={block.payload} />;
          case 'NEWS_LIST':
            return <NewsList key={key} payload={block.payload} />;
          case 'DOWNLOADS':
            return <Downloads key={key} payload={block.payload} />;
          case 'CTA':
            return <Cta key={key} payload={block.payload} />;
          case 'CONTACT_FORM':
            return <ContactForm key={key} payload={block.payload} />;
          default:
            return null;
        }
      })}
    </>
  );
}
