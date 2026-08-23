import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { money } from "../format";

/**
 * 자산 곡선. **거래마다 한 점이고 첫 점이 초기 자본이다.**
 *
 * `/backtest` 의 실제 자산 곡선과 `/projection` 의 시뮬레이션 곡선이 같은 모양이라 `shared/`
 * 로 올렸다 — 두 번째 사용처가 생겼을 때만 만든다는 § 4 의 규칙 그대로다. 점의 뜻은 두 화면에서
 * 같다: **거래 하나가 점 하나이고 첫 점은 거래 이전의 자본이다.**
 *
 * 가격 캔들 차트는 두지 않는다 — Phase 6 의 결론은 "이 규칙에는 엣지가 없다" 였고, 지금
 * 필요한 것은 예쁜 차트가 아니라 거래 목록을 눈으로 훑는 것이다(§ 6.4).
 *
 * 축 눈금도 `format/` 을 지난다. 차트 라이브러리에 자릿수를 맡기면 그 순간 표와 차트가 다른
 * 수를 말한다.
 *
 * **점의 뜻은 부르는 쪽이 말한다(`point`).** 목표 복리 화면은 점이 거래가 아니라 달이다 —
 * 기본값을 그대로 쓰면 툴팁이 `3번째 거래` 라고 적고, 그것은 자릿수를 줄이는 것과 같은
 * 종류의 거짓말이다.
 */
export function EquityChart({
  equity,
  label,
  point = "번째 거래",
}: {
  equity: readonly number[];
  label: string;
  point?: string;
}) {
  const points = equity.map((value, at) => ({ at, value }));

  return (
    <figure>
      <figcaption className="mb-2 text-sm font-medium text-ink">{label}</figcaption>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="at" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={money} width={80} tick={{ fontSize: 11 }} />
            <Tooltip
              formatter={(value) => (typeof value === "number" ? money(value) : String(value))}
              labelFormatter={(at) => `${at}${point}`}
            />
            {/*
              색을 상수로 박지 않는다. `#0f172a` 였던 자리이고, 다크 테마가 들어온 뒤로
              **배경과 같은 어둠이라 선이 보이지 않았다** — 차트가 격자만 그린 채 통과하고
              있었다. 토큰을 쓰면 테마가 바뀔 때 같은 일이 다시 생기지 않는다.
            */}
            <Line type="monotone" dataKey="value" dot={false} stroke="var(--color-ink)" />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </figure>
  );
}
