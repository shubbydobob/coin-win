import { instant, orNothing, price } from "../format";
import { DIRECTION } from "./labels";
import type { components } from "../api/schema";

type Trade = components["schemas"]["TradeResponse"];

export type Action = { readonly id: string; readonly kind: "fills" | "closure" };

/**
 * 진행 중인 거래. 세워 둔 계획과 아직 닫히지 않은 포지션이다.
 *
 * **상태를 화면이 판정하지 않는다.** `state` 문자열을 그대로 보고 분기한다 — `entry` 가 null
 * 인지로 유추하면 백엔드가 상태를 하나 더 늘릴 때 화면이 **조용히** 틀린다(§ 6.3).
 *
 * 상태마다 다음 동작이 정확히 하나다. 둘을 함께 내면 "체결도 청산도 할 수 있는" 것처럼 보이고,
 * 그것은 서버가 거절할 일을 화면이 권하는 배치다.
 *
 * `onAct` 가 없으면 동작 칸도 없다. 현황 화면(`/`)은 **"지금 무엇이 열려 있는가" 하나에만
 * 답하고 끝나므로**, 거기서 기록을 고칠 수 있게 하면 그 화면이 두 가지 일을 하게 된다.
 */
export function ActiveTrades({ trades, onAct }: { trades: readonly Trade[]; onAct?: (action: Action) => void }) {
  if (trades.length === 0) {
    return <p className="text-sm text-slate-500">진행 중인 거래가 없다</p>;
  }

  return (
    <table className="w-full text-right text-sm tabular-nums">
      <caption className="mb-2 text-left text-sm font-medium text-slate-700">진행 중인 거래</caption>
      <thead className="border-b border-slate-300 text-xs text-slate-500">
        <tr>
          <th scope="col" className="py-1 text-left">계획 시각</th>
          <th scope="col" className="py-1">상태</th>
          <th scope="col" className="py-1">방향</th>
          <th scope="col" className="py-1">손절가</th>
          <th scope="col" className="py-1">평단</th>
          {onAct && <th scope="col" className="py-1">다음</th>}
        </tr>
      </thead>
      <tbody>
        {trades.map((trade) => (
          <tr key={trade.id} className="border-b border-slate-100">
            <th scope="row" className="py-1 text-left font-normal">{instant(trade.plannedAt)}</th>
            <td className="py-1">{trade.state}</td>
            <td className="py-1">{DIRECTION[trade.plan.direction]}</td>
            <td className="py-1">{price(trade.plan.stopLoss)}</td>
            <td className="py-1">{orNothing(trade.entry, (entry) => price(entry.averageEntryPrice))}</td>
            {onAct && (
              <td className="py-1">
                <NextAction trade={trade} onAct={onAct} />
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function NextAction({ trade, onAct }: { trade: Trade; onAct: (action: Action) => void }) {
  const 버튼 = "rounded border border-slate-300 px-2 py-0.5 text-xs";

  switch (trade.state) {
    case "PLANNED":
      return (
        <button type="button" className={버튼} onClick={() => onAct({ id: trade.id, kind: "fills" })}>
          체결 기록
        </button>
      );
    case "OPEN":
      return (
        <button type="button" className={버튼} onClick={() => onAct({ id: trade.id, kind: "closure" })}>
          청산 기록
        </button>
      );
    default:
      return null;
  }
}
