import { useMutation } from "@tanstack/react-query";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import type { components } from "../../api/schema";

type Reindexed = components["schemas"]["ReindexResponse"];

/**
 * 매매 기록 재색인. 화면 구석에 둔다(§ 6.6).
 *
 * 청산 시 자동으로 색인되므로 평소에는 부를 일이 없다. **색인은 파생이고 진실의 원천은 언제나
 * 매매 기록**이므로, 언제 몇 번을 눌러도 기록이 달라지지 않는다.
 */
export function Reindex() {
  const reindex = useMutation<Reindexed, Error>({
    mutationFn: () => post("/api/ai/reindex", undefined as never),
  });

  const problem = reindex.error instanceof ApiFailure ? reindex.error.problem : null;

  return (
    <p className="text-xs text-slate-400">
      <button
        type="button"
        onClick={() => reindex.mutate()}
        disabled={reindex.isPending}
        className="underline disabled:opacity-50"
      >
        기록 재색인
      </button>
      {reindex.data && <span className="ml-2">{reindex.data.indexed}건 색인</span>}
      {problem && (
        <span role="status" className="ml-2">
          {problem.detail}
        </span>
      )}
    </p>
  );
}
