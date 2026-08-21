import { Link } from 'react-router-dom';
import { Container } from '../components/Container';
import { MediaImg } from '../components/MediaImg';
import { Reveal } from '../components/Reveal';
import { num, str } from './helpers';

export function Hero({ payload }: { payload: Record<string, unknown> }) {
  const kicker = str(payload, 'kicker');
  const subheading = str(payload, 'subheading');
  const ctaLabel = str(payload, 'ctaLabel');
  const ctaHref = str(payload, 'ctaHref');
  const imageMediaId = num(payload, 'imageMediaId');
  const hasImage = imageMediaId !== null;

  return (
    <section className="relative overflow-hidden border-b border-neutral-200">
      {/* layered backdrop: dotted grid + drifting brand-colour blobs */}
      <div aria-hidden className="bg-dots absolute inset-0 opacity-40" />
      <div aria-hidden className="animate-float-slow absolute -top-24 right-[-10%] size-[26rem] rounded-full bg-azure-200/50 blur-3xl" />
      <div aria-hidden className="animate-float-slower absolute -bottom-32 left-[-8%] size-[22rem] rounded-full bg-gold-200/60 blur-3xl" />
      <div aria-hidden className="absolute left-1/3 top-0 size-72 rounded-full bg-blue-200/40 blur-3xl" />

      <Container className={`relative grid items-center gap-10 py-16 sm:py-24 ${hasImage ? 'lg:grid-cols-[1.15fr_1fr]' : ''}`}>
        <div className={hasImage ? '' : 'mx-auto max-w-3xl text-center'}>
          <Reveal>
            {kicker && (
              <span className="mb-5 inline-flex items-center gap-2 rounded-full border border-gold-300 bg-white/80 px-4 py-1.5 text-xs font-bold uppercase tracking-[2px] text-blue-700 shadow-sm backdrop-blur">
                <span aria-hidden className="size-1.5 rounded-full bg-gold-500" />
                {kicker}
              </span>
            )}
          </Reveal>
          <Reveal delay={90}>
            <h1 className="text-gradient text-4xl font-extrabold leading-[1.08] tracking-tight sm:text-5xl lg:text-6xl">
              {str(payload, 'heading')}
            </h1>
          </Reveal>
          {subheading && (
            <Reveal delay={180}>
              <p className={`mt-6 text-lg leading-relaxed text-neutral-700 sm:text-xl ${hasImage ? 'max-w-2xl' : 'mx-auto max-w-2xl'}`}>
                {subheading}
              </p>
            </Reveal>
          )}
          {ctaLabel && ctaHref && (
            <Reveal delay={270}>
              <Link
                to={ctaHref}
                className="group mt-9 inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-azure-600 px-7 py-3.5 text-sm font-bold text-white
                  shadow-lg shadow-blue-600/25 transition-all hover:shadow-xl hover:shadow-blue-600/30 hover:brightness-110
                  focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500"
              >
                {ctaLabel}
                <span aria-hidden className="transition-transform group-hover:translate-x-1">→</span>
              </Link>
            </Reveal>
          )}
        </div>
        {hasImage && (
          <Reveal delay={200}>
            <div className="relative">
              <div aria-hidden className="absolute -inset-3 rounded-3xl bg-gradient-to-br from-gold-300/60 to-azure-300/40 blur-lg" />
              <MediaImg mediaId={imageMediaId} className="relative max-h-96 rounded-2xl shadow-2xl shadow-blue-600/20" />
            </div>
          </Reveal>
        )}
      </Container>
    </section>
  );
}
