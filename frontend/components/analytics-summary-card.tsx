import { cn } from '@/lib/utils';
import type { ReactNode } from 'react';

interface AnalyticsSummaryCardProps {
  icon: ReactNode;
  label: string;
  value: string;
  subValue?: string;
  className?: string;
}

export function AnalyticsSummaryCard({
  icon,
  label,
  value,
  subValue,
  className,
}: AnalyticsSummaryCardProps) {
  return (
    <div className={cn('rounded-lg border border-border bg-card p-5', className)}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="mt-2 text-3xl font-semibold text-foreground">{value}</p>
          {subValue && (
            <p className="mt-1 text-sm text-muted-foreground">{subValue}</p>
          )}
        </div>
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
          {icon}
        </div>
      </div>
    </div>
  );
}
