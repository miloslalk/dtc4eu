import { Container } from '../components/Container';
import { MediaImg } from '../components/MediaImg';
import { Reveal } from '../components/Reveal';
import { SectionHeading } from '../components/SectionHeading';
import { num, str } from './helpers';

export function ImageText({ payload }: { payload: Record<string, unknown> }) {
  const imageLeft = str(payload, 'imageSide') === 'LEFT';
  return (
    <section className="py-14">
      <Container className="grid items-center gap-10 lg:grid-cols-2">
        <Reveal className={imageLeft ? 'lg:order-2' : ''}>
          <SectionHeading>{str(payload, 'heading')}</SectionHeading>
          {/* Safe: the CMS backend sanitizes every HTML payload on write */}
          <div className="cms-prose" dangerouslySetInnerHTML={{ __html: str(payload, 'bodyHtml') }} />
        </Reveal>
        <Reveal delay={120} className={imageLeft ? 'lg:order-1' : ''}>
          <div className="relative">
            <div aria-hidden className={`absolute -bottom-3 size-full rounded-2xl border-2 border-gold-300/70 ${imageLeft ? '-left-3' : '-right-3'}`} />
            <MediaImg mediaId={num(payload, 'imageMediaId')} className="relative max-h-80 rounded-2xl shadow-xl shadow-blue-600/10" />
          </div>
        </Reveal>
      </Container>
    </section>
  );
}
