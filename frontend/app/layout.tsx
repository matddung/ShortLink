import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import './globals.css'

const _geist = Geist({ subsets: ["latin"] });
const _geistMono = Geist_Mono({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: 'ShortLink - URL Shortener',
  description: 'Transform long URLs into short, trackable links. Monitor your link performance with detailed analytics.',
  generator: 'v0.app',
  icons: {
    icon: [
      {
        url: '/favicon_48.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/favicon_48.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/shortlink_icon_transparent.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/favicon_48.png',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en">
      <body className="font-sans antialiased">
        {children}
        <Analytics />
      </body>
    </html>
  )
}
