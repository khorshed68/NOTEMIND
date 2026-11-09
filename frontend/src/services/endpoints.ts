import { api } from './api';
import type {
  ActivityItem,
  AdminStats,
  AttemptResult,
  AuthResponse,
  Chat,
  Course,
  CourseInput,
  DashboardStats,
  Difficulty,
  DocumentItem,
  Folder,
  FolderMember,
  FolderPermission,
  Message,
  Page,
  QuestionType,
  Quiz,
  QuizSummary,
  Role,
  User,
} from '@/types';

const data = <T>(promise: Promise<{ data: T }>) => promise.then((response) => response.data);

export const authApi = {
  login: (body: { email: string; password: string }) => data(api.post<AuthResponse>('/auth/login', body)),
  register: (body: { name: string; email: string; password: string }) =>
    data(api.post<AuthResponse>('/auth/register', body)),
  logout: () => api.post('/auth/logout'),
  updateProfile: (body: { name: string }) => data(api.put<User>('/auth/me', body)),
  changePassword: (body: { currentPassword: string; newPassword: string }) => api.post('/auth/me/password', body),
};

export const courseApi = {
  list: () => data(api.get<Page<Course>>('/courses', { params: { size: 100 } })),
  get: (id: string) => data(api.get<Course>(`/courses/${id}`)),
  create: (body: CourseInput) => data(api.post<Course>('/courses', body)),
  update: (id: string, body: CourseInput) => data(api.put<Course>(`/courses/${id}`, body)),
  remove: (id: string) => api.delete(`/courses/${id}`),
};

export interface DocumentScope {
  courseId?: string;
  folderId?: string;
}

export const documentApi = {
  list: (scope: DocumentScope = {}) => data(api.get<Page<DocumentItem>>('/documents', { params: { ...scope, size: 100 } })),
  upload: (file: File, scope: DocumentScope, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.append('file', file);
    return data(
      api.post<DocumentItem>('/documents', form, {
        params: scope,
        onUploadProgress: (event) => {
          if (event.total) onProgress?.(Math.round((event.loaded / event.total) * 100));
        },
      }),
    );
  },
  remove: (id: string) => api.delete(`/documents/${id}`),
  retry: (id: string) => api.post(`/documents/${id}/retry`),
  preview: (id: string) => data(api.get<Blob>(`/documents/${id}/preview`, { responseType: 'blob' })),
};

export const chatApi = {
  list: (scope: DocumentScope = {}) => data(api.get<Page<Chat>>('/chats', { params: { ...scope, size: 100 } })),
  get: (id: string) => data(api.get<Chat>(`/chats/${id}`)),
  create: (body: DocumentScope & { title?: string }) => data(api.post<Chat>('/chats', body)),
  rename: (id: string, title: string) => data(api.put<Chat>(`/chats/${id}`, { title })),
  remove: (id: string) => api.delete(`/chats/${id}`),
  messages: (id: string) => data(api.get<Page<Message>>(`/chats/${id}/messages`, { params: { size: 200 } })),
};

export interface QuizOptions {
  questionCount: number;
  difficulty: Difficulty;
  types: QuestionType[];
}

export const quizApi = {
  list: () => data(api.get<Page<QuizSummary>>('/quizzes', { params: { size: 100 } })),
  get: (id: string) => data(api.get<Quiz>(`/quizzes/${id}`)),
  forDocument: (id: string, body: QuizOptions) => data(api.post<Quiz>(`/documents/${id}/quiz`, body)),
  forCourse: (id: string, body: QuizOptions) => data(api.post<Quiz>(`/courses/${id}/quiz`, body)),
  submit: (id: string, answers: Record<string, string>) =>
    data(api.post<AttemptResult>(`/quizzes/${id}/attempts`, { answers })),
  latest: (id: string) => data(api.get<AttemptResult | ''>(`/quizzes/${id}/attempts/latest`)),
};

export const folderApi = {
  list: () => data(api.get<Folder[]>('/folders')),
  get: (id: string) => data(api.get<Folder>(`/folders/${id}`)),
  create: (body: { name: string; courseName: string }) => data(api.post<Folder>('/folders', body)),
  remove: (id: string) => api.delete(`/folders/${id}`),
  members: (id: string) => data(api.get<FolderMember[]>(`/folders/${id}/members`)),
  addMember: (id: string, body: { email: string; permission: FolderPermission }) =>
    data(api.post<FolderMember>(`/folders/${id}/members`, body)),
  removeMember: (id: string, userId: string) => api.delete(`/folders/${id}/members/${userId}`),
};

export const dashboardApi = {
  stats: () => data(api.get<DashboardStats>('/dashboard/stats')),
  activity: () => data(api.get<ActivityItem[]>('/dashboard/activity')),
};

export const adminApi = {
  stats: () => data(api.get<AdminStats>('/admin/stats')),
  users: (q: string, page: number) => data(api.get<Page<User>>('/admin/users', { params: { q: q || undefined, page, size: 15 } })),
  setStatus: (id: string, enabled: boolean) => data(api.patch<User>(`/admin/users/${id}/status`, { enabled })),
  setRole: (id: string, role: Role) => data(api.patch<User>(`/admin/users/${id}/role`, { role })),
  folders: () => data(api.get<Page<Folder>>('/admin/folders', { params: { size: 100 } })),
  deleteFolder: (id: string) => api.delete(`/admin/folders/${id}`),
};
