import { Badge } from '@/components/ui/badge';
import type { LinkStatus } from '@/lib/types';
import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: LinkStatus;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const statusConfig: Record<LinkStatus, { label: string; className: string }> = {
    active: {
      label: 'Active',
      className: 'bg-primary/10 text-primary border-primary/20',
    },
    inactive: {
      label: 'Inactive',
      className: 'bg-muted text-muted-foreground border-border',
    },
  };

  const config = statusConfig[status];

  return (
    <Badge variant="outline" className={cn(config.className, className)}>
      {config.label}
    </Badge>
  );
}
