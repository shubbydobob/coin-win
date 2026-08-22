import { money, price, quantity } from "../../format";
import type { components } from "../../api/schema";

type FillState = components["schemas"]["FillStateResponse"];

/**
 * 체결 상태 표. **각 행이 "여기까지만 체결되면 이렇게 된다" 는 별개의 시나리오다.**
 *
 * 이 표가 이 프로젝트의 본체다 — `scope.md` 의 문제 정의 1(분할 진입 시 총 리스크를 진입 전에
 * 파악할 수 없다)에 직접 대응한다.
 *
 * 수는 전부 응답에 있던 값이다. 회차 수(`n/전체`)만 화면이 만드는데, 그것은 배열의 길이지
 * 도메인 규칙의 결과가 아니다.
 */
export function FillStateTable({ states }: { states: readonly FillState[] }) {
  return (
    <table className="w-full text-right text-sm tabular-nums">
      <caption className="mb-2 text-left text-sm font-medium text-slate-700">
        체결 상태별 리스크
      </caption>
      <thead className="border-b border-slate-300 text-xs text-slate-500">
        <tr>
          <th scope="col" className="py-1 text-left">체결</th>
          <th scope="col" className="py-1">평단</th>
          <th scope="col" className="py-1">수량</th>
          <th scope="col" className="py-1">청산가</th>
          <th scope="col" className="py-1">최대손실</th>
        </tr>
      </thead>
      <tbody>
        {states.map((state) => (
          <tr key={state.filledEntries} className="border-b border-slate-100">
            <th scope="row" className="py-1 text-left font-normal">
              {state.filledEntries}/{states.length}
            </th>
            <td className="py-1">{price(state.averageEntryPrice)}</td>
            <td className="py-1">{quantity(state.quantity)}</td>
            <td className="py-1">{price(state.liquidationPrice)}</td>
            <td className="py-1">{money(state.maxLoss)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
