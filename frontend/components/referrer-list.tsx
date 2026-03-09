import type { ReferrerStat } from '@/lib/types';

interface ReferrerListProps {
  referrers: ReferrerStat[];
}

export function ReferrerList({ referrers }: ReferrerListProps) {
  return (
    <div className="rounded-lg border border-border bg-card p-5">
      <h3 className="font-medium text-foreground">Top Referrers</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        Where your traffic is coming from
      </p>
      
      <div className="mt-6 space-y-4">
        {referrers.map((referrer) => (
          <div key={referrer.source}>
            <div className="flex items-center justify-between text-sm">
              <span className="text-foreground">{referrer.source}</span>
              <span className="text-muted-foreground">
                {referrer.count.toLocaleString()} ({referrer.percentage}%)
              </span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${referrer.percentage}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
