import { instant, money, orNothing, price, quantity } from "../format";
import { DIRECTION } from "./labels";
import type { components } from "../api/schema";

type Reconciliation = components["schemas"]["PositionReconciliationResponse"];
type Match = components["schemas"]["PositionMatchResponse"];

/**
 * 기록과 거래소를 나란히 놓는다.
 *
 * **거래소 값으로 기록을 덮어쓰지 않는다.** 기록은 내가 무엇을 하려 했는지를 알고 거래소는
 * 지금 무엇이 열려 있는지를 안다 — 어느 쪽도 다른 쪽을 대체하지 못한다. 한 쪽만 보이면
 * "청산을 적지 않았다" 같은 사실이 화면에서 통째로 사라진다.
 *
 * **일치 여부를 화면이 판정하지 않는다.** `outcome` 을 그대로 보고 분기한다 — 두 수량을 여기서
 * 비교하면 "스케일 8 까지 정확히 같아야 한다" 는 규칙이 서버와 화면 두 곳에 생긴다.
 * 근거: `docs/adr/020`.
 */
export function PositionReconciliationPanel({ reconciliation }: { reconciliation: Reconciliation }) {
  return (
    <section aria-label="거래소 대조" className="space-y-2">
      <div className="flex items-baseline justify-between">
        <h2 className="text-sm font-medium text-slate-700">기록과 거래소</h2>
        <span className="text-xs text-slate-500">관측 {instant(reconciliation.observedAt)}</span>
      </div>

      {reconciliation.matches.length === 0 ? (
        <p className="text-sm text-slate-500">양쪽 모두 열려 있는 포지션이 없다</p>
      ) : (
        <ul className="space-y-2">
          {reconciliation.matches.map((match) => (
            <li key={match.direction}>
              <MatchRow match={match} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** 짝 한 줄. 어느 쪽이 비어 있는지가 곧 무슨 일이 있었는지다. */
function MatchRow({ match }: { match: Match }) {
  return (
    <div
      className={`rounded border p-3 ${match.discrepancy ? "border-amber-400 bg-amber-50" : "border-slate-200"}`}
    >
      <div className="flex items-baseline justify-between">
        <span className="text-sm font-medium">{DIRECTION[match.direction as "LONG" | "SHORT"]}</span>
        <span className={`text-xs ${match.discrepancy ? "text-amber-700" : "text-slate-500"}`}>
          {OUTCOME[match.outcome as Outcome]}
        </span>
      </div>

      <div className="mt-2 grid grid-cols-2 gap-4 text-sm tabular-nums">
        <Side title="기록">
          {match.recorded ? (
            <>
              <Row label="평단" value={price(match.recorded.averageEntryPrice)} />
              <Row label="수량" value={quantity(match.recorded.quantity)} />
              <Row label="진입" value={instant(match.recorded.openedAt)} />
            </>
          ) : (
            <p className="text-slate-500">없음</p>
          )}
        </Side>

        <Side title="거래소">
          {match.actual ? (
            <>
              <Row label="평단" value={price(match.actual.entryPrice)} />
              <Row label="수량" value={quantity(match.actual.quantity)} />
              <Row label="청산가" value={orNothing(match.actual.liquidationPrice, price)} />
              <Row label="미실현" value={money(match.actual.unrealizedPnl)} />
            </>
          ) : (
            <p className="text-slate-500">없음</p>
          )}
        </Side>
      </div>

      {match.discrepancy && <p className="mt-2 text-xs text-amber-800">{ADVICE[match.outcome as Outcome]}</p>}
    </div>
  );
}

function Side({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-xs text-slate-500">{title}</h3>
      <dl className="mt-1 space-y-0.5">{children}</dl>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-slate-500">{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

type Outcome = "AGREED" | "RECORDED_ONLY" | "EXCHANGE_ONLY" | "QUANTITY_DIFFERS";

const OUTCOME: Record<Outcome, string> = {
  AGREED: "일치",
  RECORDED_ONLY: "기록에만 있다",
  EXCHANGE_ONLY: "거래소에만 있다",
  QUANTITY_DIFFERS: "수량이 다르다",
};

/**
 * 무엇을 하라는 말까지 적는다. "불일치" 만 띄우면 사람이 매번 무슨 뜻인지 되짚어야 하고,
 * 그러면 경고가 배경이 된다.
 */
const ADVICE: Record<Outcome, string> = {
  AGREED: "",
  RECORDED_ONLY: "거래소에 이 포지션이 없다 — 청산을 기록했는가? 손절이 체결됐을 수 있다.",
  EXCHANGE_ONLY: "기록에 없는 포지션이 열려 있다 — 앱 밖에서 열었다면 계획을 남겨 둔다.",
  QUANTITY_DIFFERS: "수량이 어긋난다 — 물타기나 부분 청산이 기록되지 않았다.",
};
