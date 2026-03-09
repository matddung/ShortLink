'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Copy, Check, ExternalLink, MoreHorizontal, Trash2, Power, PowerOff, BarChart3 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { StatusBadge } from '@/components/status-badge';
import type { Link as LinkType } from '@/lib/types';
import { cn } from '@/lib/utils';

interface LinkTableProps {
  links: LinkType[];
  onDelete: (id: string) => void;
  onToggleStatus: (id: string, status: 'active' | 'inactive') => void;
  isDeleting?: string;
  isUpdating?: string;
}

export function LinkTable({ links, onDelete, onToggleStatus, isDeleting, isUpdating }: LinkTableProps) {
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const copyToClipboard = async (link: LinkType) => {
    try {
      await navigator.clipboard.writeText(link.shortUrl);
      setCopiedId(link.id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      console.error('Failed to copy');
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  const formatClicks = (clicks: number) => {
    if (clicks >= 1000) {
      return `${(clicks / 1000).toFixed(1)}k`;
    }
    return clicks.toString();
  };

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      {/* Header */}
      <div className="hidden border-b border-border bg-muted/50 px-4 py-3 text-xs font-medium uppercase tracking-wide text-muted-foreground sm:grid sm:grid-cols-12 sm:gap-4">
        <div className="col-span-5">Link</div>
        <div className="col-span-2">Status</div>
        <div className="col-span-2">Clicks</div>
        <div className="col-span-2">Created</div>
        <div className="col-span-1"></div>
      </div>

      {/* Rows */}
      <div className="divide-y divide-border">
        {links.map((link) => (
          <div
            key={link.id}
            className={cn(
              'flex flex-col gap-3 px-4 py-4 sm:grid sm:grid-cols-12 sm:items-center sm:gap-4',
              (isDeleting === link.id || isUpdating === link.id) && 'opacity-50'
            )}
          >
            {/* Link Info */}
            <div className="col-span-5 min-w-0">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => copyToClipboard(link)}
                  className="flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                >
                  {link.shortUrl.replace('https://', '')}
                  {copiedId === link.id ? (
                    <Check className="h-3 w-3 text-primary" />
                  ) : (
                    <Copy className="h-3 w-3 opacity-50" />
                  )}
                </button>
                <a
                  href={link.shortUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-muted-foreground hover:text-foreground"
                >
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>
              <p className="mt-1 truncate text-xs text-muted-foreground">
                {link.originalUrl}
              </p>
            </div>

            {/* Status */}
            <div className="col-span-2 flex items-center gap-2 sm:block">
              <span className="text-xs text-muted-foreground sm:hidden">Status:</span>
              <StatusBadge status={link.status} />
            </div>

            {/* Clicks */}
            <div className="col-span-2 flex items-center gap-2 sm:block">
              <span className="text-xs text-muted-foreground sm:hidden">Clicks:</span>
              <span className="font-medium text-foreground">{formatClicks(link.totalClicks)}</span>
            </div>

            {/* Created */}
            <div className="col-span-2 flex items-center gap-2 sm:block">
              <span className="text-xs text-muted-foreground sm:hidden">Created:</span>
              <span className="text-sm text-muted-foreground">{formatDate(link.createdAt)}</span>
            </div>

            {/* Actions */}
            <div className="col-span-1 flex justify-end">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon" className="h-8 w-8">
                    <MoreHorizontal className="h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem asChild>
                    <Link href={`/dashboard/links/${link.id}`}>
                      <BarChart3 className="mr-2 h-4 w-4" />
                      View Analytics
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  {link.status === 'active' ? (
                    <DropdownMenuItem onClick={() => onToggleStatus(link.id, 'inactive')}>
                      <PowerOff className="mr-2 h-4 w-4" />
                      Deactivate
                    </DropdownMenuItem>
                  ) : link.status === 'inactive' ? (
                    <DropdownMenuItem onClick={() => onToggleStatus(link.id, 'active')}>
                      <Power className="mr-2 h-4 w-4" />
                      Activate
                    </DropdownMenuItem>
                  ) : null}
                  <DropdownMenuItem 
                    onClick={() => onDelete(link.id)}
                    className="text-destructive focus:text-destructive"
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
