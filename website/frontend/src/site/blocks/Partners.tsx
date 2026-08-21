import { Container } from '../components/Container';
import { Reveal } from '../components/Reveal';
import { SectionHeading } from '../components/SectionHeading';
import { mediaUrl } from '../../lib/types';
import { items, num, str } from './helpers';

export function Partners({ payload }: { payload: Record<string, unknown> }) {
  const heading = str(payload, 'heading');
  return (
    <section className="py-14">
      <Container>
        {heading && <Reveal><SectionHeading>{heading}</SectionHeading></Reveal>}
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {items(payload).map((partner, index) => {
            const name = str(partner, 'name');
            const url = str(partner, 'url');
            const logo = mediaUrl(num(partner, 'logoMediaId'));
            const body = (
              <div className="group flex h-full items-center gap-4 rounded-2xl border border-neutral-200/70 bg-white p-5
                shadow-lg shadow-blue-600/5 transition-all duration-300 hover:-translate-y-1 hover:border-azure-200 hover:shadow-xl hover:shadow-blue-600/10">
                {logo ? (
                  <img src={logo} alt="" className="size-14 shrink-0 rounded-xl object-contain" loading="lazy" />
                ) : (
                  <span
                    aria-hidden
                    className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-50 to-azure-100
                      text-lg font-extrabold text-blue-500 ring-1 ring-blue-100"
                  >
                    {name.charAt(0) || '?'}
                  </span>
                )}
                <span>
                  <span className="block text-sm font-bold text-neutral-800 transition-colors group-hover:text-blue-700">{name}</span>
                  {str(partner, 'country') && (
                    <span className="mt-0.5 block text-xs font-medium text-neutral-500">{str(partner, 'country')}</span>
                  )}
                </span>
              </div>
            );
            return (
              <Reveal key={index} delay={index * 70} className="h-full">
                {url ? (
                  <a href={url} target="_blank" rel="noreferrer" className="block h-full">{body}</a>
                ) : (
                  <div className="h-full">{body}</div>
                )}
              </Reveal>
            );
          })}
        </div>
      </Container>
    </section>
  );
}
