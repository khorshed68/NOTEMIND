import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FolderOpen, Plus, Users } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { EmptyState, ErrorState, ListSkeleton, PageHeader } from '@/components/common';
import { Button } from '@/components/ui/button';
import { Badge, Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Input, Label } from '@/components/ui/input';
import { useAuth } from '@/contexts/AuthContext';
import { useFolders } from '@/hooks/queries';
import { errorMessage } from '@/services/api';
import { folderApi } from '@/services/endpoints';
import { plural } from '@/utils/format';

export function FoldersPage() {
  const { user } = useAuth();
  const folders = useFolders();
  const queryClient = useQueryClient();
  const canCreate = user?.role === 'CLASS_REP' || user?.role === 'ADMIN';
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [courseName, setCourseName] = useState('');

  const create = useMutation({
    mutationFn: () => folderApi.create({ name: name.trim(), courseName: courseName.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['folders'] });
      setOpen(false);
      setName('');
      setCourseName('');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <>
      <PageHeader
        title="Shared Folders"
        description="Course material shared by your class representatives."
        actions={canCreate ? <Button onClick={() => setOpen(true)}><Plus /> New folder</Button> : undefined}
      />
      {folders.isLoading && <ListSkeleton />}
      {folders.isError && <ErrorState message={errorMessage(folders.error)} onRetry={() => folders.refetch()} />}
      {folders.data?.length === 0 && (
        <EmptyState
          icon={FolderOpen}
          title="No shared folders"
          description={canCreate ? 'Create a folder and add students to share material with your class.' : 'Folders shared with you by a class representative will appear here.'}
        />
      )}
      {!!folders.data?.length && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {folders.data.map((folder) => (
            <Card key={folder.id} className="transition-shadow hover:shadow-pop">
              <Link to={`/folders/${folder.id}`} className="block rounded-lg p-4">
                <div className="flex items-center justify-between">
                  <span className="grid size-9 place-items-center rounded-md bg-primary-soft text-primary"><FolderOpen className="size-4" /></span>
                  <Badge tone={folder.owned ? 'primary' : 'neutral'}>{folder.owned ? 'Owner' : folder.canManage ? 'Manager' : 'Viewer'}</Badge>
                </div>
                <h3 className="mt-3 truncate text-sm font-semibold">{folder.name}</h3>
                <p className="truncate text-[13px] text-muted-foreground">{folder.courseName} · by {folder.ownerName}</p>
                <p className="mt-2 flex items-center gap-1.5 text-[13px] text-muted-foreground">
                  <Users className="size-3.5" /> {plural(folder.memberCount, 'member')} · {plural(folder.documentCount, 'document')}
                </p>
              </Link>
            </Card>
          ))}
        </div>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent title="Create a shared folder">
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim() && courseName.trim()) create.mutate();
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="folder-name">Folder name</Label>
              <Input id="folder-name" placeholder="Section A materials" value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="folder-course">Course name</Label>
              <Input id="folder-course" placeholder="Computer Networks" value={courseName} onChange={(event) => setCourseName(event.target.value)} />
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
              <Button type="submit" loading={create.isPending} disabled={!name.trim() || !courseName.trim()}>Create</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
