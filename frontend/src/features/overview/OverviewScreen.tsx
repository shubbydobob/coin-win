import { useQuery } from "@tanstack/react-query";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { instant, percent, quantity, ratio } from "../../format";
import { ActiveTrades } from "../../shared/ActiveTrades";
import { JournalSummaryPanel } from "../../shared/JournalSummaryPanel";
import { PositionReconciliationPanel } from "../../shared/PositionReconciliation";
import { Term } from "../../shared/Term";

const SYMBOL = "BTCUSDT";

/**
 * 현황. **지금 무엇이 열려 있는가에만 답한다.**
 *
 * 차트를 두지 않는다 — 이 화면은 질문 하나에 답하고 끝난다(§ 6.1). 진행 중인 거래에 동작
 * 버튼도 두지 않는다. 기록을 고치는 자리는 `/journal` 이고, 여기서도 되면 같은 일을 두 곳에서
 * 하게 된다.
 *
 * **시장 지표는 실패해도 화면이 죽지 않는다.** 거래소가 안 닿는 것은 503 이고, 그 블록만
 * "가져오지 못했다" 로 두고 나머지는 그대로 보인다. 거래소 계정 대조도 같다 — 키가 없으면
 * 그 자리만 비활성이고 기록과 집계는 그대로 보인다.
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
  // 거래소 계정은 키가 있어야 붙는다. 없으면 503 이고, 그때는 이 블록만 비활성이다.
  // 재시도하지 않는다 — 키가 없는 것은 기다린다고 달라지지 않는다.
  const positions = useQuery({
    queryKey: ["account", "positions"],
    queryFn: () => get("/api/account/positions"),
    retry: false,
  });

  return (
    <div className="space-y-8">
      <section aria-label="진행 중">
        {active.data && <ActiveTrades trades={active.data} />}
      </section>

      {positions.data ? (
        <PositionReconciliationPanel reconciliation={positions.data} />
      ) : (
        positions.error && (
          <p className="text-sm text-slate-500">
            {positions.error instanceof ApiFailure
              ? positions.error.problem.detail
              : "거래소 포지션을 가져오지 못했다"}
          </p>
        )
      )}

      <section aria-label="시장 지표" className="rounded border border-slate-200 p-3">
        <h2 className="text-xs text-slate-500">{SYMBOL}</h2>

        {metrics.data ? (
          <dl className="mt-2 grid grid-cols-[1fr_auto_1fr_auto] items-start gap-x-4 gap-y-2 text-sm tabular-nums">
            <Term label="펀딩비" hint="8시간마다 오가는 수수료. 양수면 롱이 숏에게 낸다." />
            <dd className="text-right">{percent(metrics.data.fundingRatePercent)}</dd>
            <Term label="미결제약정" hint="아직 닫히지 않은 계약의 합(BTC). 늘면서 가격이 오르면 신규 롱이 들어온 것이다." />
            <dd className="text-right">{quantity(metrics.data.openInterest)}</dd>
            <Term label="롱숏비율" hint="롱 계정 수 ÷ 숏 계정 수. 금액이 아니라 계정 수 기준이다." />
            <dd className="text-right">{ratio(metrics.data.longShortRatio)}</dd>
            <Term label="관측 시각" hint="거래소가 이 세 값을 낸 시각. 지금이 아니다." />
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
