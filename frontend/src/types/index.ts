export type Role = 'STUDENT' | 'CLASS_REP' | 'ADMIN';
export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuestionType = 'MCQ' | 'TRUE_FALSE' | 'SHORT_ANSWER';
export type FolderPermission = 'VIEW' | 'MANAGE';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
}

export interface Course {
  id: string;
  name: string;
  courseCode: string | null;
  description: string | null;
  documentCount: number;
  pageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CourseInput {
  name: string;
  courseCode?: string;
  description?: string;
}

export interface DocumentItem {
  id: string;
  courseId: string | null;
  folderId: string | null;
  filename: string;
  fileSize: number;
  pageCount: number | null;
  status: DocumentStatus;
  errorMessage: string | null;
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SourceRef {
  label: string;
  documentId: string;
  documentName: string;
  pageNumber: number;
  chunkId: string;
  snippet: string;
  similarity: number;
  cited: boolean;
}

export interface Chat {
  id: string;
  courseId: string | null;
  folderId: string | null;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Message {
  id: string;
  chatId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  sources: SourceRef[];
  createdAt: string;
}

export interface SendMessageResponse {
  userMessage: Message;
  assistantMessage: Message;
}

export interface QuizQuestion {
  id: string;
  type: QuestionType;
  question: string;
  options: string[];
}

export interface Quiz {
  id: string;
  documentId: string | null;
  courseId: string | null;
  title: string;
  difficulty: Difficulty;
  questions: QuizQuestion[];
  createdAt: string;
}

export interface QuizSummary {
  id: string;
  title: string;
  difficulty: Difficulty;
  questionCount: number;
  lastScore: number | null;
  lastTotal: number | null;
  createdAt: string;
}

export interface GradedQuestion {
  id: string;
  type: QuestionType;
  question: string;
  options: string[];
  yourAnswer: string;
  correctAnswer: string;
  correct: boolean;
  explanation: string;
  documentId: string;
  documentName: string;
  pageNumber: number;
}

export interface AttemptResult {
  attemptId: string;
  quizId: string;
  score: number;
  total: number;
  questions: GradedQuestion[];
  createdAt: string;
}

export interface Folder {
  id: string;
  name: string;
  courseName: string;
  ownerId: string;
  ownerName: string;
  owned: boolean;
  canManage: boolean;
  documentCount: number;
  memberCount: number;
  createdAt: string;
}

export interface FolderMember {
  userId: string;
  name: string;
  email: string;
  permission: FolderPermission;
  addedAt: string;
}

export interface DailyCount {
  date: string;
  count: number;
}

export interface DashboardStats {
  courses: number;
  documents: number;
  pages: number;
  questions: number;
  chats: number;
  quizzes: number;
  questionsPerDay: DailyCount[];
}

export interface ActivityItem {
  type: 'DOCUMENT' | 'CHAT' | 'QUIZ' | 'COURSE';
  id: string;
  title: string;
  parentId: string | null;
  at: string;
}

export interface AdminStats {
  totalUsers: number;
  enabledUsers: number;
  usersByRole: Record<string, number>;
  totalDocuments: number;
  documentsByStatus: Record<string, number>;
  totalPages: number;
  totalChunks: number;
  storageBytes: number;
  totalChats: number;
  totalQuestions: number;
  totalQuizzes: number;
  questionsPerDay: DailyCount[];
  chatModelConfigured: boolean;
  embeddingModelConfigured: boolean;
}

export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
