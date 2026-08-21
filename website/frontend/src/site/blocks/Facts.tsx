import { Container } from '../components/Container';
import { Reveal } from '../components/Reveal';
import { items, str } from './helpers';

/** "Project at a glance" — deep-blue gradient panel with glassy fact tiles. */
export function Facts({ payload }: { payload: Record<string, unknown> }) {
  const heading = str(payload, 'heading');
  return (
    <section className="py-14">
      <Container>
        <Reveal>
          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-blue-700 via-blue-600 to-azure-700 text-white shadow-2xl shadow-blue-600/25">
            <div aria-hidden className="absolute -right-16 -top-20 size-64 rounded-full bg-azure-400/20 blur-3xl" />
            <div aria-hidden className="absolute -bottom-24 left-10 size-72 rounded-full bg-gold-400/15 blur-3xl" />
            {heading && (
              <h2 className="relative px-7 pt-8 text-2xl font-extrabold tracking-tight sm:px-10">
                {heading}
              </h2>
            )}
            <dl className="relative grid gap-4 p-7 sm:grid-cols-2 sm:p-10 sm:pt-7 lg:grid-cols-3">
              {items(payload).map((fact, index) => (
                <div key={index} className="rounded-2xl border border-white/10 bg-white/10 px-5 py-4 backdrop-blur-sm">
                  <dt className="text-[11px] font-bold uppercase tracking-[2px] text-gold-300">{str(fact, 'label')}</dt>
                  <dd className="mt-1.5 text-sm font-semibold leading-snug text-white">{str(fact, 'value')}</dd>
                </div>
              ))}
            </dl>
          </div>
        </Reveal>
      </Container>
    </section>
  );
}
