'use client';

import { useState } from 'react';
import { Plus, Link2, MousePointerClick, TrendingUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/header';
import { LinkTable } from '@/components/link-table';
import { CreateLinkModal } from '@/components/create-link-modal';
import { LoadingState } from '@/components/loading-state';
import { ErrorState } from '@/components/error-state';
import { EmptyState } from '@/components/empty-state';
import { useLinks } from '@/hooks/use-links';

export function DashboardContent() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const {
    links,
    isLoading,
    error,
    updatingId,
    fetchLinks,
    addLink,
    updateStatus,
  } = useLinks();

  const totalLinks = links.length;
  const activeLinks = links.filter((link) => link.status === 'active').length;
  const totalClicks = links.reduce((sum, link) => sum + link.totalClicks, 0);

  return (
    <div className="min-h-screen bg-background">
      <Header variant="app" />

      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-foreground">Dashboard</h1>
            <p className="mt-1 text-muted-foreground">
              Manage short links and track their performance.
            </p>
          </div>
          <Button onClick={() => setIsModalOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Create Link
          </Button>
        </div>

        <div className="mt-8 grid gap-4 sm:grid-cols-3">
          <StatCard
            icon={<Link2 className="h-5 w-5" />}
            label="Total Links"
            value={totalLinks.toString()}
          />
          <StatCard
            icon={<TrendingUp className="h-5 w-5" />}
            label="Active Links"
            value={activeLinks.toString()}
          />
          <StatCard
            icon={<MousePointerClick className="h-5 w-5" />}
            label="Total Clicks"
            value={totalClicks >= 1000 ? `${(totalClicks / 1000).toFixed(1)}k` : totalClicks.toString()}
          />
        </div>

        <div className="mt-8">
          <h2 className="mb-4 text-lg font-medium text-foreground">Links</h2>

          {isLoading ? (
            <LoadingState message="Loading links..." />
          ) : error ? (
            <ErrorState
              title="Could not load links"
              message={error}
              onRetry={fetchLinks}
            />
          ) : links.length === 0 ? (
            <EmptyState
              title="No links yet"
              description="Create your first short link to start tracking analytics."
              action={{
                label: 'Create First Link',
                onClick: () => setIsModalOpen(true),
              }}
            />
          ) : (
            <LinkTable
              links={links}
              onToggleStatus={updateStatus}
              isUpdating={updatingId}
            />
          )}
        </div>
      </main>

      <CreateLinkModal
        open={isModalOpen}
        onOpenChange={setIsModalOpen}
        onSuccess={addLink}
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
