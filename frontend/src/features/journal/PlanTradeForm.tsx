import { useState } from "react";

import { decimal } from "../../form";
import { EntryRows } from "../../shared/EntryRows";
import { SPLIT_IN_HALF, toEntries } from "../../shared/entries";
import { DIRECTION } from "../../shared/labels";
import { Field } from "../../shared/Field";
import type { EntryRow } from "../../shared/entries";
import type { components } from "../../api/schema";

type Request = components["schemas"]["TradePlanRequest"];

interface Draft {
  readonly direction: Request["direction"];
  readonly entries: readonly EntryRow[];
  readonly stopLoss: string;
  readonly takeProfit: string;
  readonly leverage: string;
}

const EMPTY: Draft = {
  direction: "LONG",
  entries: SPLIT_IN_HALF,
  stopLoss: "",
  takeProfit: "",
  leverage: "",
};

/**
 * 진입 **전에** 남기는 계획.
 *
 * 잔고와 리스크 비율이 없다. 그 둘은 수량을 뽑기 위한 값이고 계획 기록에는 들어가지 않는다 —
 * `/plan` 이 계산에 쓰는 것과 여기 남기는 것이 다른 이유다.
 */
export function PlanTradeForm({ onSubmit, pending }: { onSubmit: (request: Request) => void; pending: boolean }) {
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const field = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft({ ...draft, [key]: value });

  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({
          direction: draft.direction,
          entries: toEntries(draft.entries),
          stopLoss: decimal(draft.stopLoss),
          takeProfit: decimal(draft.takeProfit),
          leverage: decimal(draft.leverage),
        });
      }}
    >
      <h3 className="text-sm font-medium text-slate-700">계획 저장</h3>

      <label className="block text-xs text-slate-500">
        계획 방향
        <select
          value={draft.direction}
          onChange={(event) => field("direction", event.target.value as Draft["direction"])}
          className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
        >
          {Object.entries(DIRECTION).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </label>

      <EntryRows entries={draft.entries} onChange={(entries) => field("entries", entries)} />

      <div className="grid grid-cols-3 gap-2">
        <Field label="계획 손절가" value={draft.stopLoss} onChange={(v) => field("stopLoss", v)} />
        <Field label="계획 익절가" value={draft.takeProfit} onChange={(v) => field("takeProfit", v)} />
        <Field label="계획 레버리지" value={draft.leverage} onChange={(v) => field("leverage", v)} />
      </div>

      <button type="submit" disabled={pending} className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50">
        계획 저장
      </button>
    </form>
  );
}
