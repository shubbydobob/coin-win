import { duration, instant, money, orNothing, price } from "../../format";
import { DIRECTION, EXIT_REASON } from "../../shared/labels";
import type { components } from "../../api/schema";

type Trade = components["schemas"]["TradeResponse"];

/**
 * 끝난 거래 목록.
 *
 * `entry` 와 `outcome` 은 타입이 null 을 담고 있다(§ 5.4). 이 엔드포인트는 끝난 거래만 내지만
 * **그것을 화면이 단정하지 않는다** — 없으면 `—` 로 둔다. 타입이 말하는 것을 화면이 무시하기
 * 시작하면 § 5.4 가 되살린 사실이 다시 지워진다.
 */
export function ClosedTradeTable({ trades }: { trades: readonly Trade[] }) {
  if (trades.length === 0) {
    return <p className="text-sm text-slate-500">조건에 드는 거래가 없다</p>;
  }

  return (
    <table className="w-full text-right text-sm tabular-nums">
      <caption className="mb-2 text-left text-sm font-medium text-slate-700">끝난 거래</caption>
      <thead className="border-b border-slate-300 text-xs text-slate-500">
        <tr>
          <th scope="col" className="py-1 text-left">진입</th>
          <th scope="col" className="py-1">방향</th>
          <th scope="col" className="py-1">평단</th>
          <th scope="col" className="py-1">청산가</th>
          <th scope="col" className="py-1">보유</th>
          <th scope="col" className="py-1">청산 이유</th>
          <th scope="col" className="py-1">계획</th>
          <th scope="col" className="py-1">실현 손익</th>
        </tr>
      </thead>
      <tbody>
        {trades.map((trade) => (
          <tr key={trade.id} id={`trade-${trade.id}`} className="border-b border-slate-100">
            <th scope="row" className="py-1 text-left font-normal">
              {orNothing(trade.entry, (entry) => instant(entry.openedAt))}
            </th>
            <td className="py-1">{DIRECTION[trade.plan.direction]}</td>
            <td className="py-1">{orNothing(trade.entry, (entry) => price(entry.averageEntryPrice))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => price(outcome.exitPrice))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => duration(outcome.holdingPeriod))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => EXIT_REASON[outcome.exitReason])}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => (outcome.followedPlan ? "지킴" : "어김"))}</td>
            <td className="py-1">{orNothing(trade.outcome, (outcome) => money(outcome.realizedPnl))}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
