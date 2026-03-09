'use client';

import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import { useRouter } from 'next/navigation';
import type { User, LoginFormData, SignupFormData } from './types';
import { authApi, tokenStorage } from './api-client';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (data: LoginFormData) => Promise<{ error?: string }>;
  signup: (data: SignupFormData) => Promise<{ error?: string }>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  const checkAuth = useCallback(async () => {
    const refreshResponse = await authApi.refresh();
    if (refreshResponse.error) {
      tokenStorage.remove();
      setUser(null);
      setIsLoading(false);
      return;
    }

    const response = await authApi.me();
    if (response.data) {
      setUser(response.data);
    } else {
      tokenStorage.remove();
      setUser(null);
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  const login = async (data: LoginFormData) => {
    const response = await authApi.login(data);
    if (response.error) {
      return { error: response.error };
    }
    if (response.data) {
      tokenStorage.set(response.data.token);
      setUser(response.data.user);
      router.push('/dashboard');
    }
    return {};
  };

  const signup = async (data: SignupFormData) => {
    const response = await authApi.signup(data);
    if (response.error) {
      return { error: response.error };
    }
    if (response.data) {
      tokenStorage.set(response.data.token);
      setUser(response.data.user);
      router.push('/dashboard');
    }
    return {};
  };

  const logout = () => {
    authApi.logout();
    setUser(null);
    router.push('/');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user,
        login,
        signup,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
