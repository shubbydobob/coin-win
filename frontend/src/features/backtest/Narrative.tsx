import { useMutation } from "@tanstack/react-query";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { toRequest } from "./backtestForm";
import type { BacktestForm } from "./backtestForm";
import type { components } from "../../api/schema";

type Narration = components["schemas"]["BacktestNarrativeResponse"];

/**
 * 백테스트 결과 요약(AI).
 *
 * **엔드포인트가 `/api/ai` 가 아니라 `/api/backtests/narrative` 인 것에 이유가 있다** —
 * `ai` 가 백테스트를 직접 돌리면 두 모듈이 서로를 참조하게 되고 ArchUnit 규칙 3 이 빌드를
 * 세운다. 사실을 만들어 넘기는 쪽이 백테스트다.
 *
 * `facts` 를 함께 보여 준다. 요약에 나오는 수는 전부 그 안에 있는 값이고, 없는 수가 들어간
 * 요약은 응답 자체가 만들어지지 않는다 — **대조할 수 있어야 근거다.**
 */
export function Narrative({ form }: { form: BacktestForm }) {
  const narrate = useMutation<Narration, Error>({
    mutationFn: () => post("/api/backtests/narrative", toRequest(form)),
  });

  const problem = narrate.error instanceof ApiFailure ? narrate.error.problem : null;

  return (
    <section className="space-y-2 rounded border border-slate-200 p-3" aria-label="결과 요약">
      <button
        type="button"
        onClick={() => narrate.mutate()}
        disabled={narrate.isPending}
        className="rounded border border-slate-300 px-2 py-1 text-sm disabled:opacity-50"
      >
        {narrate.isPending ? "요약하는 중" : "요약 (AI 보조)"}
      </button>

      {problem && (
        <p role="status" className="text-sm text-slate-600">
          {problem.detail}
        </p>
      )}

      {narrate.data && (
        <div className="space-y-2 text-sm">
          <p>{narrate.data.narrative}</p>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs text-slate-500 tabular-nums">
            {Object.entries(narrate.data.facts).map(([name, value]) => (
              <div key={name} className="contents">
                <dt>{name}</dt>
                <dd className="text-right">{String(value)}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </section>
  );
}
