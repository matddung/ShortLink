'use client';

import { AuthProvider } from '@/lib/auth-context';
import { AuthForm } from '@/components/auth-form';

export default function SignupPage() {
  return (
    <AuthProvider>
      <AuthForm mode="signup" />
    </AuthProvider>
  );
}
