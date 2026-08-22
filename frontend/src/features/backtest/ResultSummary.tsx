import { money, orNothing, percent, ratio } from "../../format";
import type { components } from "../../api/schema";

type Summary = components["schemas"]["SummaryResponse"];

/**
 * 성적 요약.
 *
 * **`profitFactor` 는 null 일 수 있다**(진 거래가 없는 표본). `0` 으로 채우지 않는다 —
 * "손익비가 0" 과 "손익비를 말할 수 없다" 는 다른 사실이다(§ 6.4).
 */
export function ResultSummary({ summary, label }: { summary: Summary; label: string }) {
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums" aria-label={label}>
      <dt className="text-slate-500">거래</dt>
      <dd className="text-right">{summary.totalTrades}</dd>
      <dt className="text-slate-500">승률</dt>
      <dd className="text-right">{percent(summary.winRate)}</dd>
      <dt className="text-slate-500">손익비</dt>
      <dd className="text-right">{orNothing(summary.profitFactor, ratio)}</dd>
      <dt className="text-slate-500">순손익</dt>
      <dd className="text-right">{money(summary.netPnl)}</dd>
      <dt className="text-slate-500">최종 자산</dt>
      <dd className="text-right">{money(summary.finalEquity)}</dd>
      <dt className="text-slate-500">최대낙폭</dt>
      <dd className="text-right">{percent(summary.maxDrawdown)}</dd>
    </dl>
  );
}
