import type { ApiResponse } from '../types';

const ensureApiSuffix = (baseUrl: string): string => {
  const normalized = baseUrl.replace(/\/+$/, '');
  return normalized.endsWith('/api') ? normalized : `${normalized}/api`;
};

const defaultApiBaseUrl = 'http://localhost:8080/api';
const API_BASE_URL = ensureApiSuffix(process.env.NEXT_PUBLIC_API_URL || defaultApiBaseUrl);

interface BackendEnvelope<T> {
  success: boolean;
  data: T;
}

interface BackendErrorEnvelope {
  code?: string;
  message?: string;
}

interface BackendTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

let accessTokenMemory: string | null = null;

export const tokenStorage = {
  get: (): string | null => accessTokenMemory,
  set: (token: string): void => {
    accessTokenMemory = token;
  },
  remove: (): void => {
    accessTokenMemory = null;
  },
};

export const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const buildHeaders = (): HeadersInit => {
    const token = tokenStorage.get();
    return {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    };
  };

  const send = async () =>
    fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers: buildHeaders(),
      credentials: 'include',
    });

  const parseJson = async (response: Response) => {
    try {
      return (await response.json()) as BackendEnvelope<T> | BackendErrorEnvelope;
    } catch {
      return null;
    }
  };

  try {
    let response = await send();

    if (
      response.status === 401 &&
      endpoint !== '/auth/refresh' &&
      endpoint !== '/auth/login' &&
      endpoint !== '/auth/signup'
    ) {
      const refreshResponse = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
      });

      const refreshData = (await parseJson(refreshResponse)) as BackendEnvelope<BackendTokenResponse> | BackendErrorEnvelope | null;
      if (refreshResponse.ok && refreshData && 'data' in refreshData) {
        tokenStorage.set(refreshData.data.accessToken);
        response = await send();
      }
    }

    const data = await parseJson(response);

    if (!response.ok) {
      if (data && 'message' in data) {
        return { error: data.message || 'Request failed.' };
      }
      return { error: 'Request failed.' };
    }

    if (data && 'data' in data) {
      return { data: data.data };
    }

    return { error: 'Unexpected response format.' };
  } catch {
    return { error: 'Network error. Try again later.' };
  }
}
