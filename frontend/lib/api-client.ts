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

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

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
        return { error: data.message || '요청 처리 중 오류가 발생했습니다.' };
      }
      return { error: '요청 처리 중 오류가 발생했습니다.' };
    }

    if (data && 'data' in data) {
      return { data: data.data };
    }

    return { error: '응답 형식이 올바르지 않습니다.' };
  } catch {
    return { error: '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' };
  }
}

// In-memory store for link mutations before backend endpoints are wired.
let localLinks: Link[] = [];
let nextLinkId = 1;

const buildLinkStats = (link: Link): LinkStats => {
  const totalClicks = link.totalClicks;
  const uniqueClicks = Math.floor(totalClicks * 0.78);
  const today = new Date();

  const dailyClicks = Array.from({ length: 14 }, (_, index) => {
    const date = new Date(today);
    date.setDate(today.getDate() - (13 - index));
    return {
      date: date.toISOString().split('T')[0],
      clicks: totalClicks > 0 ? Math.max(1, Math.floor(totalClicks / 14)) : 0,
    };
  });

  return {
    totalClicks,
    uniqueClicks,
    lastClickedAt: totalClicks > 0 ? new Date().toISOString() : null,
    referrers: [],
    dailyClicks,
    topCountries: [],
  };
};

const toUser = (backendUser: BackendUserResponse): User => ({
  id: String(backendUser.id),
  email: backendUser.email,
  name: backendUser.name,
  createdAt: new Date().toISOString(),
});

// Auth API
export const authApi = {
  refresh: async (): Promise<ApiResponse<{ token: string }>> => {
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
    await apiRequest<string>('/auth/logout', {
      method: 'POST',
    });

    tokenStorage.remove();
    userStorage.remove();
  },
};

// Links API
export const linksApi = {
  getAll: async (): Promise<ApiResponse<Link[]>> => {
    await delay(300);
    return apiRequest<Link[]>('/links', {
      method: 'GET',
    });
  },

  getById: async (id: string): Promise<ApiResponse<Link>> => {
    await delay(200);
    const link = localLinks.find((l) => l.id === id);
    if (link) {
      return { data: link };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  getStats: async (id: string): Promise<ApiResponse<LinkStats>> => {
    await delay(250);
    const link = localLinks.find((l) => l.id === id);
    if (link) {
      return { data: buildLinkStats(link) };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  create: async (formData: CreateLinkFormData): Promise<ApiResponse<Link>> => {
    await delay(250);
    return apiRequest<Link>('/links', {
      method: 'POST',
      body: JSON.stringify({
        originalUrl: formData.originalUrl,
        customCode: formData.customCode,
      }),
    });
  },

  delete: async (id: string): Promise<ApiResponse<{ success: boolean }>> => {
    await delay(150);
    localLinks = localLinks.filter((l) => l.id !== id);
    return { data: { success: true } };
  },

  updateStatus: async (
    id: string,
    status: 'active' | 'inactive'
  ): Promise<ApiResponse<Link>> => {
    await delay(150);
    const linkIndex = localLinks.findIndex((l) => l.id === id);
    if (linkIndex !== -1) {
      localLinks[linkIndex] = { ...localLinks[linkIndex], status };
      return { data: localLinks[linkIndex] };
    }
    return { error: '링크를 찾을 수 없습니다.' };
  },

  getAnonymous: async (): Promise<ApiResponse<Link[]>> => {
    await delay(150);
    return apiRequest<Link[]>('/links/anonymous', {
      method: 'GET',
    });
  },

  createAnonymous: async (originalUrl: string): Promise<ApiResponse<Link>> => {
    await delay(250);
    return apiRequest<Link>('/links/anonymous', {
      method: 'POST',
      body: JSON.stringify({ originalUrl }),
    });
  },
};