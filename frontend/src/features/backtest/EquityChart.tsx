import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { money } from "../../format";

/**
 * 자산 곡선. **거래마다 한 점이고 첫 점이 초기 자본이다.**
 *
 * 가격 캔들 차트는 두지 않는다 — Phase 6 의 결론은 "이 규칙에는 엣지가 없다" 였고, 지금
 * 필요한 것은 예쁜 차트가 아니라 거래 목록을 눈으로 훑는 것이다(§ 6.4).
 *
 * 축 눈금도 `format/` 을 지난다. 차트 라이브러리에 자릿수를 맡기면 그 순간 표와 차트가 다른
 * 수를 말한다.
 */
export function EquityChart({ equity, label }: { equity: readonly number[]; label: string }) {
  const points = equity.map((value, at) => ({ at, value }));

  return (
    <figure>
      <figcaption className="mb-2 text-sm font-medium text-slate-700">{label}</figcaption>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="at" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={money} width={80} tick={{ fontSize: 11 }} />
            <Tooltip
              formatter={(value) => (typeof value === "number" ? money(value) : String(value))}
              labelFormatter={(at) => `${at}번째 거래`}
            />
            <Line type="monotone" dataKey="value" dot={false} stroke="#0f172a" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </figure>
  );
}
