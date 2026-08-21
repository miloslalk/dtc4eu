import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { usePage } from '../lib/publicApi';
import { PageRenderer } from './PageRenderer';
import { NotFound } from './NotFound';
import { Spinner } from './components/Spinner';

const DEFAULT_TITLE = 'DTC4EU — Democratic Therapeutic Communities for Social Inclusion';

export function PageView({ slug }: { slug?: string }) {
  const params = useParams();
  const { t } = useTranslation();
  const effectiveSlug = slug ?? params.slug ?? 'home';
  const page = usePage(effectiveSlug);

  useEffect(() => {
    if (!page.data) return;
    document.title = page.data.seoTitle || `${page.data.title} — DTC4EU`;
    const meta = document.querySelector('meta[name="description"]');
    if (meta && page.data.seoDescription) {
      meta.setAttribute('content', page.data.seoDescription);
    }
    return () => {
      document.title = DEFAULT_TITLE;
    };
  }, [page.data]);

  if (page.isLoading) {
    return <Spinner label={t('site.loading')} />;
  }
  if (page.isError || !page.data) {
    return <NotFound />;
  }
  return <PageRenderer blocks={page.data.blocks} />;
}
