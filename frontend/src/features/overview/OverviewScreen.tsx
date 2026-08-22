import { useQuery } from "@tanstack/react-query";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { instant, percent, quantity, ratio } from "../../format";
import { ActiveTrades } from "../../shared/ActiveTrades";
import { JournalSummaryPanel } from "../../shared/JournalSummaryPanel";

const SYMBOL = "BTCUSDT";

/**
 * 현황. **지금 무엇이 열려 있는가에만 답한다.**
 *
 * 차트를 두지 않는다 — 이 화면은 질문 하나에 답하고 끝난다(§ 6.1). 진행 중인 거래에 동작
 * 버튼도 두지 않는다. 기록을 고치는 자리는 `/journal` 이고, 여기서도 되면 같은 일을 두 곳에서
 * 하게 된다.
 *
 * **시장 지표는 실패해도 화면이 죽지 않는다.** 거래소가 안 닿는 것은 503 이고, 그 블록만
 * "가져오지 못했다" 로 두고 나머지는 그대로 보인다.
 */
export function OverviewScreen() {
  const active = useQuery({ queryKey: ["trades", "active"], queryFn: () => get("/api/trades/active") });
  const summary = useQuery({
    queryKey: ["trades", "summary", {}],
    queryFn: () => get("/api/trades/summary"),
  });
  const metrics = useQuery({
    queryKey: ["markets", SYMBOL, "metrics"],
    queryFn: () => get("/api/markets/{symbol}/metrics", { path: { symbol: SYMBOL } }),
  });

  return (
    <div className="space-y-8">
      <section aria-label="진행 중">
        {active.data && <ActiveTrades trades={active.data} />}
      </section>

      <section aria-label="시장 지표" className="rounded border border-slate-200 p-3">
        <h2 className="text-xs text-slate-500">{SYMBOL}</h2>

        {metrics.data ? (
          <dl className="mt-2 grid grid-cols-4 gap-x-4 text-sm tabular-nums">
            <dt className="text-slate-500">펀딩비</dt>
            <dd className="text-right">{percent(metrics.data.fundingRatePercent)}</dd>
            <dt className="text-slate-500">미결제약정</dt>
            <dd className="text-right">{quantity(metrics.data.openInterest)}</dd>
            <dt className="text-slate-500">롱숏비율</dt>
            <dd className="text-right">{ratio(metrics.data.longShortRatio)}</dd>
            <dt className="text-slate-500">관측 시각</dt>
            <dd className="text-right">{instant(metrics.data.at)}</dd>
          </dl>
        ) : (
          <div className="mt-2 flex items-center gap-3 text-sm">
            <span className="text-slate-500">
              {metrics.isPending
                ? "가져오는 중"
                : metrics.error instanceof ApiFailure
                  ? metrics.error.problem.detail
                  : "시장 지표를 가져오지 못했다"}
            </span>
            {metrics.error && (
              <button
                type="button"
                onClick={() => metrics.refetch()}
                className="rounded border border-slate-300 px-2 py-0.5 text-xs"
              >
                다시 시도
              </button>
            )}
          </div>
        )}
      </section>

      {summary.data && <JournalSummaryPanel summary={summary.data} />}
    </div>
  );
}
