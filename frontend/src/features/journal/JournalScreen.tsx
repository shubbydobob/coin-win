import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { ClosedTradeTable } from "./ClosedTradeTable";
import { JournalSummaryPanel } from "./JournalSummaryPanel";
import { NO_FILTER, toQuery } from "./journalQuery";
import { TradeFilters } from "./TradeFilters";
import type { TradeQuery } from "./journalQuery";

/**
 * 매매 기록. `scope.md` 의 문제 정의 2(매매 이력이 구조화되지 않아 사후 분석이 불가능하다)에
 * 대응한다.
 *
 * **목록과 집계에 같은 조회 조건이 걸린다.** 조건 객체를 하나 만들어 두 요청에 그대로 넘긴다 —
 * 집계가 목록과 다른 모집단을 보고 있으면 화면이 거짓말을 한다(§ 6.3).
 */
export function JournalScreen() {
  const [query, setQuery] = useState<TradeQuery>(() => toQuery(NO_FILTER));

  const trades = useQuery({
    queryKey: ["trades", query],
    queryFn: () => get("/api/trades", { query }),
  });
  const summary = useQuery({
    queryKey: ["trades", "summary", query],
    queryFn: () => get("/api/trades/summary", { query }),
  });

  return (
    <div className="space-y-6">
      <TradeFilters onApply={(draft) => setQuery(toQuery(draft))} />

      {(trades.isPending || summary.isPending) && <p className="text-sm text-slate-500">불러오는 중</p>}

      <Failure error={trades.error ?? summary.error} />

      <div className="grid gap-8 md:grid-cols-2">
        {trades.data && <ClosedTradeTable trades={trades.data} />}
        {summary.data && <JournalSummaryPanel summary={summary.data} />}
      </div>
    </div>
  );
}

function Failure({ error }: { error: Error | null }) {
  if (!error) {
    return null;
  }
  return (
    <p role="alert" className="text-sm text-red-700">
      {error instanceof ApiFailure ? error.problem.detail : error.message}
    </p>
  );
}
