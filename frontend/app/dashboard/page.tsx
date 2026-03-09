'use client';

import { AuthProvider } from '@/lib/auth-context';
import { ProtectedRoute } from '@/components/protected-route';
import { DashboardContent } from '@/components/dashboard-content';

export default function DashboardPage() {
  return (
    <AuthProvider>
      <ProtectedRoute>
        <DashboardContent />
      </ProtectedRoute>
    </AuthProvider>
  );
}
