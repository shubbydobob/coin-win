import { duration, money, percent } from "../../format";
import type { components } from "../../api/schema";

type Summary = components["schemas"]["JournalSummaryResponse"];

type Tally = components["schemas"]["TallyResponse"];

/**
 * 집계.
 *
 * **강조하는 것은 승률이 아니라 `costOfDeviation` 이다**(§ 6.3). 계획을 어겨서 얻은 것의 합이고
 * 음수면 어기는 편이 손해였다는 뜻이다. **이 프로젝트가 기록을 남기는 이유가 이 수치다.**
 *
 * 지킨 거래와 어긴 거래를 나란히 놓는다. 하나로 합치면 "전체 승률 40%" 같은 수가 나오는데,
 * 그것은 계획이 나쁜 것인지 계획을 안 지킨 것인지를 말해 주지 않는다.
 */
export function JournalSummaryPanel({ summary }: { summary: Summary }) {
  return (
    <section className="space-y-4" aria-label="집계">
      <div className="rounded border border-slate-300 p-3">
        <h3 className="text-xs text-slate-500">계획을 어겨서 얻은 것</h3>
        <p className="mt-1 text-2xl tabular-nums">{money(summary.costOfDeviation)}</p>
        <p className="mt-1 text-xs text-slate-500">
          음수면 어기는 편이 손해였다. 어긴 거래를 손절가에서 닫았다면{" "}
          {money(summary.lossIfEveryStopHonored)} 였다.
        </p>
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
        <dt className="text-slate-500">전체 거래</dt>
        <dd className="text-right">{summary.totalTrades}</dd>
        <dt className="text-slate-500">실현 손익</dt>
        <dd className="text-right">{money(summary.totalRealizedPnl)}</dd>
        <dt className="text-slate-500">계획 준수율</dt>
        <dd className="text-right">{percent(summary.planAdherence)}</dd>
      </dl>

      <div className="grid grid-cols-2 gap-4">
        <TallyBlock title="계획을 지킨 거래" tally={summary.followed} />
        <TallyBlock title="계획을 어긴 거래" tally={summary.broken} />
      </div>

      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm tabular-nums">
        <dt className="text-slate-500">거래 간격 (평균)</dt>
        <dd className="text-right">{duration(summary.intervals.average)}</dd>
        <dt className="text-slate-500">거래 간격 (최단)</dt>
        <dd className="text-right">{duration(summary.intervals.shortest)}</dd>
        {summary.intervals.overlaps > 0 && (
          <>
            <dt className="text-amber-700">겹쳐서 셀 수 없던 쌍</dt>
            <dd className="text-right text-amber-700">{summary.intervals.overlaps}</dd>
          </>
        )}
      </dl>
    </section>
  );
}

function TallyBlock({ title, tally }: { title: string; tally: Tally }) {
  return (
    <div className="rounded border border-slate-200 p-3 text-sm tabular-nums">
      <h3 className="text-xs text-slate-500">{title}</h3>
      <dl className="mt-1 grid grid-cols-2 gap-x-2">
        <dt className="text-slate-500">건수</dt>
        <dd className="text-right">{tally.trades}</dd>
        <dt className="text-slate-500">승 / 패</dt>
        <dd className="text-right">{tally.wins} / {tally.losses}</dd>
        <dt className="text-slate-500">승률</dt>
        <dd className="text-right">{percent(tally.winRate)}</dd>
        <dt className="text-slate-500">손익</dt>
        <dd className="text-right">{money(tally.realizedPnl)}</dd>
      </dl>
    </div>
  );
}
