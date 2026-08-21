import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useNewsDetail } from '../lib/publicApi';
import { mediaUrl } from '../lib/types';
import { Container } from './components/Container';
import { Spinner } from './components/Spinner';
import { NotFound } from './NotFound';

export function NewsDetailPage() {
  const { slug = '' } = useParams();
  const { t } = useTranslation();
  const post = useNewsDetail(slug);

  useEffect(() => {
    if (post.data) {
      document.title = `${post.data.title} — DTC4EU`;
    }
  }, [post.data]);

  if (post.isLoading) {
    return <Spinner label={t('site.loading')} />;
  }
  if (post.isError || !post.data) {
    return <NotFound />;
  }

  const cover = mediaUrl(post.data.coverMediaId);

  return (
    <article className="py-10">
      <Container className="max-w-3xl">
        <Link to="/resources" className="text-sm font-semibold text-azure-500 hover:underline">
          ← {t('site.backToNews')}
        </Link>
        <p className="mt-6 text-xs font-bold uppercase tracking-[3px] text-azure-500">{t('site.news')}</p>
        <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-blue-600 sm:text-4xl">{post.data.title}</h1>
        {post.data.publishedAt && (
          <time dateTime={post.data.publishedAt} className="mt-2 block text-sm text-neutral-500">
            {new Date(post.data.publishedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })}
          </time>
        )}
        {cover && <img src={cover} alt="" className="mt-6 w-full rounded-2xl object-cover" />}
        {/* Safe: sanitized server-side */}
        <div className="cms-prose mt-6" dangerouslySetInnerHTML={{ __html: post.data.bodyHtml }} />
      </Container>
    </article>
  );
}
