import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { BacktestSettings } from "./BacktestSettings";
import { BacktestTrades } from "./BacktestTrades";
import { CandleSync } from "./CandleSync";
import { ComparisonPanel } from "./ComparisonPanel";
import { EquityChart } from "../../shared/EquityChart";
import { ResultSummary } from "./ResultSummary";
import { STARTING_FORM, toRequest } from "./backtestForm";
import type { BacktestForm } from "./backtestForm";
import type { components } from "../../api/schema";

type Result = components["schemas"]["BacktestResultResponse"];

type Comparison = components["schemas"]["ComparisonResponse"];

/**
 * 백테스트. 같은 요청 본문을 세 엔드포인트가 공유한다(§ 6.4).
 *
 * 그래서 설정은 하나이고 버튼이 셋이다. 각각 따로 설정을 두면 "무엇과 무엇을 비교한
 * 것인가" 가 화면에서 사라진다.
 */
export function BacktestScreen() {
  const [form, setForm] = useState<BacktestForm>(STARTING_FORM);
  const [result, setResult] = useState<Result | null>(null);
  const [comparison, setComparison] = useState<{ label: string; value: Comparison } | null>(null);

  const run = useMutation<Result, Error, BacktestForm>({
    mutationFn: (submitted) => post("/api/backtests", toRequest(submitted)),
    onSuccess: (value) => {
      setResult(value);
      setComparison(null);
    },
  });

  const compare = useMutation<Comparison, Error, { path: "indicator-filter-comparison" | "cost-comparison"; label: string }>({
    mutationFn: ({ path }) =>
      path === "indicator-filter-comparison"
        ? post("/api/backtests/indicator-filter-comparison", toRequest(form))
        : post("/api/backtests/cost-comparison", toRequest(form)),
    onSuccess: (value, { label }) => {
      setComparison({ label, value });
      setResult(null);
    },
  });

  const pending = run.isPending || compare.isPending;
  const error = run.error ?? compare.error;

  return (
    <div className="space-y-6">
      <BacktestSettings form={form} onChange={setForm} />

      <CandleSync form={form} />

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => run.mutate(form)}
          disabled={pending}
          className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          {run.isPending ? "돌리는 중" : "실행"}
        </button>
        <button
          type="button"
          onClick={() => compare.mutate({ path: "indicator-filter-comparison", label: "지표 필터 비교" })}
          disabled={pending}
          className="rounded border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-50"
        >
          지표 필터 비교
        </button>
        <button
          type="button"
          onClick={() => compare.mutate({ path: "cost-comparison", label: "비용 비교" })}
          disabled={pending}
          className="rounded border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-50"
        >
          비용 비교
        </button>
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error instanceof ApiFailure ? error.problem.detail : error.message}
        </p>
      )}

      {result && (
        <section className="space-y-6" aria-label="백테스트 결과">
          <div className="grid gap-8 md:grid-cols-2">
            <ResultSummary summary={result.summary} label="성적" />
            <EquityChart equity={result.equityCurve} label="자산 곡선" />
          </div>
          <BacktestTrades trades={result.trades} />
        </section>
      )}

      {comparison && <ComparisonPanel comparison={comparison.value} label={comparison.label} />}
    </div>
  );
}
