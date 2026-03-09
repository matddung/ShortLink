'use client';

import { Header } from '@/components/header';
import { UrlShortenerForm } from '@/components/url-shortener-form';
import { AuthProvider } from '@/lib/auth-context';
import { Link2, BarChart3, Shield, Zap, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';

function LandingContent() {
  return (
    <div className="min-h-screen bg-background">
      <Header variant="landing" />
      
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 -z-10">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-background to-background" />
        </div>
        
        <div className="mx-auto max-w-4xl px-4 py-24 text-center sm:py-32">
          <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-border bg-secondary px-3 py-1 text-sm text-muted-foreground">
            <Zap className="h-3 w-3 text-primary" />
            빠르고, 안정적이며, 무료입니다
          </div>
          
          <h1 className="text-balance text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
            Transform long URLs into
            <span className="text-primary"> powerful short links</span>
          </h1>
          
          <p className="mx-auto mt-6 max-w-2xl text-pretty text-lg text-muted-foreground">
            짧고 기억하기 쉬운 링크를 단 몇 초 만에 만드세요. 클릭 수를 추적하고, 실적을 분석하고, 모든 링크를 하나의 대시보드에서 관리하세요
          </p>
          
          <div className="mx-auto mt-10 max-w-xl">
            <UrlShortenerForm />
          </div>
          
          <p className="mt-4 text-sm text-muted-foreground">
            <Link href="/signup" className="text-primary hover:underline">
              무료 회원가입
            </Link>{' '}
            고급 기능이 필요하면 가입하세요
          </p>
        </div>
      </section>
      
      {/* Features Section */}
      <section className="border-t border-border bg-card">
        <div className="mx-auto max-w-6xl px-4 py-20">
          <div className="text-center">
            <h2 className="text-2xl font-semibold text-foreground sm:text-3xl">
              링크 관리를 위한 모든 기능
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-muted-foreground">
              ShortLink는 링크 생성, 관리, 분석에 필요한 기능을 제공합니다
            </p>
          </div>
          
          <div className="mt-16 grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
            <FeatureCard
              icon={<Link2 className="h-5 w-5" />}
              title="즉시 단축"
              description="어떤 URL이든 즉시 짧고 기억하기 쉬운 링크로 바꿔드립니다"
            />
            <FeatureCard
              icon={<BarChart3 className="h-5 w-5" />}
              title="상세 분석"
              description="클릭, 유입 경로, 지역 데이터를 추적하고 사용자 행동을 깊이 있게 이해하세요"
            />
            <FeatureCard
              icon={<Shield className="h-5 w-5" />}
              title="링크 관리"
              description="언제든 활성/비활성/삭제가 가능하며 단축 링크를 완전히 제어할 수 있습니다"
            />
          </div>
        </div>
      </section>
      
      {/* CTA Section */}
      <section className="border-t border-border">
        <div className="mx-auto max-w-4xl px-4 py-20 text-center">
          <h2 className="text-2xl font-semibold text-foreground sm:text-3xl">
            시작할 준비가 되셨나요?
          </h2>
          <p className="mt-4 text-muted-foreground">
            무료 계정을 만들고 몇 초 만에 URL 단축을 시작하세요
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-4 sm:flex-row">
            <Button size="lg" asChild>
              <Link href="/signup">
                무료로 시작하기
                <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
            </Button>
            <Button variant="outline" size="lg" asChild>
              <Link href="/login">
                로그인
              </Link>
            </Button>
          </div>
        </div>
      </section>
      
      {/* Footer */}
      <footer className="border-t border-border">
        <div className="mx-auto max-w-6xl px-4 py-8">
          <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
            <div className="flex items-center gap-2">
              <div className="flex h-6 w-6 items-center justify-center rounded bg-primary">
                <Link2 className="h-3 w-3 text-primary-foreground" />
              </div>
              <span className="text-sm font-medium text-foreground">ShortLink</span>
            </div>
            <p className="text-sm text-muted-foreground">
              개발자를 위해, 개발자가 만든 서비스입니다
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}

function FeatureCard({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="rounded-lg border border-border bg-background p-6">
      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
        {icon}
      </div>
      <h3 className="mt-4 font-medium text-foreground">{title}</h3>
      <p className="mt-2 text-sm text-muted-foreground">{description}</p>
    </div>
  );
}

export default function LandingPage() {
  return (
    <AuthProvider>
      <LandingContent />
    </AuthProvider>
  );
}