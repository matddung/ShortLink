import type {
  ApiResponse,
  CreateLinkFormData,
  Link,
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
      return { error: response.error || '링크를 불러올 수 없습니다.' };
    }

    const link = response.data.find((item) => item.id === id);
    if (!link) {
      return { error: '링크를 찾을 수 없습니다.' };
    }

    return { data: link };
  },

  getStats: async (id: string): Promise<ApiResponse<LinkStats>> => {
    await delay(250);
    const response = await apiRequest<LinkStats>(`/links/${id}/stats`, {
      method: 'GET',
    });

    if (response.error || !response.data) {
      return { error: response.error || '통계 데이터를 불러올 수 없습니다.' };
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

  delete: async (id: string): Promise<ApiResponse<{ success: boolean }>> => {
    await delay(150);
    return { data: { success: true } };
  },

  updateStatus: async (
    id: string,
    status: 'active' | 'inactive'
  ): Promise<ApiResponse<Link>> => {
    await delay(150);
    const linkResponse = await linksApi.getById(id);
    if (linkResponse.error || !linkResponse.data) {
      return { error: linkResponse.error || '링크를 찾을 수 없습니다.' };
    }

    return { data: { ...linkResponse.data, status } };
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
