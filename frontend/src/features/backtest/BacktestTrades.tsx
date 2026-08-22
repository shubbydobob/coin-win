import { instant, money, price } from "../../format";
import { DIRECTION, EXIT_REASON } from "../../shared/labels";
import type { components } from "../../api/schema";

type Trade = components["schemas"]["BacktestTradeResponse"];

/**
 * 백테스트가 낸 거래 목록.
 *
 * **매매 기록의 거래와 다른 타입이다.** 백테스트 거래에는 계획도 상태도 없다 — 이미 끝난
 * 한 건의 사실뿐이다. 그것이 화면에서 갈라져 있어야 하는 이유는 § 6.3 의 상태 전이가 여기에
 * 없기 때문이다.
 *
 * 스키마가 이 둘을 하나로 합쳐 놓았던 적이 있다(단순명이 같았다). 그때 이 표는 없는 필드를
 * 읽다 죽었고, **브라우저로 한 번 돌려 보기 전까지 아무도 몰랐다.**
 *
 * 이 표가 있어야 하는 이유는 `rationale` 때문이다 — "지지대 91800.00~91999.00 (터치 2회)
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
          <th scope="col" className="py-1">체결</th>
          <th scope="col" className="py-1">평단</th>
          <th scope="col" className="py-1">청산가</th>
          <th scope="col" className="py-1">이유</th>
          <th scope="col" className="py-1">손익</th>
          <th scope="col" className="py-1 pl-3 text-left">근거</th>
        </tr>
      </thead>
      <tbody>
        {trades.map((trade) => (
          <tr key={`${trade.openedAt}-${trade.averageEntryPrice}`} className="border-b border-slate-100 align-top">
            <th scope="row" className="py-1 text-left font-normal">{instant(trade.openedAt)}</th>
            <td className="py-1">{DIRECTION[trade.direction]}</td>
            <td className="py-1">{trade.filledEntries}</td>
            <td className="py-1">{price(trade.averageEntryPrice)}</td>
            <td className="py-1">{price(trade.exitPrice)}</td>
            <td className="py-1">{EXIT_REASON[trade.exitReason]}</td>
            <td className="py-1">{money(trade.realizedPnl)}</td>
            <td className="py-1 pl-3 text-left text-xs text-slate-500">{trade.rationale}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
