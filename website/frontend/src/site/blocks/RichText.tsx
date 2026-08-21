import { Container } from '../components/Container';
import { Reveal } from '../components/Reveal';
import { SectionHeading } from '../components/SectionHeading';
import { str } from './helpers';

export function RichText({ payload }: { payload: Record<string, unknown> }) {
  const heading = str(payload, 'heading');
  return (
    <section className="py-14">
      <Container>
        <Reveal>
          {heading && <SectionHeading>{heading}</SectionHeading>}
          {/* Safe: the CMS backend sanitizes every HTML payload on write */}
          <div className="cms-prose max-w-3xl text-[1.05rem]" dangerouslySetInnerHTML={{ __html: str(payload, 'bodyHtml') }} />
        </Reveal>
      </Container>
    </section>
  );
}
