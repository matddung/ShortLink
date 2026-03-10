'use client';

import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { DailyClick } from '@/lib/types';

interface DailyClicksChartProps {
  data: DailyClick[];
}

export function DailyClicksChart({ data }: DailyClicksChartProps) {
  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  };

  return (
    <div className="rounded-lg border border-border bg-card p-5">
      <h3 className="font-medium text-foreground">Daily Clicks</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        Click activity over the last 14 days (today cumulative)
      </p>
      
      <div className="mt-6 h-64">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="clickGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="hsl(160, 60%, 45%)" stopOpacity={0.3} />
                <stop offset="95%" stopColor="hsl(160, 60%, 45%)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid 
              strokeDasharray="3 3" 
              stroke="hsl(260, 3%, 28%)" 
              vertical={false}
            />
            <XAxis 
              dataKey="date" 
              tickFormatter={formatDate}
              stroke="hsl(0, 0%, 65%)"
              fontSize={12}
              tickLine={false}
              axisLine={false}
            />
            <YAxis 
              stroke="hsl(0, 0%, 65%)"
              fontSize={12}
              tickLine={false}
              axisLine={false}
              width={40}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: 'hsl(260, 3%, 17%)',
                border: '1px solid hsl(260, 3%, 28%)',
                borderRadius: '8px',
                fontSize: '12px',
              }}
              labelFormatter={formatDate}
              formatter={(value: number) => [value, 'Clicks']}
            />
            <Area
              type="monotone"
              dataKey="clicks"
              stroke="hsl(160, 60%, 45%)"
              strokeWidth={2}
              fill="url(#clickGradient)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
