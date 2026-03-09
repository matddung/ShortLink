// User types
export interface User {
  id: string;
  email: string;
  name: string;
  createdAt: string;
}

// Link types
export type LinkStatus = 'active' | 'inactive' | 'expired';

export interface Link {
  id: string;
  originalUrl: string;
  shortCode: string;
  shortUrl: string;
  createdAt: string;
  expiresAt?: string;
  status: LinkStatus;
  totalClicks: number;
  userId: string;
}

// Analytics types
export interface ReferrerStat {
  source: string;
  count: number;
  percentage: number;
}

export interface DailyClick {
  date: string;
  clicks: number;
}

export interface LinkStats {
  totalClicks: number;
  uniqueClicks: number;
  lastClickedAt: string | null;
  referrers: ReferrerStat[];
  dailyClicks: DailyClick[];
  topCountries: { country: string; count: number }[];
}

// API Response types
export interface ApiResponse<T> {
  data?: T;
  error?: string;
  message?: string;
}

export interface AuthResponse {
  user: User;
  token: string;
}

// Form types
export interface LoginFormData {
  email: string;
  password: string;
}

export interface SignupFormData {
  name: string;
  email: string;
  password: string;
}

export interface CreateLinkFormData {
  originalUrl: string;
  customCode?: string;
  expiresAt?: string;
}
