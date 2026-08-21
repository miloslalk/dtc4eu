import { Container } from '../components/Container';
import { Reveal } from '../components/Reveal';
import { SectionHeading } from '../components/SectionHeading';
import { items, str } from './helpers';

export function Cards({ payload }: { payload: Record<string, unknown> }) {
  const heading = str(payload, 'heading');
  const cards = items(payload);
  return (
    <section className="py-14">
      <Container>
        {heading && <Reveal><SectionHeading>{heading}</SectionHeading></Reveal>}
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {cards.map((card, index) => (
            <Reveal key={index} delay={index * 90} className="h-full">
              <article
                className="group relative h-full overflow-hidden rounded-2xl border border-neutral-200/70 bg-white p-7
                  shadow-lg shadow-blue-600/5 transition-all duration-300 hover:-translate-y-1.5 hover:shadow-xl hover:shadow-blue-600/10"
              >
                <div aria-hidden className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-gold-500 via-gold-400 to-azure-400 opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
                <span
                  aria-hidden
                  className="mb-5 flex size-11 items-center justify-center rounded-xl bg-gradient-to-br from-blue-50 to-azure-100
                    text-base font-extrabold text-blue-600 ring-1 ring-blue-100"
                >
                  {str(card, 'icon') || String(index + 1).padStart(2, '0')}
                </span>
                <h3 className="mb-2.5 text-lg font-bold text-blue-700">{str(card, 'title')}</h3>
                {/* Safe: sanitized server-side */}
                <div className="cms-prose text-sm" dangerouslySetInnerHTML={{ __html: str(card, 'bodyHtml') }} />
              </article>
            </Reveal>
          ))}
        </div>
      </Container>
    </section>
  );
}
