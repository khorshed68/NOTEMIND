import { useQuery } from '@tanstack/react-query';
import { chatApi, courseApi, documentApi, folderApi, type DocumentScope } from '@/services/endpoints';
import type { DocumentItem } from '@/types';

const isActive = (document: DocumentItem) => document.status === 'UPLOADED' || document.status === 'PROCESSING';

export function useCourses() {
  return useQuery({ queryKey: ['courses'], queryFn: courseApi.list });
}

export function useFolders() {
  return useQuery({ queryKey: ['folders'], queryFn: folderApi.list });
}

/** Polls only while something is still processing, so idle pages make no requests. */
export function useDocuments(scope: DocumentScope = {}, enabled = true) {
  return useQuery({
    queryKey: ['documents', scope],
    queryFn: () => documentApi.list(scope),
    enabled,
    refetchInterval: (query) => (query.state.data?.content.some(isActive) ? 2000 : false),
  });
}

export function useChats(scope: DocumentScope = {}, enabled = true) {
  return useQuery({ queryKey: ['chats', scope], queryFn: () => chatApi.list(scope), enabled });
}
