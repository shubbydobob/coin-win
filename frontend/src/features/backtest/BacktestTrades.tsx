import { duration, instant, money, orNothing, price } from "../../format";
import { DIRECTION, EXIT_REASON } from "../../shared/labels";
import type { components } from "../../api/schema";

type Trade = components["schemas"]["TradeResponse"];

/**
 * 백테스트가 낸 거래 목록.
 *
 * 이 표가 있어야 하는 이유는 `rationale` 때문이다 — "지지대 59000.00~59200.00 (터치 3회)
 * 근단 반전 진입" 이 **왜 그 거래가 섰는지**를 문장으로 갖고 있다. 요약만 보고 판단하지
 * 않도록 원자료를 함께 낸다(§ 6.4).
 */
export function BacktestTrades({ trades }: { trades: readonly Trade[] }) {
  if (trades.length === 0) {
    return <p className="text-sm text-slate-500">이 구간에서 선 거래가 없다</p>;
  }

  return (
    <table className="w-full text-right text-sm tabular-nums">
      <caption className="mb-2 text-left text-sm font-medium text-slate-700">
        거래 {trades.length}건
      </caption>
      <thead className="border-b border-slate-300 text-xs text-slate-500">
        <tr>
          <th scope="col" className="py-1 text-left">진입</th>
          <th scope="col" className="py-1">방향</th>
          <th scope="col" className="py-1">평단</th>
          <th scope="col" className="py-1">청산가</th>
          <th scope="col" className="py-1">보유</th>
          <th scope="col" className="py-1">이유</th>
          <th scope="col" className="py-1">손익</th>
          <th scope="col" className="py-1 text-left">근거</th>
        </tr>
      </thead>
      <tbody>
        {trades.map((trade) => (
          <tr key={trade.id} className="border-b border-slate-100 align-top">
            <th scope="row" className="py-1 text-left font-normal">
              {orNothing(trade.entry, (entry) => instant(entry.openedAt))}
            </th>
            <td className="py-1">{DIRECTION[trade.plan.direction]}</td>
            <td className="py-1">{orNothing(trade.entry, (entry) => price(entry.averageEntryPrice))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => price(outcome.exitPrice))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => duration(outcome.holdingPeriod))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => EXIT_REASON[outcome.exitReason])}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => money(outcome.realizedPnl))}</td>
            <td className="py-1 text-left text-xs text-slate-500">
              {orNothing(trade.entry, (entry) => entry.rationale)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
