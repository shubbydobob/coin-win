import { useState } from "react";

import { DIRECTION, EXIT_REASON } from "../../shared/labels";
import { NO_FILTER } from "./tradeQuery";
import type { FilterDraft } from "./tradeQuery";

interface Props {
  readonly onApply: (draft: FilterDraft) => void;
}

/**
 * 조회 조건. **UI 는 하나이고 목록과 집계 두 요청에 같은 값이 간다**(§ 6.3).
 *
 * 타이핑마다 보내지 않고 "조회" 를 누를 때 보낸다. 날짜를 치는 도중의 `2026-08-0` 같은 값으로
 * 요청이 나가는 것을 막기도 하지만, 더 중요한 이유는 **두 요청이 같은 순간의 조건으로 나가야
 * 한다**는 것이다.
 */
export function TradeFilters({ onApply }: Props) {
  const [draft, setDraft] = useState<FilterDraft>(NO_FILTER);

  const field = <K extends keyof FilterDraft>(key: K, value: FilterDraft[K]) =>
    setDraft({ ...draft, [key]: value });

  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        onApply(draft);
      }}
    >
      <label className="text-xs text-slate-500">
        청산 시작일
        <input
          type="date"
          value={draft.closedFrom}
          onChange={(event) => field("closedFrom", event.target.value)}
          className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm"
        />
      </label>
      <label className="text-xs text-slate-500">
        청산 종료일
        <input
          type="date"
          value={draft.closedTo}
          onChange={(event) => field("closedTo", event.target.value)}
          className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm"
        />
      </label>
      <label className="text-xs text-slate-500">
        방향
        <select
          value={draft.direction}
          onChange={(event) => field("direction", event.target.value as FilterDraft["direction"])}
          className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm"
        >
          <option value="">전체</option>
          {Object.entries(DIRECTION).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </label>
      <label className="text-xs text-slate-500">
        청산 이유
        <select
          value={draft.exitReason}
          onChange={(event) => field("exitReason", event.target.value as FilterDraft["exitReason"])}
          className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm"
        >
          <option value="">전체</option>
          {Object.entries(EXIT_REASON).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </label>
      <label className="text-xs text-slate-500">
        계획 준수
        <select
          value={draft.followedPlan}
          onChange={(event) => field("followedPlan", event.target.value as FilterDraft["followedPlan"])}
          className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm"
        >
          <option value="">전체</option>
          <option value="true">지킨 거래</option>
          <option value="false">어긴 거래</option>
        </select>
      </label>

      <button type="submit" className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white">
        조회
      </button>
    </form>
  );
}
