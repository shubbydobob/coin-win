import { duration, money, percent } from "../format";
import { Term } from "./Term";
import type { components } from "../api/schema";

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

      <dl className="grid grid-cols-[1fr_auto] items-start gap-x-4 gap-y-2 text-sm tabular-nums">
        <Term label="전체 거래" hint="청산까지 끝난 거래만 센다. 진행 중인 것은 빠진다." />
        <dd className="text-right">{summary.totalTrades}</dd>
        <Term label="실현 손익" hint="끝난 거래 손익의 합. 수수료와 펀딩비를 뺀 뒤의 수다." />
        <dd className="text-right">{money(summary.totalRealizedPnl)}</dd>
        <Term label="계획 준수율" hint="계획대로 닫은 거래 ÷ 전체 거래. 이 도구가 실제로 쓰이고 있는지를 본다." />
        <dd className="text-right">{percent(summary.planAdherence)}</dd>
      </dl>

      <div className="grid grid-cols-2 gap-4">
        <TallyBlock
          title="계획을 지킨 거래"
          hint="계획 손절·계획 익절에서 닫힌 거래. 손실이어도 지킨 것이다."
          tally={summary.followed}
        />
        <TallyBlock
          title="계획을 어긴 거래"
          hint="조기 청산·손절 지나 보유·청산당함. 이익이어도 어긴 것이다."
          tally={summary.broken}
        />
      </div>

      <dl className="grid grid-cols-[1fr_auto] items-start gap-x-4 gap-y-2 text-sm tabular-nums">
        <Term label="거래 간격 (평균)" hint="직전 거래를 닫고 다음을 열기까지 걸린 시간의 평균." />
        <dd className="text-right">{duration(summary.intervals.average)}</dd>
        <Term
          label="거래 간격 (최단)"
          hint="가장 짧았던 간격. 손실 직후 곧바로 다시 들어가는 습관은 여기서만 보인다."
        />
        <dd className="text-right">{duration(summary.intervals.shortest)}</dd>
        {summary.intervals.overlaps > 0 && (
          <>
            <dt className="text-amber-700">
              겹쳐서 셀 수 없던 쌍
              <span className="mt-0.5 block text-xs leading-snug text-amber-600">
                앞 거래가 닫히기 전에 뒤 거래가 열린 쌍. 그 사이에는 간격이 없어 평균에서 뺐다.
              </span>
            </dt>
            <dd className="text-right text-amber-700">{summary.intervals.overlaps}</dd>
          </>
        )}
      </dl>
    </section>
  );
}

function TallyBlock({ title, hint, tally }: { title: string; hint: string; tally: Tally }) {
  return (
    <div className="rounded border border-slate-200 p-3 text-sm tabular-nums">
      <h3 className="text-xs text-slate-500">{title}</h3>
      <p className="mt-0.5 text-xs leading-snug text-slate-400">{hint}</p>
      <dl className="mt-2 grid grid-cols-[1fr_auto] items-start gap-x-2 gap-y-2">
        <Term label="건수" hint="이 묶음에 든 거래 수." />
        <dd className="text-right">{tally.trades}</dd>
        <Term label="승 / 패" hint="0 원으로 끝난 거래는 승리가 아니다 — 패로 센다." />
        <dd className="text-right">{tally.wins} / {tally.losses}</dd>
        <Term label="승률" hint="이긴 거래 ÷ 이 묶음의 거래 수." />
        <dd className="text-right">{percent(tally.winRate)}</dd>
        <Term label="손익" hint="이 묶음의 실현 손익 합." />
        <dd className="text-right">{money(tally.realizedPnl)}</dd>
      </dl>
    </div>
  );
}
