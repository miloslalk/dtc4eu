import { Link } from 'react-router-dom';
import { Container } from '../components/Container';
import { Reveal } from '../components/Reveal';
import { safeHref, str } from './helpers';

export function Cta({ payload }: { payload: Record<string, unknown> }) {
  const bodyHtml = str(payload, 'bodyHtml');
  return (
    <section className="py-14">
      <Container>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-600 to-azure-600 px-6 py-12 text-center text-white shadow-2xl shadow-blue-600/25 sm:px-12">
            <div aria-hidden className="bg-dots absolute inset-0 opacity-10" />
            <div aria-hidden className="absolute -left-10 -top-16 size-52 rounded-full bg-gold-400/25 blur-3xl" />
            <div aria-hidden className="absolute -bottom-20 -right-10 size-64 rounded-full bg-white/10 blur-3xl" />
            <h2 className="relative text-2xl font-extrabold tracking-tight sm:text-3xl">{str(payload, 'heading')}</h2>
            {bodyHtml && (
              /* Safe: sanitized server-side */
              <div
                className="relative mx-auto mt-3 max-w-xl text-sm leading-relaxed text-blue-50 [&_a]:text-gold-300 [&_a]:underline"
                dangerouslySetInnerHTML={{ __html: bodyHtml }}
              />
            )}
            <Link
              to={safeHref(str(payload, 'buttonHref')) || '#'}
              className="group relative mt-7 inline-flex items-center gap-2 rounded-xl bg-white px-7 py-3.5 text-sm font-bold text-blue-700
                shadow-lg transition-all hover:shadow-xl hover:brightness-95
                focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500"
            >
              {str(payload, 'buttonLabel')}
              <span aria-hidden className="transition-transform group-hover:translate-x-1">→</span>
            </Link>
          </div>
        </Reveal>
      </Container>
    </section>
  );
}
