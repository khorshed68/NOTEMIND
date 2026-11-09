import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, type AxiosResponse } from 'axios';
import { Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ProtectedRoute } from '@/routes/guards';
import { renderApp } from './utils';

const auth = vi.hoisted(() => ({
  user: null as null | { role: string },
  loading: false,
  login: vi.fn(),
  register: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({ useAuth: () => auth }));

function apiError(status: number, data: object) {
  return new AxiosError('fail', undefined, undefined, undefined, { status, data } as AxiosResponse);
}

beforeEach(() => {
  auth.user = null;
  auth.loading = false;
  auth.login.mockReset();
  auth.register.mockReset();
});

const loginRoutes = (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/dashboard" element={<p>Dashboard screen</p>} />
  </Routes>
);

describe('LoginPage', () => {
  it('validates fields before calling the server', async () => {
    renderApp(loginRoutes, '/login');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('rejects a malformed email', async () => {
    renderApp(loginRoutes, '/login');
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'whatever1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter a valid email')).toBeInTheDocument();
  });

  it('signs in and goes to the dashboard', async () => {
    auth.login.mockResolvedValue({ id: '1' });
    renderApp(loginRoutes, '/login');
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co');
    await userEvent.type(screen.getByLabelText('Password'), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Dashboard screen')).toBeInTheDocument();
    expect(auth.login).toHaveBeenCalledWith('a@b.co', 'secret123');
  });

  it('shows the server error when credentials are wrong', async () => {
    auth.login.mockRejectedValue(apiError(401, { message: 'Incorrect email or password.' }));
    renderApp(loginRoutes, '/login');
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong1234');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Incorrect email or password.')).toBeInTheDocument();
  });

  it('toggles password visibility', async () => {
    renderApp(loginRoutes, '/login');
    const input = screen.getByLabelText('Password');
    expect(input).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(input).toHaveAttribute('type', 'text');
  });
});

describe('RegisterPage', () => {
  const routes = (
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/dashboard" element={<p>Dashboard screen</p>} />
    </Routes>
  );

  it('enforces the password rules', async () => {
    renderApp(routes, '/register');
    await userEvent.type(screen.getByLabelText('Full name'), 'Sam');
    await userEvent.type(screen.getByLabelText('Email'), 'sam@uni.edu');
    await userEvent.type(screen.getByLabelText('Password'), 'onlyletters');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Password must contain a number')).toBeInTheDocument();
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('creates the account', async () => {
    auth.register.mockResolvedValue({ id: '1' });
    renderApp(routes, '/register');
    await userEvent.type(screen.getByLabelText('Full name'), 'Sam Rahman');
    await userEvent.type(screen.getByLabelText('Email'), 'sam@uni.edu');
    await userEvent.type(screen.getByLabelText('Password'), 'goodpass1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(auth.register).toHaveBeenCalledWith('Sam Rahman', 'sam@uni.edu', 'goodpass1'));
    expect(await screen.findByText('Dashboard screen')).toBeInTheDocument();
  });

  it('maps server field errors onto the form', async () => {
    auth.register.mockRejectedValue(apiError(400, { message: 'Bad', fieldErrors: { email: 'Already used' } }));
    renderApp(routes, '/register');
    await userEvent.type(screen.getByLabelText('Full name'), 'Sam');
    await userEvent.type(screen.getByLabelText('Email'), 'sam@uni.edu');
    await userEvent.type(screen.getByLabelText('Password'), 'goodpass1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Already used')).toBeInTheDocument();
  });
});

describe('ProtectedRoute', () => {
  const routes = (
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/secret" element={<p>Secret page</p>} />
      </Route>
      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route path="/admin" element={<p>Admin page</p>} />
      </Route>
      <Route path="/login" element={<p>Login screen</p>} />
      <Route path="/dashboard" element={<p>Dashboard screen</p>} />
    </Routes>
  );

  it('redirects anonymous visitors to login', () => {
    renderApp(routes, '/secret');
    expect(screen.getByText('Login screen')).toBeInTheDocument();
    expect(screen.queryByText('Secret page')).not.toBeInTheDocument();
  });

  it('waits for the session check instead of redirecting', () => {
    auth.loading = true;
    renderApp(routes, '/secret');
    expect(screen.getByLabelText('Loading')).toBeInTheDocument();
    expect(screen.queryByText('Login screen')).not.toBeInTheDocument();
  });

  it('lets signed-in users through', () => {
    auth.user = { role: 'STUDENT' };
    renderApp(routes, '/secret');
    expect(screen.getByText('Secret page')).toBeInTheDocument();
  });

  it('keeps students out of admin pages', () => {
    auth.user = { role: 'STUDENT' };
    renderApp(routes, '/admin');
    expect(screen.getByText('Dashboard screen')).toBeInTheDocument();
    expect(screen.queryByText('Admin page')).not.toBeInTheDocument();
  });
});
