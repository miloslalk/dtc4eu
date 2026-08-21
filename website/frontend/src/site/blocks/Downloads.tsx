import { Container } from '../components/Container';
import { SectionHeading } from '../components/SectionHeading';
import { mediaUrl } from '../../lib/types';
import { items, num, str } from './helpers';

export function Downloads({ payload }: { payload: Record<string, unknown> }) {
  const heading = str(payload, 'heading');
  return (
    <section className="py-14">
      <Container>
        {heading && <SectionHeading>{heading}</SectionHeading>}
        <ul className="space-y-3">
          {items(payload).map((item, index) => {
            const url = mediaUrl(num(item, 'mediaId'));
            return (
              <li key={index}>
                <a
                  href={url ?? '#'}
                  target="_blank"
                  rel="noreferrer"
                  className="group flex items-center gap-4 rounded-2xl border border-neutral-200/70 bg-white p-4 shadow-lg shadow-blue-600/5 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-blue-600/10"
                >
                  <span aria-hidden className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-gold-50 to-gold-100 text-lg ring-1 ring-gold-200">
                    📄
                  </span>
                  <span>
                    <span className="block text-sm font-bold text-blue-700">{str(item, 'title')}</span>
                    {str(item, 'description') && (
                      <span className="block text-xs text-neutral-500">{str(item, 'description')}</span>
                    )}
                  </span>
                </a>
              </li>
            );
          })}
        </ul>
      </Container>
    </section>
  );
}
