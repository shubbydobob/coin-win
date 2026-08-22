import { money, percent, ratio } from "../../format";
import { PercentileBar } from "./PercentileBar";
import type { components } from "../../api/schema";

type Distribution = components["schemas"]["MonteCarloResponse"];

/**
 * 몬테카를로 결과.
 *
 * 이 화면이 답해야 하는 질문은 **"얼마 버나" 가 아니라 "운이 나쁘면 어디까지 가나"** 다
 * (§ 6.5). Phase 2 의 결론이 "같은 기댓값에서도 경로에 따라 결과가 갈린다" 이기 때문이다.
 * 그래서 하위 5% 와 최악 낙폭을 중앙값보다 크게 놓는다.
 */
export function MonteCarloResult({ distribution }: { distribution: Distribution }) {
  return (
    <section className="space-y-4" aria-label="몬테카를로 결과">
      <div className="grid grid-cols-2 gap-4">
        <div className="rounded border border-slate-300 p-3">
          <h3 className="text-xs text-slate-500">운이 나쁘면 (하위 5%)</h3>
          <p className="mt-1 text-2xl tabular-nums">{money(distribution.percentile5Equity)}</p>
        </div>
        <div className="rounded border border-slate-300 p-3">
          <h3 className="text-xs text-slate-500">가장 깊었던 낙폭</h3>
          <p className="mt-1 text-2xl tabular-nums">{percent(distribution.worstMaxDrawdown)}</p>
        </div>
      </div>

      <PercentileBar
        worst={distribution.worstEquity}
        p5={distribution.percentile5Equity}
        median={distribution.medianEquity}
        p95={distribution.percentile95Equity}
        best={distribution.bestEquity}
      />

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
        <dt className="text-slate-500">초기 자본에 못 미친 비율</dt>
        <dd className="text-right">{percent(distribution.lossProbability)}</dd>
        <dt className="text-slate-500">낙폭 중앙값</dt>
        <dd className="text-right">{percent(distribution.medianMaxDrawdown)}</dd>
        <dt className="text-slate-500">거래당 기댓값 (R)</dt>
        <dd className="text-right">{ratio(distribution.expectancyPerTrade)}</dd>
        <dt className="text-slate-500">시행 / 시행당 거래</dt>
        <dd className="text-right">{distribution.runs} / {distribution.tradesPerRun}</dd>
      </dl>
    </section>
  );
}
