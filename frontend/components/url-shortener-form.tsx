'use client';

import { useState } from 'react';
import { Copy, Check, ArrowRight, ExternalLink } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { linksApi } from '@/lib/api-client';
import type { Link } from '@/lib/types';

export function UrlShortenerForm() {
  const [url, setUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [createdLink, setCreatedLink] = useState<Link | null>(null);
  const [copied, setCopied] = useState(false);

  const isValidUrl = (string: string) => {
    try {
      new URL(string);
      return true;
    } catch {
      return false;
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setCreatedLink(null);

    if (!url.trim()) {
      setError('URL을 입력해 주세요');
      return;
    }

    if (!isValidUrl(url)) {
      setError('올바른 URL을 입력해 주세요');
      return;
    }

    setIsLoading(true);
    const response = await linksApi.createAnonymous(url);
    setIsLoading(false);

    if (response.error) {
      setError(response.error);
      return;
    }

    if (response.data) {
      setCreatedLink(response.data);
      setUrl('');
    }
  };

  const copyToClipboard = async () => {
    if (!createdLink) return;
    
    try {
      await navigator.clipboard.writeText(createdLink.shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      console.error('복사에 실패했습니다');
    }
  };

  const handleNewLink = () => {
    setCreatedLink(null);
    setUrl('');
  };

  return (
    <div className="w-full">
      {!createdLink ? (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              type="text"
              placeholder="긴 URL을 여기에 붙여넣으세요..."
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="h-12 flex-1 bg-secondary text-foreground placeholder:text-muted-foreground"
            />
            <Button 
              type="submit" 
              disabled={isLoading} 
              className="h-12 px-6"
            >
              {isLoading ? (
                <Spinner className="h-4 w-4" />
              ) : (
                <>
                  단축하기
                  <ArrowRight className="ml-2 h-4 w-4" />
                </>
              )}
            </Button>
          </div>
          {error && (
            <p className="text-sm text-destructive">{error}</p>
          )}
        </form>
      ) : (
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex flex-col gap-4">
            <div>
              <p className="text-xs text-muted-foreground">원본 URL</p>
              <p className="mt-1 truncate text-sm text-foreground">
                {createdLink.originalUrl}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <div className="flex-1 rounded-md bg-secondary px-3 py-2">
                <a 
                  href={createdLink.shortUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 text-primary hover:underline"
                >
                  {createdLink.shortUrl}
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>
              <Button
                variant="outline"
                size="icon"
                onClick={copyToClipboard}
                className="shrink-0"
              >
                {copied ? (
                  <Check className="h-4 w-4 text-primary" />
                ) : (
                  <Copy className="h-4 w-4" />
                )}
              </Button>
            </div>
            <Button variant="ghost" onClick={handleNewLink} className="w-full">
              새 링크 만들기
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
