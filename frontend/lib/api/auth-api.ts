import type {
  ApiResponse,
  AuthResponse,
  LoginFormData,
  SignupFormData,
  User,
} from '../types';
import { apiRequest, tokenStorage } from './http-client';

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

const toUser = (backendUser: BackendUserResponse): User => ({
  id: String(backendUser.id),
  email: backendUser.email,
  name: backendUser.name,
  createdAt: new Date().toISOString(),
});

export const authApi = {
  refresh: async (): Promise<ApiResponse<{ token: string }>> => {
    const refreshResponse = await apiRequest<BackendTokenResponse>('/auth/refresh', {
      method: 'POST',
    });

    if (refreshResponse.error || !refreshResponse.data) {
      return { error: refreshResponse.error || 'Could not refresh token.' };
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
      return { error: signupResponse.error || 'Could not sign up.' };
    }

    const loginResponse = await apiRequest<BackendTokenResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: formData.email, password: formData.password }),
    });

    if (loginResponse.error || !loginResponse.data) {
      return { error: loginResponse.error || 'Signed up, but could not log in.' };
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
      return { error: loginResponse.error || 'Could not log in.' };
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
      return { error: 'Authentication is required.' };
    }

    if (!tokenStorage.get()) {
      const refreshResult = await authApi.refresh();
      if (refreshResult.error) {
        return { error: 'Authentication is required.' };
      }
    }

    const meResponse = await apiRequest<BackendUserResponse>('/auth/me', {
      method: 'GET',
    });

    if (meResponse.error || !meResponse.data) {
      return { error: meResponse.error || 'Could not load user information. Log in again.' };
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
