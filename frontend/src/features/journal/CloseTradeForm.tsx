import { useState } from "react";

import { decimal, instantAt } from "../../form";
import { EXIT_REASON } from "../../shared/labels";
import { Field } from "./Field";
import type { components } from "../../api/schema";

type Request = components["schemas"]["CloseTradeRequest"];

type ExitReason = Request["exitReason"];

/**
 * 청산 기록.
 *
 * **손익을 입력받지 않는다.** 청산가·시각·이유·수수료·펀딩비만 받고 손익은 도메인이 체결
 * 내역에서 계산한다(Phase 5). 손익 입력란을 두면 체결 내역과 손익이 어긋나 있어도 알 방법이
 * 없어진다 — 거래소 화면의 숫자를 그대로 받는 것이 바로 그 상태다.
 *
 * 받는 것은 **재현할 수 없는 것**뿐이다. 수수료와 펀딩비가 그렇다.
 */
export function CloseTradeForm({ onSubmit, pending }: { onSubmit: (request: Request) => void; pending: boolean }) {
  const [exitPrice, setExitPrice] = useState("");
  const [exitAt, setExitAt] = useState("");
  const [exitReason, setExitReason] = useState<ExitReason>("PLANNED_TARGET");
  const [fees, setFees] = useState("");
  const [funding, setFunding] = useState("");

  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({
          exitPrice: decimal(exitPrice),
          exitAt: instantAt(exitAt),
          exitReason,
          fees: decimal(fees),
          funding: decimal(funding),
        });
      }}
    >
      <h3 className="text-sm font-medium text-slate-700">청산 기록</h3>

      <div className="grid grid-cols-2 gap-2">
        <Field label="청산가" value={exitPrice} onChange={setExitPrice} />
        <Field label="청산 시각 (UTC)" type="datetime-local" value={exitAt} onChange={setExitAt} />
        <Field label="수수료" value={fees} onChange={setFees} />
        <Field label="펀딩비" value={funding} onChange={setFunding} />
      </div>

      <label className="block text-xs text-slate-500">
        청산 이유
        <select
          value={exitReason}
          onChange={(event) => setExitReason(event.target.value as ExitReason)}
          className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
        >
          {Object.entries(EXIT_REASON).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </label>

      <button type="submit" disabled={pending} className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50">
        청산 저장
      </button>
    </form>
  );
}
