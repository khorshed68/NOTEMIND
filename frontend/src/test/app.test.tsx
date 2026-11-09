import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UploadDialog } from '@/components/documents/UploadDialog';
import { CoursesPage } from '@/pages/CoursesPage';
import { DashboardPage } from '@/pages/DashboardPage';
import type { Course, DocumentItem } from '@/types';
import { page, renderApp } from './utils';

const api = vi.hoisted(() => ({
  courseList: vi.fn(),
  courseCreate: vi.fn(),
  courseRemove: vi.fn(),
  documentList: vi.fn(),
  upload: vi.fn(),
  chatList: vi.fn(),
  stats: vi.fn(),
  activity: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({ useAuth: () => ({ user: { name: 'Nadia Karim', role: 'STUDENT' } }) }));
vi.mock('@/components/pdf/PdfViewerDialog', () => ({ PdfViewerDialog: () => null }));
vi.mock('@/components/dashboard/QuestionsChart', () => ({ QuestionsChart: () => <div>chart</div> }));
vi.mock('@/services/endpoints', () => ({
  courseApi: { list: api.courseList, create: api.courseCreate, update: vi.fn(), remove: api.courseRemove, get: vi.fn() },
  documentApi: { list: api.documentList, upload: api.upload, remove: vi.fn(), retry: vi.fn(), preview: vi.fn() },
  chatApi: { list: api.chatList },
  folderApi: { list: vi.fn() },
  dashboardApi: { stats: api.stats, activity: api.activity },
}));

const course: Course = {
  id: 'c1', name: 'Compiler Design', courseCode: 'CSE 3101', description: null, documentCount: 3, pageCount: 120,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

const doc: DocumentItem = {
  id: 'd1', courseId: 'c1', folderId: null, filename: 'Lecture 1.pdf', fileSize: 2048, pageCount: 10, status: 'READY',
  errorMessage: null, canManage: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
  api.courseList.mockResolvedValue(page([course]));
  api.documentList.mockResolvedValue(page([doc]));
  api.chatList.mockResolvedValue(page([]));
  api.stats.mockResolvedValue({ courses: 1, documents: 3, pages: 120, questions: 42, chats: 7, quizzes: 2, questionsPerDay: [] });
  api.activity.mockResolvedValue([]);
});

describe('DashboardPage', () => {
  it('greets the user and shows real statistics', async () => {
    renderApp(<DashboardPage />);
    expect(screen.getByText('Welcome back, Nadia')).toBeInTheDocument();
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('Pages processed')).toBeInTheDocument();
    expect(await screen.findByText('Compiler Design')).toBeInTheDocument();
    expect(await screen.findByText('Lecture 1.pdf')).toBeInTheDocument();
  });

  it('reports a failed statistics request and can retry', async () => {
    api.stats.mockRejectedValueOnce(new Error('Stats are unavailable'));
    renderApp(<DashboardPage />);
    expect(await screen.findByText('Stats are unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('42')).toBeInTheDocument();
  });
});

describe('CoursesPage', () => {
  it('lists courses', async () => {
    renderApp(<CoursesPage />);
    expect(await screen.findByText('Compiler Design')).toBeInTheDocument();
    expect(screen.getByText('CSE 3101')).toBeInTheDocument();
  });

  it('shows an empty state when there are none', async () => {
    api.courseList.mockResolvedValue(page([]));
    renderApp(<CoursesPage />);
    expect(await screen.findByText('Create your first course')).toBeInTheDocument();
  });

  it('creates a course', async () => {
    api.courseCreate.mockResolvedValue(course);
    renderApp(<CoursesPage />);
    await screen.findByText('Compiler Design');
    await userEvent.click(screen.getByRole('button', { name: 'New course' }));
    await userEvent.type(await screen.findByLabelText('Course name'), 'Networks');
    await userEvent.type(screen.getByLabelText('Course code'), 'CSE 3205');
    await userEvent.click(screen.getByRole('button', { name: 'Create course' }));
    await waitFor(() => expect(api.courseCreate).toHaveBeenCalledWith(expect.objectContaining({ name: 'Networks', courseCode: 'CSE 3205' })));
  });

  it('requires a course name', async () => {
    renderApp(<CoursesPage />);
    await screen.findByText('Compiler Design');
    await userEvent.click(screen.getByRole('button', { name: 'New course' }));
    await screen.findByLabelText('Course name');
    await userEvent.click(screen.getByRole('button', { name: 'Create course' }));
    expect(await screen.findByText('Course name is required')).toBeInTheDocument();
    expect(api.courseCreate).not.toHaveBeenCalled();
  });

  it('confirms before deleting', async () => {
    api.courseRemove.mockResolvedValue({});
    renderApp(<CoursesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Compiler Design' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    expect(await screen.findByText('Delete this course?')).toBeInTheDocument();
    expect(api.courseRemove).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(api.courseRemove).toHaveBeenCalledWith('c1'));
  });
});

describe('UploadDialog', () => {
  const pdf = new File(['%PDF-1.7'], 'notes.pdf', { type: 'application/pdf' });

  it('uploads to the given course and shows success', async () => {
    api.upload.mockImplementation(async (_file, _scope, onProgress) => {
      onProgress(50);
      return doc;
    });
    renderApp(<UploadDialog open onOpenChange={() => undefined} scope={{ courseId: 'c1' }} />);
    await userEvent.upload(screen.getByTestId('file-input'), pdf);
    await waitFor(() => expect(api.upload).toHaveBeenCalledWith(pdf, { courseId: 'c1' }, expect.any(Function)));
    expect(await screen.findByLabelText('Uploaded')).toBeInTheDocument();
  });

  it('rejects non-PDF files without calling the server', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderApp(<UploadDialog open onOpenChange={() => undefined} scope={{ courseId: 'c1' }} />);
    await user.upload(screen.getByTestId('file-input'), new File(['x'], 'photo.png', { type: 'image/png' }));
    expect(await screen.findByText('Only PDF files are accepted.')).toBeInTheDocument();
    expect(api.upload).not.toHaveBeenCalled();
  });

  it('rejects files over the size limit', async () => {
    const large = new File(['x'], 'big.pdf', { type: 'application/pdf' });
    Object.defineProperty(large, 'size', { value: 21 * 1024 * 1024 });
    renderApp(<UploadDialog open onOpenChange={() => undefined} scope={{ courseId: 'c1' }} />);
    await userEvent.upload(screen.getByTestId('file-input'), large);
    expect(await screen.findByText('File is larger than 20 MB.')).toBeInTheDocument();
  });

  it('shows the server rejection reason', async () => {
    api.upload.mockRejectedValue(new Error('This PDF appears to be corrupted and could not be read.'));
    renderApp(<UploadDialog open onOpenChange={() => undefined} scope={{ courseId: 'c1' }} />);
    await userEvent.upload(screen.getByTestId('file-input'), pdf);
    expect(await screen.findByText('This PDF appears to be corrupted and could not be read.')).toBeInTheDocument();
  });
});
