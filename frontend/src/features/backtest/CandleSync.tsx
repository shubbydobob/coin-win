import { useMutation } from "@tanstack/react-query";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import type { BacktestForm } from "./backtestForm";
import type { components } from "../../api/schema";

type Synced = components["schemas"]["CandleSyncResponse"];

/**
 * 캔들 동기화.
 *
 * **이 버튼이 이 화면에 있어야 한다.** 백테스트는 저장된 캔들을 읽는데, 캔들을 넣는 길이
 * 화면에 없으면 **브라우저만으로는 백테스트를 한 번도 돌릴 수 없다**(§ 6.4).
 *
 * 구간과 종목·주기는 백테스트 설정에서 그대로 가져온다. 따로 입력받으면 두 값이 갈라지고,
 * 없는 구간의 캔들을 받아 놓고 다른 구간을 돌리게 된다.
 */
export function CandleSync({ form }: { form: BacktestForm }) {
  const sync = useMutation<Synced, Error>({
    mutationFn: () =>
      post("/api/markets/{symbol}/candles/sync", undefined as never, {
        path: { symbol: form.symbol },
        query: { interval: form.interval, from: `${form.from}T00:00:00Z`, to: `${form.to}T00:00:00Z` },
      }),
  });

  return (
    <div className="flex items-center gap-3 text-sm">
      <button
        type="button"
        onClick={() => sync.mutate()}
        disabled={sync.isPending}
        className="rounded border border-slate-300 px-3 py-1.5 disabled:opacity-50"
      >
        {sync.isPending ? "캔들 가져오는 중" : "캔들 동기화"}
      </button>

      {sync.data && <span className="text-slate-500">새로 저장 {sync.data.newlyStored}개</span>}
      {sync.error && (
        <span role="alert" className="text-red-700">
          {sync.error instanceof ApiFailure ? sync.error.problem.detail : sync.error.message}
        </span>
      )}
    </div>
  );
}
