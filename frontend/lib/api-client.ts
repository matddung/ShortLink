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
import { dummyUser, dummyLinks, generateDummyStats, delay } from './dummy-data';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '/api';
const USE_DUMMY_DATA = true; // Toggle this to switch between dummy and real API

// Token management
export const tokenStorage = {
  get: (): string | null => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem('shortlink_token');
  },
  set: (token: string): void => {
    if (typeof window === 'undefined') return;
    localStorage.setItem('shortlink_token', token);
  },
  remove: (): void => {
    if (typeof window === 'undefined') return;
    localStorage.removeItem('shortlink_token');
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
    });

    const data = await response.json();

    if (!response.ok) {
      return { error: data.message || 'An error occurred' };
    }

    return { data };
  } catch {
    return { error: 'Network error. Please try again.' };
  }
}

// In-memory store for dummy data mutations
let localLinks = [...dummyLinks];
let nextLinkId = 7;

// Auth API
export const authApi = {
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
    return apiRequest<AuthResponse>('/auth/signup', {
      method: 'POST',
      body: JSON.stringify(formData),
    });
  },

  login: async (formData: LoginFormData): Promise<ApiResponse<AuthResponse>> => {
    if (USE_DUMMY_DATA) {
      await delay(800);
      if (formData.email && formData.password) {
        return {
          data: {
            user: dummyUser,
            token: 'dummy-jwt-token-' + Date.now(),
          },
        };
      }
      return { error: 'Invalid email or password' };
    }
    return apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(formData),
    });
  },

  me: async (): Promise<ApiResponse<User>> => {
    if (USE_DUMMY_DATA) {
      await delay(300);
      const token = tokenStorage.get();
      if (token) {
        return { data: dummyUser };
      }
      return { error: 'Not authenticated' };
    }
    return apiRequest<User>('/auth/me');
  },

  logout: async (): Promise<void> => {
    tokenStorage.remove();
  },
};

// Links API
export const linksApi = {
  getAll: async (): Promise<ApiResponse<Link[]>> => {
    if (USE_DUMMY_DATA) {
      await delay(500);
      return { data: localLinks };
    }
    return apiRequest<Link[]>('/links');
  },

  getById: async (id: string): Promise<ApiResponse<Link>> => {
    if (USE_DUMMY_DATA) {
      await delay(300);
      const link = localLinks.find((l) => l.id === id);
      if (link) {
        return { data: link };
      }
      return { error: 'Link not found' };
    }
    return apiRequest<Link>(`/links/${id}`);
  },

  getStats: async (id: string): Promise<ApiResponse<LinkStats>> => {
    if (USE_DUMMY_DATA) {
      await delay(400);
      const link = localLinks.find((l) => l.id === id);
      if (link) {
        return { data: generateDummyStats(id) };
      }
      return { error: 'Link not found' };
    }
    return apiRequest<LinkStats>(`/links/${id}/stats`);
  },

  create: async (formData: CreateLinkFormData): Promise<ApiResponse<Link>> => {
    if (USE_DUMMY_DATA) {
      await delay(600);
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
    }
    return apiRequest<Link>('/links', {
      method: 'POST',
      body: JSON.stringify(formData),
    });
  },

  delete: async (id: string): Promise<ApiResponse<{ success: boolean }>> => {
    if (USE_DUMMY_DATA) {
      await delay(400);
      localLinks = localLinks.filter((l) => l.id !== id);
      return { data: { success: true } };
    }
    return apiRequest<{ success: boolean }>(`/links/${id}`, {
      method: 'DELETE',
    });
  },

  updateStatus: async (
    id: string,
    status: 'active' | 'inactive'
  ): Promise<ApiResponse<Link>> => {
    if (USE_DUMMY_DATA) {
      await delay(400);
      const linkIndex = localLinks.findIndex((l) => l.id === id);
      if (linkIndex !== -1) {
        localLinks[linkIndex] = { ...localLinks[linkIndex], status };
        return { data: localLinks[linkIndex] };
      }
      return { error: 'Link not found' };
    }
    return apiRequest<Link>(`/links/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  },

  // For anonymous users on landing page
  createAnonymous: async (originalUrl: string): Promise<ApiResponse<Link>> => {
    if (USE_DUMMY_DATA) {
      await delay(600);
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
    }
    return apiRequest<Link>('/links/anonymous', {
      method: 'POST',
      body: JSON.stringify({ originalUrl }),
    });
  },
};
