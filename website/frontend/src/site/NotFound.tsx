import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Container } from './components/Container';

export function NotFound() {
  const { t } = useTranslation();
  return (
    <Container className="py-24 text-center">
      <p className="font-mono text-5xl font-bold text-blue-200">404</p>
      <h1 className="mt-4 text-2xl font-extrabold text-blue-600">{t('site.notFoundTitle')}</h1>
      <p className="mt-2 text-neutral-600">{t('site.notFoundBody')}</p>
      <Link to="/" className="mt-6 inline-block rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-blue-700">
        {t('site.backHome')}
      </Link>
    </Container>
  );
}
