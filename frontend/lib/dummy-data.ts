import type { User, Link, LinkStats } from './types';

export const dummyUser: User = {
  id: 'user-1',
  email: 'demo@shortlink.io',
  name: 'Demo User',
  createdAt: '2024-01-15T10:00:00Z',
};

export const dummyLinks: Link[] = [
  {
    id: 'link-1',
    originalUrl: 'https://vercel.com/docs/getting-started-with-vercel',
    shortCode: 'vercel-docs',
    shortUrl: 'https://shrt.link/vercel-docs',
    createdAt: '2024-03-01T10:30:00Z',
    status: 'active',
    totalClicks: 1247,
    userId: 'user-1',
  },
  {
    id: 'link-2',
    originalUrl: 'https://nextjs.org/docs/app/building-your-application/routing',
    shortCode: 'nextjs-routing',
    shortUrl: 'https://shrt.link/nextjs-routing',
    createdAt: '2024-02-28T14:20:00Z',
    status: 'active',
    totalClicks: 892,
    userId: 'user-1',
  },
  {
    id: 'link-3',
    originalUrl: 'https://react.dev/reference/react/hooks',
    shortCode: 'react-hooks',
    shortUrl: 'https://shrt.link/react-hooks',
    createdAt: '2024-02-25T09:15:00Z',
    expiresAt: '2024-02-26T09:15:00Z',
    status: 'expired',
    totalClicks: 456,
    userId: 'user-1',
  },
  {
    id: 'link-4',
    originalUrl: 'https://tailwindcss.com/docs/installation',
    shortCode: 'tw-install',
    shortUrl: 'https://shrt.link/tw-install',
    createdAt: '2024-02-20T16:45:00Z',
    status: 'inactive',
    totalClicks: 234,
    userId: 'user-1',
  },
  {
    id: 'link-5',
    originalUrl: 'https://github.com/vercel/next.js/tree/canary/examples',
    shortCode: 'nextjs-examples',
    shortUrl: 'https://shrt.link/nextjs-examples',
    createdAt: '2024-02-18T11:00:00Z',
    status: 'active',
    totalClicks: 1893,
    userId: 'user-1',
  },
  {
    id: 'link-6',
    originalUrl: 'https://ui.shadcn.com/docs/components/button',
    shortCode: 'shadcn-btn',
    shortUrl: 'https://shrt.link/shadcn-btn',
    createdAt: '2024-02-15T08:30:00Z',
    status: 'active',
    totalClicks: 567,
    userId: 'user-1',
  },
];

export const generateDummyStats = (linkId: string): LinkStats => {
  const link = dummyLinks.find((l) => l.id === linkId);
  const baseClicks = link?.totalClicks || 500;
  
  // Generate daily clicks for the last 14 days
  const dailyClicks = Array.from({ length: 14 }, (_, i) => {
    const date = new Date();
    date.setDate(date.getDate() - (13 - i));
    return {
      date: date.toISOString().split('T')[0],
      clicks: Math.floor(Math.random() * (baseClicks / 10)) + 10,
    };
  });

  return {
    totalClicks: baseClicks,
    uniqueClicks: Math.floor(baseClicks * 0.78),
    lastClickedAt: new Date(Date.now() - Math.random() * 3600000).toISOString(),
    referrers: [
      { source: 'Direct', count: Math.floor(baseClicks * 0.35), percentage: 35 },
      { source: 'Twitter', count: Math.floor(baseClicks * 0.25), percentage: 25 },
      { source: 'Google', count: Math.floor(baseClicks * 0.2), percentage: 20 },
      { source: 'GitHub', count: Math.floor(baseClicks * 0.12), percentage: 12 },
      { source: 'Other', count: Math.floor(baseClicks * 0.08), percentage: 8 },
    ],
    dailyClicks,
    topCountries: [
      { country: 'United States', count: Math.floor(baseClicks * 0.4) },
      { country: 'Germany', count: Math.floor(baseClicks * 0.15) },
      { country: 'United Kingdom', count: Math.floor(baseClicks * 0.12) },
      { country: 'Japan', count: Math.floor(baseClicks * 0.08) },
      { country: 'Canada', count: Math.floor(baseClicks * 0.06) },
    ],
  };
};

// Simulate API delay
export const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
