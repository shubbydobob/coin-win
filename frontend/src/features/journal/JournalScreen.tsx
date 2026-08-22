import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { get, post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { ActiveTrades } from "../../shared/ActiveTrades";
import { ClosedTradeTable } from "./ClosedTradeTable";
import { CloseTradeForm } from "./CloseTradeForm";
import { JournalQuery } from "./JournalQuery";
import { JournalSummaryPanel } from "../../shared/JournalSummaryPanel";
import { NO_FILTER, toQuery } from "./tradeQuery";
import { PlanTradeForm } from "./PlanTradeForm";
import { RecordFillsForm } from "./RecordFillsForm";
import { Reindex } from "./Reindex";
import { TradeFilters } from "./TradeFilters";
import type { Action } from "../../shared/ActiveTrades";
import type { TradeQuery } from "./tradeQuery";

/**
 * 매매 기록. `scope.md` 의 문제 정의 2(매매 이력이 구조화되지 않아 사후 분석이 불가능하다)에
 * 대응한다.
 *
 * **목록과 집계에 같은 조회 조건이 걸린다.** 조건 객체를 하나 만들어 두 요청에 그대로 넘긴다 —
 * 집계가 목록과 다른 모집단을 보고 있으면 화면이 거짓말을 한다(§ 6.3).
 *
 * 쓰기가 성공하면 `trades` 로 시작하는 질의를 전부 무효화한다. 진행 중인 목록·끝난 목록·집계가
 * 같은 기록에서 나오므로 **하나만 새로 고치면 화면 안에서 서로 다른 시점을 보게 된다.**
 */
export function JournalScreen() {
  const [query, setQuery] = useState<TradeQuery>(() => toQuery(NO_FILTER));
  const [action, setAction] = useState<Action | null>(null);
  const client = useQueryClient();

  const active = useQuery({ queryKey: ["trades", "active"], queryFn: () => get("/api/trades/active") });
  const trades = useQuery({ queryKey: ["trades", "closed", query], queryFn: () => get("/api/trades", { query }) });
  const summary = useQuery({ queryKey: ["trades", "summary", query], queryFn: () => get("/api/trades/summary", { query }) });

  const record = useMutation({
    mutationFn: (write: () => Promise<unknown>) => write(),
    onSuccess: async () => {
      setAction(null);
      await client.invalidateQueries({ queryKey: ["trades"] });
    },
  });

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <ActiveTrades trades={active.data ?? []} onAct={setAction} />

        <div className="grid gap-8 md:grid-cols-2">
          <PlanTradeForm
            pending={record.isPending}
            onSubmit={(request) => record.mutate(() => post("/api/trades", request))}
          />

          {action?.kind === "fills" && (
            <RecordFillsForm
              pending={record.isPending}
              onSubmit={(request) =>
                record.mutate(() => post("/api/trades/{id}/fills", request, { path: { id: action.id } }))
              }
            />
          )}
          {action?.kind === "closure" && (
            <CloseTradeForm
              pending={record.isPending}
              onSubmit={(request) =>
                record.mutate(() => post("/api/trades/{id}/closure", request, { path: { id: action.id } }))
              }
            />
          )}
        </div>

        <Failure error={record.error} />
      </section>

      <section className="space-y-4">
        <TradeFilters onApply={(draft) => setQuery(toQuery(draft))} />

        {(trades.isPending || summary.isPending) && <p className="text-sm text-slate-500">불러오는 중</p>}

        <Failure error={trades.error ?? summary.error} />

        <div className="grid gap-8 md:grid-cols-2">
          {trades.data && <ClosedTradeTable trades={trades.data} />}
          {summary.data && <JournalSummaryPanel summary={summary.data} />}
        </div>

        <JournalQuery />
        <Reindex />
      </section>
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
