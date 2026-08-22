import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { decimal } from "../../form";
import { money, percent } from "../../format";
import { EquityChart } from "../../shared/EquityChart";
import { Field } from "../../shared/Field";
import { MonteCarloResult } from "./MonteCarloResult";
import { STARTING_FORM, toSpec } from "./projectionForm";
import type { ProjectionForm } from "./projectionForm";
import type { components } from "../../api/schema";

type Curve = components["schemas"]["EquityCurveResponse"];

type Distribution = components["schemas"]["MonteCarloResponse"];

/**
 * 복리 · 몬테카를로. 같은 `ProjectionSpecRequest` 를 두 엔드포인트가 공유한다(§ 6.5).
 *
 * 표본 경로 하나와 N 회 분포를 함께 볼 수 있어야 한다 — 곡선 하나만 보면 그것이 **가능한 경로
 * 중 하나**라는 사실이 사라지고, 분포만 보면 그 안에서 자산이 어떻게 움직이는지가 사라진다.
 */
export function ProjectionScreen() {
  const [form, setForm] = useState<ProjectionForm>(STARTING_FORM);
  const field = <K extends keyof ProjectionForm>(key: K, value: ProjectionForm[K]) =>
    setForm({ ...form, [key]: value });

  const curve = useMutation<Curve, Error>({
    mutationFn: () => post("/api/projections/equity-curve", { spec: toSpec(form), seed: decimal(form.seed) }),
  });
  const distribution = useMutation<Distribution, Error>({
    mutationFn: () =>
      post("/api/projections/monte-carlo", {
        spec: toSpec(form),
        runs: decimal(form.runs),
        seed: decimal(form.seed),
      }),
  });

  const pending = curve.isPending || distribution.isPending;
  const error = curve.error ?? distribution.error;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-4 gap-2">
        <Field label="시작 자산" value={form.initialCapital} onChange={(v) => field("initialCapital", v)} />
        <Field label="승률" value={form.winRate} onChange={(v) => field("winRate", v)} />
        <Field label="손익비" value={form.riskRewardRatio} onChange={(v) => field("riskRewardRatio", v)} />
        <Field label="거래당 리스크" value={form.riskPerTrade} onChange={(v) => field("riskPerTrade", v)} />
        <Field label="주당 거래 수" value={form.tradesPerWeek} onChange={(v) => field("tradesPerWeek", v)} />
        <Field label="기간 (주)" value={form.weeks} onChange={(v) => field("weeks", v)} />
        <Field label="시행 횟수" value={form.runs} onChange={(v) => field("runs", v)} />
        <Field label="시드" value={form.seed} onChange={(v) => field("seed", v)} />
      </div>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => curve.mutate()}
          disabled={pending}
          className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          표본 경로
        </button>
        <button
          type="button"
          onClick={() => distribution.mutate()}
          disabled={pending}
          className="rounded border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-50"
        >
          {distribution.isPending ? "돌리는 중" : "분포"}
        </button>
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error instanceof ApiFailure ? error.problem.detail : error.message}
        </p>
      )}

      {curve.data && (
        <section className="space-y-2" aria-label="표본 경로">
          <EquityChart equity={curve.data.equity} label="시드 하나가 만든 자산 곡선" />
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
            <dt className="text-slate-500">최종 자산</dt>
            <dd className="text-right">{money(curve.data.finalEquity)}</dd>
            <dt className="text-slate-500">최대낙폭</dt>
            <dd className="text-right">{percent(curve.data.maxDrawdown)}</dd>
            <dt className="text-slate-500">거래 수</dt>
            <dd className="text-right">{curve.data.trades}</dd>
          </dl>
        </section>
      )}

      {distribution.data && <MonteCarloResult distribution={distribution.data} />}
    </div>
  );
}
