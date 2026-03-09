'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, Link2, MousePointerClick, TrendingUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/header';
import { LinkTable } from '@/components/link-table';
import { CreateLinkModal } from '@/components/create-link-modal';
import { LoadingState } from '@/components/loading-state';
import { ErrorState } from '@/components/error-state';
import { EmptyState } from '@/components/empty-state';
import { linksApi } from '@/lib/api-client';
import type { Link } from '@/lib/types';

export function DashboardContent() {
  const [links, setLinks] = useState<Link[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | undefined>();
  const [updatingId, setUpdatingId] = useState<string | undefined>();

  const fetchLinks = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    const response = await linksApi.getAll();
    if (response.error) {
      setError(response.error);
    } else if (response.data) {
      setLinks(response.data);
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    fetchLinks();
  }, [fetchLinks]);

  const handleCreateSuccess = (newLink: Link) => {
    setLinks((prev) => [newLink, ...prev]);
  };

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    const response = await linksApi.delete(id);
    if (response.data?.success) {
      setLinks((prev) => prev.filter((link) => link.id !== id));
    }
    setDeletingId(undefined);
  };

  const handleToggleStatus = async (id: string, status: 'active' | 'inactive') => {
    setUpdatingId(id);
    const response = await linksApi.updateStatus(id, status);
    if (response.data) {
      setLinks((prev) =>
        prev.map((link) => (link.id === id ? response.data! : link))
      );
    }
    setUpdatingId(undefined);
  };

  // Stats
  const totalLinks = links.length;
  const activeLinks = links.filter((l) => l.status === 'active').length;
  const totalClicks = links.reduce((sum, l) => sum + l.totalClicks, 0);

  return (
    <div className="min-h-screen bg-background">
      <Header variant="app" />

      <main className="mx-auto max-w-6xl px-4 py-8">
        {/* Page Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-foreground">대시보드</h1>
            <p className="mt-1 text-muted-foreground">
              단축 링크를 관리하고 성과를 추적하세요
            </p>
          </div>
          <Button onClick={() => setIsModalOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            링크 생성
          </Button>
        </div>

        {/* Stats */}
        <div className="mt-8 grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<Link2 className="h-5 w-5" />}
            label="전체 링크"
            value={totalLinks.toString()}
          />
          <StatCard
            icon={<TrendingUp className="h-5 w-5" />}
            label="활성 링크"
            value={activeLinks.toString()}
          />
          <StatCard
            icon={<MousePointerClick className="h-5 w-5" />}
            label="총 클릭 수"
            value={totalClicks >= 1000 ? `${(totalClicks / 1000).toFixed(1)}k` : totalClicks.toString()}
          />
        </div>

        {/* Links Table */}
        <div className="mt-8">
          <h2 className="mb-4 text-lg font-medium text-foreground">내 링크</h2>
          
          {isLoading ? (
            <LoadingState message="링크를 불러오는 중..." />
          ) : error ? (
            <ErrorState
              title="링크를 불러오지 못했습니다"
              message={error}
              onRetry={fetchLinks}
            />
          ) : links.length === 0 ? (
            <EmptyState
              title="아직 링크가 없습니다"
              description="첫 단축 링크를 만들어 분석을 시작해보세요."
              action={{
                label: '첫 링크 만들기',
                onClick: () => setIsModalOpen(true),
              }}
            />
          ) : (
            <LinkTable
              links={links}
              onDelete={handleDelete}
              onToggleStatus={handleToggleStatus}
              isDeleting={deletingId}
              isUpdating={updatingId}
            />
          )}
        </div>
      </main>

      <CreateLinkModal
        open={isModalOpen}
        onOpenChange={setIsModalOpen}
        onSuccess={handleCreateSuccess}
      />
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
          {icon}
        </div>
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="text-2xl font-semibold text-foreground">{value}</p>
        </div>
      </div>
    </div>
  );
}
