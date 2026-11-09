import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DailyCount } from '@/types';

const label = (date: string) =>
  new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`));

function ChartTooltip({ active, payload }: { active?: boolean; payload?: readonly { payload: DailyCount }[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="rounded-md border bg-popover px-2.5 py-1.5 text-xs shadow-pop">
      <p className="text-muted-foreground">{label(point.date)}</p>
      <p className="font-medium tabular-nums">
        {point.count} {point.count === 1 ? 'question' : 'questions'}
      </p>
    </div>
  );
}

/** Single series over time: one hue, thin rounded bars, recessive axes, no legend. */
export function QuestionsChart({ data }: { data: DailyCount[] }) {
  return (
    <>
      <div className="h-44" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: -28 }} barCategoryGap="30%">
            <XAxis
              dataKey="date"
              tickFormatter={label}
              tickLine={false}
              axisLine={{ stroke: 'hsl(var(--border))' }}
              tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
              minTickGap={28}
            />
            <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }} width={48} />
            <Tooltip content={<ChartTooltip />} cursor={{ fill: 'hsl(var(--accent))' }} />
            <Bar dataKey="count" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} maxBarSize={22} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>Questions asked per day</caption>
        <thead>
          <tr>
            <th>Date</th>
            <th>Questions</th>
          </tr>
        </thead>
        <tbody>
          {data.map((point) => (
            <tr key={point.date}>
              <td>{point.date}</td>
              <td>{point.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
