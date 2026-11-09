import { FileText } from 'lucide-react';
import { Link } from 'react-router-dom';
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from '@/components/common';
import { DocumentList } from '@/components/documents/DocumentList';
import { Button } from '@/components/ui/button';
import { useDocuments } from '@/hooks/queries';
import { errorMessage } from '@/services/api';

export function DocumentsPage() {
  const documents = useDocuments();
  return (
    <>
      <PageHeader title="All Documents" description="Everything you have uploaded or that has been shared with you." />
      {documents.isLoading && <ListSkeleton />}
      {documents.isError && <ErrorState message={errorMessage(documents.error)} onRetry={() => documents.refetch()} />}
      {documents.data?.content.length === 0 && (
        <EmptyState
          icon={FileText}
          title="No documents yet"
          description="Open a course and upload your lecture notes in PDF format."
          action={<Button asChild><Link to="/courses">Go to courses</Link></Button>}
        />
      )}
      {!!documents.data?.content.length && <DocumentList documents={documents.data.content} />}
    </>
  );
}
