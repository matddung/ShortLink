'use client';

import { AuthProvider } from '@/lib/auth-context';
import { ProtectedRoute } from '@/components/protected-route';
import { LinkDetailContent } from '@/components/link-detail-content';

export default function LinkDetailPage() {
  return (
    <AuthProvider>
      <ProtectedRoute>
        <LinkDetailContent />
      </ProtectedRoute>
    </AuthProvider>
  );
}
