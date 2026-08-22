import { money } from "../../format";
import { ResultSummary } from "./ResultSummary";
import type { components } from "../../api/schema";

type Comparison = components["schemas"]["ComparisonResponse"];

/**
 * 기준 실행과 한 가지를 바꾼 실행.
 *
 * **나란히 놓는다. 겹쳐 그리지 않는다** — 겹치면 어느 쪽이 무엇인지 보려고 범례를 읽어야
 * 하고, 두 결과의 차이는 `pnlDifference` · `tradeDifference` 라는 수 두 개로 이미 나와
 * 있다(§ 6.4).
 */
export function ComparisonPanel({ comparison, label }: { comparison: Comparison; label: string }) {
  return (
    <section className="space-y-3" aria-label={label}>
      <h3 className="text-sm font-medium text-slate-700">{label}</h3>

      <div className="grid grid-cols-2 gap-4">
        <ResultSummary summary={comparison.baseline.summary} label="기준" />
        <ResultSummary summary={comparison.variant.summary} label="변경" />
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
        <dt className="text-slate-500">순손익 차이</dt>
        <dd className="text-right">{money(comparison.pnlDifference)}</dd>
        <dt className="text-slate-500">거래 수 차이</dt>
        <dd className="text-right">{comparison.tradeDifference}</dd>
      </dl>
    </section>
  );
}
