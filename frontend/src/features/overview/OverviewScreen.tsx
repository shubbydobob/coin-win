import { useQuery } from "@tanstack/react-query";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { instant, percent, quantity, ratio } from "../../format";
import { ActiveTrades } from "../../shared/ActiveTrades";
import { JournalSummaryPanel } from "../../shared/JournalSummaryPanel";
import { PositionReconciliationPanel } from "../../shared/PositionReconciliation";
import { SmallButton } from "../../shared/SmallButton";
import { Term } from "../../shared/Term";

const SYMBOL = "BTCUSDT";

/**
 * 계좌를 다시 묻는 주기.
 *
 * **비용은 서명 요청 하나다.** `BinanceServerClock` 이 거래소 시각을 15분 캐시하므로 폴링 한
 * 번이 왕복 하나이고, 분당 넷은 개인 엔드포인트 한도에 견줘 무시할 수 있다.
 *
 * **탭이 숨겨지면 멈춘다.** `refetchIntervalInBackground` 를 켜지 않은 것이 그 뜻이다 — 아무도
 * 보고 있지 않은 화면 때문에 서명 요청을 계속 쓸 이유가 없다.
 *
 * **오류가 나면 멈춘다.** 위 `refetchInterval` 이 그것이다. 키가 없어서 503 인 경우가 대부분인데
 * 그것은 기다린다고 달라지지 않고, 15초마다 같은 실패를 되풀이하면 로그가 그 실패로 덮인다.
 * 다시 켜는 자리는 사람이 누르는 "다시 시도" 다.
 */
const ACCOUNT_POLL_MS = 15_000;

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
    // 이 화면에서 유일하게 **매 순간 달라지는** 값이라 혼자 폴링한다. 미실현 손익과 청산가는
    // 가격이 움직이면 같이 움직이고, 멈춘 수를 띄워 두면 사람이 그것을 현재로 읽는다.
    // 시장 지표는 폴링하지 않는다 — 펀딩비는 8시간에 한 번 갱신된다.
    refetchInterval: (query) => (query.state.error ? false : ACCOUNT_POLL_MS),
  });

  return (
    <div className="space-y-8">
      <section aria-label="진행 중">
        {active.data && <ActiveTrades trades={active.data} />}
      </section>

      {positions.data ? (
        <PositionReconciliationPanel
          reconciliation={positions.data}
          onRefresh={() => positions.refetch()}
          refreshing={positions.isFetching}
        />
      ) : (
        positions.error && (
          // 오류일 때도 이름표를 붙인다. 이 화면에는 "다시 시도" 가 둘이고, 영역 이름이
          // 없으면 사람도 스크린리더도 어느 쪽을 누르는지 알 수 없다.
          <section aria-label="거래소 대조" className="flex items-center gap-3 text-sm">
            <span className="text-slate-500">
              {positions.error instanceof ApiFailure
                ? positions.error.problem.detail
                : "거래소 포지션을 가져오지 못했다"}
            </span>
            {/* 폴링은 오류에서 멈춰 있다. 이 버튼이 그것을 다시 켜는 유일한 자리다. */}
            <SmallButton onClick={() => positions.refetch()}>다시 시도</SmallButton>
          </section>
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
              <SmallButton onClick={() => metrics.refetch()}>다시 시도</SmallButton>
            )}
          </div>
        )}
      </section>

      {summary.data && <JournalSummaryPanel summary={summary.data} />}
    </div>
  );
}
