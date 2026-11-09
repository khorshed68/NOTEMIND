import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Sparkles, Trash2, Upload, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ConfirmDialog, EmptyState, ErrorState, ListSkeleton, PageHeader } from '@/components/common';
import { DocumentList } from '@/components/documents/DocumentList';
import { UploadDialog } from '@/components/documents/UploadDialog';
import { Button } from '@/components/ui/button';
import { Badge, Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useDocuments } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { folderApi } from '@/services/endpoints';
import type { FolderPermission } from '@/types';

export function FolderDetailPage() {
  const { folderId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const folder = useQuery({ queryKey: ['folders', folderId], queryFn: () => folderApi.get(folderId) });
  const documents = useDocuments({ folderId });
  const canManage = !!folder.data?.canManage;
  const members = useQuery({ queryKey: ['folders', folderId, 'members'], queryFn: () => folderApi.members(folderId), enabled: canManage });

  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [email, setEmail] = useState('');
  const [permission, setPermission] = useState<FolderPermission>('VIEW');

  const refreshMembers = () => {
    queryClient.invalidateQueries({ queryKey: ['folders', folderId] });
  };

  const add = useMutation({
    mutationFn: () => folderApi.addMember(folderId, { email: email.trim(), permission }),
    onSuccess: () => {
      setEmail('');
      refreshMembers();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const removeMember = useMutation({
    mutationFn: (userId: string) => folderApi.removeMember(folderId, userId),
    onSuccess: refreshMembers,
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (folder.isError) return <ErrorState message={errorMessage(folder.error)} onRetry={() => folder.refetch()} />;
  if (folder.isLoading || !folder.data) return <ListSkeleton />;

  return (
    <>
      <Link to="/folders" className="mb-3 inline-flex items-center gap-1 text-[13px] text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-3.5" /> Shared Folders
      </Link>
      <PageHeader
        title={folder.data.name}
        description={`${folder.data.courseName} · shared by ${folder.data.ownerName}`}
        actions={
          <>
            {folder.data.owned && <Button variant="outline" onClick={() => setDeleting(true)}><Trash2 /> Delete</Button>}
            {canManage && <Button variant="outline" onClick={() => setUploading(true)}><Upload /> Upload</Button>}
            <Button asChild><Link to={`/chat?folder=${folderId}`}><Sparkles /> Ask AI</Link></Button>
          </>
        }
      />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section aria-label="Documents">
          {documents.isLoading && <ListSkeleton rows={3} />}
          {documents.data?.content.length === 0 && (
            <EmptyState icon={Upload} title="No materials yet" description={canManage ? 'Upload PDFs for this class.' : 'Nothing has been shared here yet.'} />
          )}
          {!!documents.data?.content.length && <DocumentList documents={documents.data.content} />}
        </section>
        {canManage && (
          <Card className="h-fit">
            <CardHeader><CardTitle>Members</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              <form
                className="space-y-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (email.trim()) add.mutate();
                }}
              >
                <Input aria-label="Student email" type="email" placeholder="student@university.edu" value={email} onChange={(event) => setEmail(event.target.value)} />
                <div className="flex gap-2">
                  <Select
                    ariaLabel="Permission"
                    value={permission}
                    onChange={(value) => setPermission(value as FolderPermission)}
                    options={[{ value: 'VIEW', label: 'Can view' }, { value: 'MANAGE', label: 'Can manage' }]}
                  />
                  <Button type="submit" loading={add.isPending} disabled={!email.trim()}><UserPlus /> Add</Button>
                </div>
              </form>
              {members.data?.length === 0 && <p className="text-sm text-muted-foreground">No members yet.</p>}
              <ul className="divide-y">
                {members.data?.map((member) => (
                  <li key={member.userId} className="flex items-center justify-between gap-2 py-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm">{member.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{member.email}</p>
                    </div>
                    <Badge>{member.permission === 'MANAGE' ? 'Manager' : 'Viewer'}</Badge>
                    <Button variant="ghost" size="icon-sm" aria-label={`Remove ${member.name}`} onClick={() => removeMember.mutate(member.userId)}><Trash2 /></Button>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}
      </div>
      <UploadDialog open={uploading} onOpenChange={setUploading} scope={{ folderId }} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Delete this folder?"
        description="All materials in it will be permanently removed for every member."
        onConfirm={async () => {
          try {
            await folderApi.remove(folderId);
            queryClient.invalidateQueries({ queryKey: ['folders'] });
            navigate('/folders', { replace: true });
          } catch (error) {
            toast.error(errorMessage(error));
          }
        }}
      />
    </>
  );
}
