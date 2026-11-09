import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, AuthResponse } from '@/types';

/** The access token lives only in memory, so injected scripts cannot lift it from storage. */
let accessToken: string | null = null;
let onSessionLost: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function setSessionLostHandler(handler: (() => void) | null) {
  onSessionLost = handler;
}

export const api = axios.create({ baseURL: '/api', withCredentials: true });

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

let refreshing: Promise<AuthResponse> | null = null;

/** Concurrent callers share one request, because the refresh token rotates on every use. */
export function refreshSession(): Promise<AuthResponse> {
  refreshing ??= axios
    .post<AuthResponse>('/api/auth/refresh', null, { withCredentials: true })
    .then((response) => {
      setAccessToken(response.data.accessToken);
      return response.data;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as RetriableConfig | undefined;
  const isAuthCall = config?.url?.startsWith('/auth/login') || config?.url?.startsWith('/auth/register');
  if (error.response?.status !== 401 || !config || config._retried || isAuthCall) {
    throw error;
  }
  config._retried = true;
  try {
    await refreshSession();
  } catch {
    setAccessToken(null);
    onSessionLost?.();
    throw error;
  }
  return api(config);
});

export function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined;
    if (data && typeof data === 'object' && data.message) {
      return { status: error.response?.status ?? 0, message: data.message, fieldErrors: data.fieldErrors };
    }
    if (!error.response) return { status: 0, message: 'Cannot reach the server. Check your connection.' };
    return { status: error.response.status, message: 'Something went wrong. Please try again.' };
  }
  return { status: 0, message: error instanceof Error ? error.message : 'Something went wrong.' };
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}
