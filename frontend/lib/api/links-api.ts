import type {
  ApiResponse,
  CreateLinkFormData,
  Link,
  LinkStatus,
  LinkStats,
} from '../types';
import { apiRequest, delay } from './http-client';

export const linksApi = {
  getAll: async (): Promise<ApiResponse<Link[]>> => {
    await delay(300);
    return apiRequest<Link[]>('/links', {
      method: 'GET',
    });
  },

  getById: async (id: string): Promise<ApiResponse<Link>> => {
    await delay(200);
    const response = await apiRequest<Link[]>('/links', {
      method: 'GET',
    });

    if (response.error || !response.data) {
      return { error: response.error || 'Could not load links.' };
    }

    const link = response.data.find((item) => item.id === id);
    if (!link) {
      return { error: 'Link not found.' };
    }

    return { data: link };
  },

  getStats: async (id: string): Promise<ApiResponse<LinkStats>> => {
    await delay(250);
    const response = await apiRequest<LinkStats>(`/links/${id}/stats`, {
      method: 'GET',
    });

    if (response.error || !response.data) {
      return { error: response.error || 'Could not load analytics.' };
    }

    return { data: response.data };
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

  updateStatus: async (
    id: string,
    status: LinkStatus
  ): Promise<ApiResponse<Link>> => {
    await delay(150);
    return apiRequest<Link>(`/links/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
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
