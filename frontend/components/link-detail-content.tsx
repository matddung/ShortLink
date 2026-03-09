'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { ArrowLeft, Copy, Check, ExternalLink, MousePointerClick, Users, Clock, Globe } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/header';
import { StatusBadge } from '@/components/status-badge';
import { LoadingState } from '@/components/loading-state';
import { ErrorState } from '@/components/error-state';
import { AnalyticsSummaryCard } from '@/components/analytics-summary-card';
import { ReferrerList } from '@/components/referrer-list';
import { DailyClicksChart } from '@/components/daily-clicks-chart';
import { linksApi } from '@/lib/api-client';
import type { Link as LinkType, LinkStats } from '@/lib/types';

export function LinkDetailContent() {
  const params = useParams();
  const router = useRouter();
  const linkId = params.id as string;

  const [link, setLink] = useState<LinkType | null>(null);
  const [stats, setStats] = useState<LinkStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    const [linkResponse, statsResponse] = await Promise.all([
      linksApi.getById(linkId),
      linksApi.getStats(linkId),
    ]);

    if (linkResponse.error) {
      setError(linkResponse.error);
    } else if (linkResponse.data) {
      setLink(linkResponse.data);
    }

    if (statsResponse.data) {
      setStats(statsResponse.data);
    }

    setIsLoading(false);
  }, [linkId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const copyToClipboard = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link.shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      console.error('Failed to copy');
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  };

  const formatLastClicked = (dateString: string | null) => {
    if (!dateString) return 'Never';
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    
    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins} min ago`;
    
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
    
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background">
        <Header variant="app" />
        <main className="mx-auto max-w-6xl px-4 py-8">
          <LoadingState message="Loading link analytics..." />
        </main>
      </div>
    );
  }

  if (error || !link) {
    return (
      <div className="min-h-screen bg-background">
        <Header variant="app" />
        <main className="mx-auto max-w-6xl px-4 py-8">
          <ErrorState
            title="링크를 찾을 수 없습니다"
            message={error || 'The link you are looking for does not exist.'}
            onRetry={() => router.push('/dashboard')}
          />
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header variant="app" />

      <main className="mx-auto max-w-6xl px-4 py-8">
        {/* Back Link */}
        <Link
          href="/dashboard"
          className="mb-6 inline-flex items-center text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          대시보드로 돌아가기
        </Link>

        {/* Link Header */}
        <div className="rounded-lg border border-border bg-card p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-3">
                <h1 className="truncate text-xl font-semibold text-foreground">
                  {link.shortUrl.replace('https://', '')}
                </h1>
                <StatusBadge status={link.status} />
              </div>
              <p className="mt-2 truncate text-sm text-muted-foreground">
                {link.originalUrl}
              </p>
              <p className="mt-2 text-xs text-muted-foreground">
                생성일 {formatDate(link.createdAt)}
              </p>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={copyToClipboard}>
                {copied ? (
                  <>
                    <Check className="mr-2 h-4 w-4 text-primary" />
                    복사됨
                  </>
                ) : (
                  <>
                    <Copy className="mr-2 h-4 w-4" />
                    복사
                  </>
                )}
              </Button>
              <Button variant="outline" size="sm" asChild>
                <a href={link.shortUrl} target="_blank" rel="noopener noreferrer">
                  <ExternalLink className="mr-2 h-4 w-4" />
                  열기
                </a>
              </Button>
            </div>
          </div>
        </div>

        {/* Stats Grid */}
        {stats && (
          <>
            <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <AnalyticsSummaryCard
                icon={<MousePointerClick className="h-5 w-5" />}
                label="총 클릭 수"
                value={stats.totalClicks.toLocaleString()}
              />
              <AnalyticsSummaryCard
                icon={<Users className="h-5 w-5" />}
                label="고유 클릭 수"
                value={stats.uniqueClicks.toLocaleString()}
              />
              <AnalyticsSummaryCard
                icon={<Clock className="h-5 w-5" />}
                label="마지막 클릭"
                value={formatLastClicked(stats.lastClickedAt)}
              />
              <AnalyticsSummaryCard
                icon={<Globe className="h-5 w-5" />}
                label="상위 국가"
                value={stats.topCountries[0]?.country || '없음'}
                subValue={stats.topCountries[0] ? `${stats.topCountries[0].count}회 클릭` : undefined}
              />
            </div>

            {/* Charts Row */}
            <div className="mt-6 grid gap-6 lg:grid-cols-3">
              <div className="lg:col-span-2">
                <DailyClicksChart data={stats.dailyClicks} />
              </div>
              <div>
                <ReferrerList referrers={stats.referrers} />
              </div>
            </div>

            {/* Top Countries */}
            <div className="mt-6 rounded-lg border border-border bg-card p-5">
              <h3 className="font-medium text-foreground">상위 국가</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Geographic distribution of your clicks
              </p>
              
              <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                {stats.topCountries.map((country, index) => (
                  <div
                    key={country.country}
                    className="flex items-center justify-between rounded-md bg-muted/50 px-3 py-2"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">#{index + 1}</span>
                      <span className="text-sm text-foreground">{country.country}</span>
                    </div>
                    <span className="text-sm font-medium text-foreground">
                      {country.count}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
