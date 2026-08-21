import { useTranslation } from 'react-i18next';
import { useSubmissionMutations, useSubmissions } from './adminApi';
import { Button, EmptyState } from './components/ui';
import { Spinner } from '../site/components/Spinner';

export function InboxPage() {
  const { t } = useTranslation();
  const submissions = useSubmissions();
  const { setRead, remove } = useSubmissionMutations();

  if (submissions.isLoading) return <Spinner />;

  return (
    <div>
      <h1 className="mb-5 text-2xl font-extrabold tracking-tight text-blue-600">{t('admin.nav.inbox')}</h1>
      {(submissions.data ?? []).length === 0 ? (
        <EmptyState message={t('admin.common.empty')} />
      ) : (
        <div className="space-y-3">
          {(submissions.data ?? []).map((submission) => (
            <article key={submission.id}
              className={`rounded-xl border bg-white px-5 py-4 shadow-sm ${
                submission.read ? 'border-neutral-200' : 'border-gold-400'
              }`}>
              <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                {!submission.read && <span className="size-2 rounded-full bg-gold-500" aria-label="Unread" />}
                <span className="text-sm font-bold text-neutral-800">{submission.name}</span>
                <a href={`mailto:${submission.email}`} className="text-sm text-azure-500 hover:underline">{submission.email}</a>
                <time className="ml-auto text-xs text-neutral-400" dateTime={submission.createdAt}>
                  {new Date(submission.createdAt).toLocaleString('en-GB')}
                </time>
              </header>
              {submission.subject && <p className="mt-1 text-sm font-semibold text-blue-700">{submission.subject}</p>}
              <p className="mt-2 whitespace-pre-wrap text-sm text-neutral-700">{submission.message}</p>
              <footer className="mt-3 flex gap-2">
                <Button variant="ghost" loading={setRead.isPending}
                  onClick={() => setRead.mutate({ id: submission.id, read: !submission.read })}>
                  {submission.read ? 'Mark unread' : 'Mark read'}
                </Button>
                <Button variant="danger" onClick={() => {
                  if (window.confirm(t('admin.common.confirmDelete'))) remove.mutate(submission.id);
                }}>
                  {t('admin.common.delete')}
                </Button>
              </footer>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
