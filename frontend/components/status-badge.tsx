import { Badge } from '@/components/ui/badge';
import type { LinkStatus } from '@/lib/types';
import { cn } from '@/lib/utils';

interface StatusBadgeProps {
  status: LinkStatus;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const statusConfig = {
    active: {
      label: '활성',
      className: 'bg-primary/10 text-primary border-primary/20',
    },
    inactive: {
      label: '비활성',
      className: 'bg-muted text-muted-foreground border-border',
    },
    expired: {
      label: '만료',
      className: 'bg-destructive/10 text-destructive border-destructive/20',
    },
  };

  const config = statusConfig[status];

  return (
    <Badge variant="outline" className={cn(config.className, className)}>
      {config.label}
    </Badge>
  );
}
