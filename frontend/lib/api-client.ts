import type {
  User,
  Link,
  LinkStats,
  ApiResponse,
  AuthResponse,
  LoginFormData,
  SignupFormData,
  CreateLinkFormData,
} from './types';
import { dummyLinks, generateDummyStats, delay } from './dummy-data';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const USE_DUMMY_DATA = process.env.NEXT_PUBLIC_USE_DUMMY_DATA === 'true';

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

interface BackendUserResponse {
  id: number;
  email: string;
  name: string;
}

const USER_KEY = 'shortlink_user';
let accessTokenMemory: string | null = null;

// Token management
export const tokenStorage = {
  get: (): string | null => {
    return accessTokenMemory;
  },
  set: (token: string): void => {
    accessTokenMemory = token;
  },
  remove: (): void => {
    accessTokenMemory = null;
  },
};

const userStorage = {
  get: (): User | null => {
    if (typeof window === 'undefined') return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;

    try {
      return JSON.parse(raw) as User;
    } catch {
      localStorage.removeItem(USER_KEY);
      return null;
    }
  },
  set: (user: User): void => {
    if (typeof window === 'undefined') return;
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  remove: (): void => {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(USER_KEY);
  },
};

// Helper function for API requests
async function apiRequest<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = tokenStorage.get();

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
      credentials: 'include',
    });

    const data = (await response.json()) as BackendEnvelope<T> | BackendErrorEnvelope;

    if (!response.ok) {
      if ('message' in data) {
        return { error: data.message || '요청 처리 중 오류가 발생했습니다.' };
      }
      return { error: '요청 처리 중 오류가 발생했습니다.' };
    }

    if ('data' in data) {
      return { data: data.data };
    }

    return { error: '응답 형식이 올바르지 않습니다.' };
  } catch {
    return { error: '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' };
  }
}

// In-memory store for dummy data mutations
let localLinks = USE_DUMMY_DATA ? [...dummyLinks] : [];
let nextLinkId = USE_DUMMY_DATA ? dummyLinks.length + 1 : 1;

const toUser = (backendUser: BackendUserResponse): User => ({
  id: String(backendUser.id),
  email: backendUser.email,
  name: backendUser.name,
  createdAt: new Date().toISOString(),
});

// Auth API
export const authApi = {
  refresh: async (): Promise<ApiResponse<{ token: string }>> => {
    if (USE_DUMMY_DATA) {
      await delay(300);
      const token = 'dummy-jwt-token-' + Date.now();
      tokenStorage.set(token);
      return { data: { token } };
    }

    const refreshResponse = await apiRequest<BackendTokenResponse>('/auth/refresh', {
      method: 'POST',
    });

    if (refreshResponse.error || !refreshResponse.data) {
      return { error: refreshResponse.error || '토큰 갱신에 실패했습니다.' };
    }

    tokenStorage.set(refreshResponse.data.accessToken);
    return { data: { token: refreshResponse.data.accessToken } };
  },

  signup: async (formData: SignupFormData): Promise<ApiResponse<AuthResponse>> => {
    if (USE_DUMMY_DATA) {
      await delay(800);
      const newUser: User = {
        id: 'user-new',
        email: formData.email,
        name: formData.name,
        createdAt: new Date().toISOString(),
      };
      return {
        data: {
          user: newUser,
          token: 'dummy-jwt-token-' + Date.now(),
        },
      };
    }

    const signupResponse = await apiRequest<BackendUserResponse>('/auth/signup', {
      method: 'POST',
      body: JSON.stringify(formData),
    });

    if (signupResponse.error || !signupResponse.data) {
      return { error: signupResponse.error || '회원가입에 실패했습니다.' };
    }

    const loginResponse = await apiRequest<BackendTokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: formData.email, password: formData.password }),
    });

    if (loginResponse.error || !loginResponse.data) {
      return { error: loginResponse.error || '회원가입 후 로그인에 실패했습니다.' };
    }

    const user = toUser(signupResponse.data);
    userStorage.set(user);

    return {
      data: {
        user,
        token: loginResponse.data.accessToken,
      },
    };
  },

  login: async (formData: LoginFormData): Promise<ApiResponse<AuthResponse>> => {
    if (USE_DUMMY_DATA) {
      await delay(800);
      if (formData.email && formData.password) {
        return {
          data: {
            user: {
              id: 'user-1',
              email: formData.email,
              name: '데모 사용자',
              createdAt: new Date().toISOString(),
            },
            token: 'dummy-jwt-token-' + Date.now(),
          },
        };
      }
      return { error: '이메일 또는 비밀번호가 올바르지 않습니다.' };
    }

    const loginResponse = await apiRequest<BackendTokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(formData),
    });

    if (loginResponse.error || !loginResponse.data) {
      return { error: loginResponse.error || '로그인에 실패했습니다.' };
    }

    const cachedUser = userStorage.get();
    const user: User = cachedUser ?? {
      id: formData.email,
      email: formData.email,
      name: formData.email.split('@')[0],
      createdAt: new Date().toISOString(),
    };

    userStorage.set(user);

    return {
      data: {
        user,
        token: loginResponse.data.accessToken,
      },
    };
  },

  me: async (): Promise<ApiResponse<User>> => {
    if (USE_DUMMY_DATA) {
      await delay(300);
      const token = tokenStorage.get();
      if (token) {
        const cachedUser = userStorage.get();
        return { data: cachedUser ?? { id: 'user-1', email: 'demo@short.link', name: '데모 사용자', createdAt: new Date().toISOString() } };
      }
      return { error: '인증이 필요합니다.' };
    }

    const token = tokenStorage.get();
    if (!token) {
      return { error: '인증이 필요합니다.' };
    }

    if (!tokenStorage.get()) {
      const refreshResult = await authApi.refresh();
      if (refreshResult.error) {
        return { error: '인증이 필요합니다.' };
      }
    }

    const meResponse = await apiRequest<BackendUserResponse>('/auth/me', {
      method: 'GET',
    });

    if (meResponse.error || !meResponse.data) {
      return { error: meResponse.error || '사용자 정보를 불러올 수 없습니다. 다시 로그인해 주세요.' };
    }

    const user = toUser(meResponse.data);
    userStorage.set(user);
    return { data: user };
  },

  logout: async (): Promise<void> => {
    if (!USE_DUMMY_DATA) {
      await apiRequest<string>('/auth/logout', {
        method: 'POST',
      });
    }

    tokenStorage.remove();
    userStorage.remove();
  },
};

// Links API
export const linksApi = {
  getAll: async (): Promise<ApiResponse<Link[]>> => {
    await delay(USE_DUMMY_DATA ? 500 : 300);
    return { data: localLinks };
  },

  getById: async (id: string): Promise<ApiResponse<Link>> => {
    await delay(USE_DUMMY_DATA ? 300 : 200);
    const link = localLinks.find((l) => l.id === id);
    if (link) {
      return { data: link };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  getStats: async (id: string): Promise<ApiResponse<LinkStats>> => {
    await delay(USE_DUMMY_DATA ? 400 : 250);
    const link = localLinks.find((l) => l.id === id);
    if (link) {
      return { data: generateDummyStats(id) };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  create: async (formData: CreateLinkFormData): Promise<ApiResponse<Link>> => {
    await delay(USE_DUMMY_DATA ? 600 : 250);
    const shortCode = formData.customCode || Math.random().toString(36).substring(2, 8);
    const newLink: Link = {
      id: `link-${nextLinkId++}`,
      originalUrl: formData.originalUrl,
      shortCode,
      shortUrl: `https://shrt.link/${shortCode}`,
      createdAt: new Date().toISOString(),
      expiresAt: formData.expiresAt,
      status: 'active',
      totalClicks: 0,
      userId: 'user-1',
    };
    localLinks = [newLink, ...localLinks];
    return { data: newLink };
  },

  delete: async (id: string): Promise<ApiResponse<{ success: boolean }>> => {
    await delay(USE_DUMMY_DATA ? 400 : 150);
    localLinks = localLinks.filter((l) => l.id !== id);
    return { data: { success: true } };
  },

  updateStatus: async (
    id: string,
    status: 'active' | 'inactive'
  ): Promise<ApiResponse<Link>> => {
    await delay(USE_DUMMY_DATA ? 400 : 150);
    const linkIndex = localLinks.findIndex((l) => l.id === id);
    if (linkIndex !== -1) {
      localLinks[linkIndex] = { ...localLinks[linkIndex], status };
      return { data: localLinks[linkIndex] };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  createAnonymous: async (originalUrl: string): Promise<ApiResponse<Link>> => {
    await delay(USE_DUMMY_DATA ? 600 : 250);
    const shortCode = Math.random().toString(36).substring(2, 8);
    const newLink: Link = {
      id: `anon-link-${Date.now()}`,
      originalUrl,
      shortCode,
      shortUrl: `https://shrt.link/${shortCode}`,
      createdAt: new Date().toISOString(),
      status: 'active',
      totalClicks: 0,
      userId: 'anonymous',
    };
    return { data: newLink };
  },
};