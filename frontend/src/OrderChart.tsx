import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { Row } from "./api";
export default function OrderChart({ data }: { data: Row[] }) {
  return (
    <ResponsiveContainer width="100%" height={235}>
      <AreaChart
        data={data}
        margin={{ top: 15, right: 12, bottom: 0, left: -25 }}
      >
        <defs>
          <linearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#267a60" stopOpacity={0.2} />
            <stop offset="100%" stopColor="#267a60" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid
          strokeDasharray="3 5"
          vertical={false}
          stroke="#e8ece8"
        />
        <XAxis
          dataKey="day"
          tickFormatter={(s: string) => s.slice(5)}
          axisLine={false}
          tickLine={false}
          tick={{ fontSize: 11, fill: "#839089" }}
        />
        <YAxis
          allowDecimals={false}
          axisLine={false}
          tickLine={false}
          tick={{ fontSize: 11, fill: "#839089" }}
        />
        <Tooltip />
        <Area
          type="monotone"
          dataKey="orders"
          stroke="#267a60"
          strokeWidth={2.5}
          fill="url(#fill)"
        />
        <Area
          type="monotone"
          dataKey="delivered"
          stroke="#a8bc80"
          strokeWidth={2}
          fill="transparent"
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
