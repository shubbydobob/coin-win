import { money, ratio } from "../../format";
import { FillStateTable } from "./FillStateTable";
import type { components } from "../../api/schema";

type Analysis = components["schemas"]["PositionAnalysisResponse"];

/**
 * 계산 결과.
 *
 * 경고 둘은 **입력을 막지 않는다.** 백엔드가 막지 않기로 했으므로 화면도 막지 않는다.
 *
 * 경고 문구에 **기준값(1.5)을 적지 않는다.** 그 수는 `PositionPlan.MINIMUM_RISK_REWARD` 에
 * 있고, 화면이 복창하면 백엔드가 기준을 바꿨을 때 화면만 옛 숫자를 말한다. 대신 서버가 낸
 * 손익비를 옆에 그대로 둔다 — 판정은 서버가, 근거 수치는 응답이 갖는다.
 */
export function PlanResult({ analysis }: { analysis: Analysis }) {
  return (
    <section className="space-y-4" aria-label="계산 결과">
      <FillStateTable states={analysis.fillStates} />

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
        <dt className="text-slate-500">필요 증거금</dt>
        <dd className="text-right">{money(analysis.requiredMargin)}</dd>
        <dt className="text-slate-500">손익비</dt>
        <dd className="text-right">{ratio(analysis.riskRewardRatio)}</dd>
      </dl>

      <ul className="space-y-1 text-sm text-amber-700 empty:hidden">
        {analysis.weakRiskReward && <li>손익비가 기준에 못 미친다</li>}
        {analysis.marginExceedsBalance && <li>필요 증거금이 잔고를 넘는다</li>}
      </ul>
    </section>
  );
}
