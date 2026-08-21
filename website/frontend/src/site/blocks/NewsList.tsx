import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useNews } from '../../lib/publicApi';
import { mediaUrl } from '../../lib/types';
import { Container } from '../components/Container';
import { PlaceholderImage } from '../components/PlaceholderImage';
import { Reveal } from '../components/Reveal';
import { SectionHeading } from '../components/SectionHeading';
import { num, str } from './helpers';

export function NewsList({ payload }: { payload: Record<string, unknown> }) {
  const { t } = useTranslation();
  const heading = str(payload, 'heading');
  const limit = num(payload, 'limit') ?? 6;
  const news = useNews(limit);

  const posts = news.data ?? [];

  return (
    <section className="py-14">
      <Container>
        {heading && <Reveal><SectionHeading>{heading}</SectionHeading></Reveal>}
        {posts.length === 0 ? (
          <p className="text-sm text-neutral-500">{t('site.noNews')}</p>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {posts.map((post, index) => {
              const cover = mediaUrl(post.coverMediaId);
              return (
                <Reveal key={post.slug} delay={index * 90} className="h-full">
                  <article className="group flex h-full flex-col overflow-hidden rounded-2xl border border-neutral-200/70 bg-white shadow-lg shadow-blue-600/5 transition-all duration-300 hover:-translate-y-1.5 hover:shadow-xl hover:shadow-blue-600/10">
                    <Link to={`/news/${post.slug}`} className="relative block overflow-hidden" tabIndex={-1} aria-hidden>
                      {cover ? (
                        <img src={cover} alt="" loading="lazy"
                          className="aspect-[16/9] w-full object-cover transition-transform duration-500 group-hover:scale-105" />
                      ) : (
                        <PlaceholderImage className="aspect-[16/9] min-h-0 rounded-none" />
                      )}
                      {post.publishedAt && (
                        <time dateTime={post.publishedAt}
                          className="absolute left-3 top-3 rounded-full bg-white/90 px-3 py-1 text-[11px] font-bold uppercase tracking-wide text-blue-700 shadow-sm backdrop-blur">
                          {new Date(post.publishedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                        </time>
                      )}
                    </Link>
                    <div className="flex flex-1 flex-col p-6">
                      <h3 className="text-lg font-bold leading-snug text-blue-700 transition-colors group-hover:text-azure-600">
                        <Link to={`/news/${post.slug}`} className="focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-gold-500">
                          {post.title}
                        </Link>
                      </h3>
                      {post.excerpt && <p className="mt-2.5 flex-1 text-sm leading-relaxed text-neutral-600">{post.excerpt}</p>}
                      <Link to={`/news/${post.slug}`}
                        className="mt-4 inline-flex items-center gap-1.5 text-sm font-bold text-azure-500 hover:text-azure-600">
                        {t('site.readMore')}
                        <span aria-hidden className="transition-transform group-hover:translate-x-1">→</span>
                      </Link>
                    </div>
                  </article>
                </Reveal>
              );
            })}
          </div>
        )}
      </Container>
    </section>
  );
}
