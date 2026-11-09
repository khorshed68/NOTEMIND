import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bot, ChevronLeft, ChevronRight, Database, FileText, HardDrive, Layers, MessageSquare, Trash2, Users } from 'lucide-react';
import { lazy, Suspense, useState } from 'react';
import { toast } from 'sonner';
import { ConfirmDialog, ErrorState, ListSkeleton, PageHeader, StatCard } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Badge, Card, CardContent, CardHeader, CardTitle, Skeleton } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/contexts/AuthContext';
import { errorMessage } from '@/services/api';
import { adminApi } from '@/services/endpoints';
import type { Folder, Role } from '@/types';
import { formatBytes, formatDate, plural } from '@/utils/format';

const QuestionsChart = lazy(() => import('@/components/dashboard/QuestionsChart').then((module) => ({ default: module.QuestionsChart })));

const ROLE_OPTIONS = [
  { value: 'STUDENT', label: 'Student' },
  { value: 'CLASS_REP', label: 'Class rep' },
  { value: 'ADMIN', label: 'Admin' },
];

function UsersTable() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const users = useQuery({ queryKey: ['admin', 'users', search, page], queryFn: () => adminApi.users(search, page), placeholderData: (previous) => previous });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin'] });
  const onError = (error: unknown) => toast.error(errorMessage(error));
  const setStatus = useMutation({ mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => adminApi.setStatus(id, enabled), onSuccess: refresh, onError });
  const setRole = useMutation({ mutationFn: ({ id, role }: { id: string; role: Role }) => adminApi.setRole(id, role), onSuccess: refresh, onError });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Users</CardTitle>
        <Input
          aria-label="Search users"
          placeholder="Search name or email"
          className="max-w-60"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
      </CardHeader>
      <CardContent className="overflow-x-auto">
        {users.isError && <ErrorState message={errorMessage(users.error)} onRetry={() => users.refetch()} />}
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b text-left text-xs text-muted-foreground">
              <th className="pb-2 font-medium">User</th>
              <th className="pb-2 font-medium">Role</th>
              <th className="pb-2 font-medium">Joined</th>
              <th className="pb-2 font-medium">Status</th>
              <th className="pb-2" />
            </tr>
          </thead>
          <tbody className="divide-y">
            {users.data?.content.map((user) => (
              <tr key={user.id}>
                <td className="py-2.5 pr-3">
                  <p className="font-medium">{user.name}</p>
                  <p className="text-xs text-muted-foreground">{user.email}</p>
                </td>
                <td className="w-40 pr-3">
                  <Select
                    ariaLabel={`Role for ${user.name}`}
                    value={user.role}
                    options={ROLE_OPTIONS}
                    disabled={user.id === me?.id}
                    onChange={(role) => setRole.mutate({ id: user.id, role: role as Role })}
                  />
                </td>
                <td className="whitespace-nowrap pr-3 text-muted-foreground">{formatDate(user.createdAt)}</td>
                <td className="pr-3"><Badge tone={user.enabled ? 'success' : 'danger'}>{user.enabled ? 'Active' : 'Disabled'}</Badge></td>
                <td className="text-right">
                  <Button variant="outline" size="sm" disabled={user.id === me?.id} onClick={() => setStatus.mutate({ id: user.id, enabled: !user.enabled })}>
                    {user.enabled ? 'Disable' : 'Enable'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {users.data && users.data.totalPages > 1 && (
          <div className="mt-3 flex items-center justify-end gap-2 text-[13px] text-muted-foreground">
            <Button variant="ghost" size="icon-sm" aria-label="Previous page" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft /></Button>
            Page {page + 1} of {users.data.totalPages}
            <Button variant="ghost" size="icon-sm" aria-label="Next page" disabled={page + 1 >= users.data.totalPages} onClick={() => setPage(page + 1)}><ChevronRight /></Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function SharedResources() {
  const queryClient = useQueryClient();
  const folders = useQuery({ queryKey: ['admin', 'folders'], queryFn: adminApi.folders });
  const [deleting, setDeleting] = useState<Folder>();
  return (
    <Card>
      <CardHeader><CardTitle>Shared folders</CardTitle></CardHeader>
      <CardContent>
        {folders.data?.content.length === 0 && <p className="text-sm text-muted-foreground">No shared folders exist.</p>}
        <ul className="divide-y">
          {folders.data?.content.map((folder) => (
            <li key={folder.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{folder.name}</p>
                <p className="truncate text-xs text-muted-foreground">{folder.courseName} · {folder.ownerName} · {plural(folder.documentCount, 'document')} · {plural(folder.memberCount, 'member')}</p>
              </div>
              <Button variant="ghost" size="icon-sm" aria-label={`Delete ${folder.name}`} onClick={() => setDeleting(folder)}><Trash2 /></Button>
            </li>
          ))}
        </ul>
      </CardContent>
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title="Delete this shared folder?"
        description={`"${deleting?.name}" and its materials will be permanently removed for every member.`}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await adminApi.deleteFolder(deleting.id);
            queryClient.invalidateQueries({ queryKey: ['admin'] });
          } catch (error) {
            toast.error(errorMessage(error));
          }
        }}
      />
    </Card>
  );
}

export function AdminPage() {
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: adminApi.stats });
  return (
    <>
      <PageHeader title="Admin" description="Users, usage and document processing across NoteMind." />
      {stats.isError && <ErrorState message={errorMessage(stats.error)} onRetry={() => stats.refetch()} />}
      {stats.isLoading && <ListSkeleton />}
      {stats.data && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard icon={Users} label="Users" value={`${stats.data.enabledUsers} / ${stats.data.totalUsers} active`} />
            <StatCard icon={FileText} label="Documents" value={stats.data.totalDocuments} />
            <StatCard icon={Layers} label="Pages" value={stats.data.totalPages} />
            <StatCard icon={Database} label="Indexed chunks" value={stats.data.totalChunks} />
            <StatCard icon={MessageSquare} label="Questions" value={stats.data.totalQuestions} />
            <StatCard icon={MessageSquare} label="Chats" value={stats.data.totalChats} />
            <StatCard icon={Layers} label="Quizzes" value={stats.data.totalQuizzes} />
            <StatCard icon={HardDrive} label="Storage" value={formatBytes(stats.data.storageBytes)} />
          </div>
          <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
            <Card>
              <CardHeader><CardTitle>Questions asked, last 14 days</CardTitle></CardHeader>
              <CardContent className="pt-3">
                <Suspense fallback={<Skeleton className="h-44" />}>
                  <QuestionsChart data={stats.data.questionsPerDay} />
                </Suspense>
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>System</CardTitle></CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div>
                  <p className="mb-1.5 text-xs font-medium text-muted-foreground">Document processing</p>
                  {Object.entries(stats.data.documentsByStatus).map(([status, count]) => (
                    <div key={status} className="flex justify-between py-0.5">
                      <span className="capitalize">{status.toLowerCase()}</span>
                      <span className="tabular-nums">{count}</span>
                    </div>
                  ))}
                </div>
                <div>
                  <p className="mb-1.5 text-xs font-medium text-muted-foreground">Users by role</p>
                  {Object.entries(stats.data.usersByRole).map(([role, count]) => (
                    <div key={role} className="flex justify-between py-0.5">
                      <span className="capitalize">{role.toLowerCase().replace('_', ' ')}</span>
                      <span className="tabular-nums">{count}</span>
                    </div>
                  ))}
                </div>
                <div className="flex items-center justify-between border-t pt-3">
                  <span className="flex items-center gap-1.5"><Bot className="size-4" /> Chat model</span>
                  <Badge tone={stats.data.chatModelConfigured ? 'success' : 'warning'}>{stats.data.chatModelConfigured ? 'Configured' : 'Not configured'}</Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5"><Database className="size-4" /> Embeddings</span>
                  <Badge tone={stats.data.embeddingModelConfigured ? 'success' : 'warning'}>{stats.data.embeddingModelConfigured ? 'Configured' : 'Not configured'}</Badge>
                </div>
              </CardContent>
            </Card>
          </div>
        </>
      )}
      <div className="mt-6 space-y-6">
        <UsersTable />
        <SharedResources />
      </div>
    </>
  );
}
