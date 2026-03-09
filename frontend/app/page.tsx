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
            Fast, reliable, and free
          </div>
          
          <h1 className="text-balance text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
            Transform long URLs into
            <span className="text-primary"> powerful short links</span>
          </h1>
          
          <p className="mx-auto mt-6 max-w-2xl text-pretty text-lg text-muted-foreground">
            Create short, memorable links in seconds. Track clicks, analyze 
            performance, and manage all your links from a single dashboard.
          </p>
          
          <div className="mx-auto mt-10 max-w-xl">
            <UrlShortenerForm />
          </div>
          
          <p className="mt-4 text-sm text-muted-foreground">
            No account required.{' '}
            <Link href="/signup" className="text-primary hover:underline">
              Sign up free
            </Link>{' '}
            for advanced features.
          </p>
        </div>
      </section>
      
      {/* Features Section */}
      <section className="border-t border-border bg-card">
        <div className="mx-auto max-w-6xl px-4 py-20">
          <div className="text-center">
            <h2 className="text-2xl font-semibold text-foreground sm:text-3xl">
              Everything you need to manage your links
            </h2>
            <p className="mx-auto mt-4 max-w-2xl text-muted-foreground">
              ShortLink provides all the tools you need to create, manage, and 
              track your short URLs with detailed analytics.
            </p>
          </div>
          
          <div className="mt-16 grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
            <FeatureCard
              icon={<Link2 className="h-5 w-5" />}
              title="Instant Shortening"
              description="Transform any URL into a short, memorable link in milliseconds. No waiting, no hassle."
            />
            <FeatureCard
              icon={<BarChart3 className="h-5 w-5" />}
              title="Detailed Analytics"
              description="Track clicks, referrers, and geographic data. Understand your audience like never before."
            />
            <FeatureCard
              icon={<Shield className="h-5 w-5" />}
              title="Link Management"
              description="Activate, deactivate, or delete links anytime. Full control over your shortened URLs."
            />
          </div>
        </div>
      </section>
      
      {/* CTA Section */}
      <section className="border-t border-border">
        <div className="mx-auto max-w-4xl px-4 py-20 text-center">
          <h2 className="text-2xl font-semibold text-foreground sm:text-3xl">
            Ready to get started?
          </h2>
          <p className="mt-4 text-muted-foreground">
            Create your free account and start shortening URLs in seconds.
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-4 sm:flex-row">
            <Button size="lg" asChild>
              <Link href="/signup">
                Get started free
                <ArrowRight className="ml-2 h-4 w-4" />
              </Link>
            </Button>
            <Button variant="outline" size="lg" asChild>
              <Link href="/login">
                Sign in to your account
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
              Built for developers, by developers.
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
