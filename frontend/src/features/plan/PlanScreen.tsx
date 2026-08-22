import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { EntryRows } from "./EntryRows";
import { EMPTY_FORM, toRequest } from "./planForm";
import { PlanResult } from "./PlanResult";
import type { PlanForm } from "./planForm";
import type { components } from "../../api/schema";

type Analysis = components["schemas"]["PositionAnalysisResponse"];

/**
 * 분할 진입 계획의 체결 상태별 리스크.
 *
 * **수량 입력란이 없다.** 수량은 손절가와 리스크 예산이 결정하는 출력이다. 입력란을 두면
 * 사용자가 리스크 사이징을 건너뛴다 — Phase 7 이 계획 초안에서 총수량 칸을 뺀 것과 같은
 * 이유다(§ 6.2).
 *
 * 오류가 나도 입력을 지우지 않는다. 422 는 "고쳐서 다시" 이고, 고칠 것을 지우면 처음부터
 * 다시 쳐야 한다(§ 7).
 */
export function PlanScreen() {
  const [form, setForm] = useState<PlanForm>(EMPTY_FORM);
  const analysis = useMutation<Analysis, Error, PlanForm>({
    mutationFn: (submitted) => post("/api/position-plans/analysis", toRequest(submitted)),
  });

  const field = <K extends keyof PlanForm>(key: K, value: PlanForm[K]) =>
    setForm({ ...form, [key]: value });

  return (
    <div className="grid gap-8 md:grid-cols-2">
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          analysis.mutate(form);
        }}
      >
        <label className="block text-xs text-slate-500">
          방향
          <select
            value={form.direction}
            onChange={(event) => field("direction", event.target.value as PlanForm["direction"])}
            className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
          >
            <option value="LONG">롱</option>
            <option value="SHORT">숏</option>
          </select>
        </label>

        <EntryRows entries={form.entries} onChange={(entries) => field("entries", entries)} />

        <div className="grid grid-cols-2 gap-2">
          <NumberField label="손절가" value={form.stopLoss} onChange={(v) => field("stopLoss", v)} />
          <NumberField label="익절가" value={form.takeProfit} onChange={(v) => field("takeProfit", v)} />
          <NumberField label="레버리지" value={form.leverage} onChange={(v) => field("leverage", v)} />
          <NumberField label="잔고" value={form.accountBalance} onChange={(v) => field("accountBalance", v)} />
          <NumberField label="리스크 비율" value={form.riskPercent} onChange={(v) => field("riskPercent", v)} />
        </div>

        <button
          type="submit"
          disabled={analysis.isPending}
          className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          {analysis.isPending ? "계산 중" : "계산"}
        </button>

        {analysis.error && <Failure error={analysis.error} />}
      </form>

      {analysis.data && <PlanResult analysis={analysis.data} />}
    </div>
  );
}

interface NumberFieldProps {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
}

function NumberField({ label, value, onChange }: NumberFieldProps) {
  return (
    <label className="block text-xs text-slate-500">
      {label}
      <input
        type="number"
        step="any"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
      />
    </label>
  );
}

/**
 * 서버가 쓴 문장을 그대로 보여 준다. 화면이 자기 문장으로 바꾸면 도메인 규칙의 표현이 두 곳에
 * 생기고, 규칙이 바뀔 때 화면만 옛 문장을 말한다(§ 7).
 */
function Failure({ error }: { error: Error }) {
  return (
    <p role="alert" className="text-sm text-red-700">
      {error instanceof ApiFailure ? error.problem.detail : error.message}
    </p>
  );
}
