import { money, percent, price, quantity } from "../../format";
import { RangeMeter } from "../../shared/Meter";
import { DIRECTION } from "../../shared/labels";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Reconciliation = components["schemas"]["PositionReconciliationResponse"];
type Match = components["schemas"]["PositionMatchResponse"];
type Outliers = components["schemas"]["MetricOutliersResponse"];
type Direction = "LONG" | "SHORT";

/**
 * 내 자리. **내가 무엇을 들고 있고, 그것이 지금 시장의 어디에 서 있나.**
 *
 * 이 카드가 생긴 이유는 화면이 세로로 길어서가 아니다. **내 포지션과 시장 지표가 같은 화면에
 * 있는데도 대조가 사람 머릿속에서만 일어나고 있었다** — 청산가 84,273 과 현재가 77,560 을
 * 눈으로 오가며 "8% 쯤 남았나" 를 매번 계산해야 했고, 내가 숏이라는 사실과 붐비는 쪽이
 * 롱이라는 사실은 스크롤 두 번 떨어져 있었다.
 *
 * **여기 있는 수는 전부 서버가 낸 것이다.** 명목과 청산 거리는 `ExchangePosition` 의 계산이고,
 * 붐비는 쪽은 `MarketOutliers` 의 셈이다 — `docs/adr/020`.
 *
 * **판정하지 않는다.** 내 방향과 붐비는 쪽을 나란히 놓지만 "반대라서 위험하다" 고 말하지
 * 않는다. 그 문장은 예측이고 이 저장소는 그 예측에 증거가 없다(`docs/adr/021`).
 * 펀딩도 마찬가지다 — "내나 받나" 는 정의이고 "그러니 버텨라" 는 아니다.
 */
export function MyPositionCard({
  reconciliation,
  outliers,
}: {
  reconciliation: Reconciliation;
  outliers?: Outliers;
}) {
  const 열린것 = reconciliation.matches.filter((match) => match.actual);

  return (
    <section aria-label="내 자리" className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">내 자리</h2>
        <span className="text-xs text-ink-3">
          거래소가 지금 말하는 내 포지션을 시장 옆에 놓는다
        </span>
      </div>

      {열린것.length === 0 ? (
        <Empty reconciliation={reconciliation} />
      ) : (
        <div className="mt-3 space-y-3">
          {열린것.map((match) => (
            <Open key={match.direction} match={match} outliers={outliers} />
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * 거래소에 열린 것이 없을 때. **"없다" 와 "기록에만 있다" 는 다른 사실이다** — 뒤쪽은
 * 청산을 적지 않았다는 뜻일 수 있고, 그것이 이 기능이 막으려는 상태 그 자체다.
 */
function Empty({ reconciliation }: { reconciliation: Reconciliation }) {
  const 기록에만 = reconciliation.matches.filter((match) => match.recorded);

  return (
    <div className="mt-2 text-sm text-ink-2">
      <p>거래소에 열려 있는 포지션이 없다.</p>
      {기록에만.length > 0 && (
        <p className="mt-1 text-warn">
          그런데 기록에는 {기록에만.length}건이 열려 있다 — 청산을 적었는가?
        </p>
      )}
    </div>
  );
}

/** 열려 있는 포지션 하나. 위에서 아래로 **무엇을 · 얼마나 · 어디까지 · 어느 편에** 순서다. */
function Open({ match, outliers }: { match: Match; outliers?: Outliers }) {
  const 포지션 = match.actual;
  if (!포지션) {
    return null;
  }
  const 방향 = match.direction as Direction;

  return (
    <div
      className={`rounded-lg border p-3 ${match.discrepancy ? "border-warn/50 bg-warn/5" : "border-line"}`}
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-base font-medium">
          <span className={방향 === "LONG" ? "text-up" : "text-down"}>{DIRECTION[방향]}</span>{" "}
          <span className="tabular-nums">{quantity(포지션.quantity)}</span>{" "}
          <span className="text-xs text-ink-3">BTC</span>
        </span>
        {match.discrepancy && (
          <span className="text-xs text-warn">{DISCREPANCY[match.outcome] ?? "기록과 다르다"}</span>
        )}
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm tabular-nums sm:grid-cols-4">
        <Cell label="평단" hint="거래소가 계산한 평균 진입가." value={price(포지션.entryPrice)} />
        <Cell
          label="지금 (표시가)"
          hint="청산이 트리거되고 미실현이 계산되는 값. 마지막 체결가와 다를 수 있다."
          value={price(포지션.markPrice)}
        />
        <Cell
          label="미실현"
          hint="지금 닫으면 확정될 손익. 아직 확정된 것이 아니다."
          value={money(포지션.unrealizedPnl)}
          tone={포지션.unrealizedPnl < 0 ? "down" : "up"}
        />
        <Cell
          label="명목"
          hint="표시가 × 수량. 수량이 아니라 이것이 위험의 크기다."
          value={money(포지션.notional)}
        />
      </dl>

      <Liquidation 포지션={포지션} />
      <Crowd 방향={방향} outliers={outliers} />
    </div>
  );
}

/**
 * 평단에서 청산가까지의 길 위에 지금이 어디인가.
 *
 * **눈금의 양 끝을 평단과 청산가로 잡는다.** "청산까지 20% 면 절반" 같은 상한을 정하면 그
 * 상한은 우리가 지어낸 수이고 화면이 그것을 기준처럼 보이게 만든다. 두 끝은 둘 다 거래소가
 * 말한 실제 가격이라 지어낼 것이 없다.
 */
function Liquidation({ 포지션 }: { 포지션: NonNullable<Match["actual"]> }) {
  if (포지션.liquidationPrice === null || 포지션.liquidationDistancePercent === null) {
    return (
      <p className="mt-3 text-xs text-ink-3">
        거래소가 청산 지점을 말하지 않는다 — 0 으로 채우지 않고 비워 둔다.
      </p>
    );
  }

  const 평단 = 포지션.entryPrice;
  const 청산 = 포지션.liquidationPrice;

  return (
    <div className="mt-3">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="text-ink-2">
          청산 <span className="tabular-nums text-ink">{price(청산)}</span>
        </span>
        <span className="tabular-nums font-medium text-warn">
          {percent(포지션.liquidationDistancePercent)} 남았다
        </span>
      </div>
      <div className="mt-1.5">
        <RangeMeter
          low={Math.min(평단, 청산)}
          high={Math.max(평단, 청산)}
          current={포지션.markPrice}
          label={`평단 ${price(평단)} 에서 청산 ${price(청산)} 까지 중 지금은 ${price(포지션.markPrice)}`}
        />
        <div className="mt-0.5 flex justify-between text-[10px] text-ink-4">
          <span>{평단 < 청산 ? "평단" : "청산"}</span>
          <span>{평단 < 청산 ? "청산" : "평단"}</span>
        </div>
      </div>
    </div>
  );
}

/**
 * 내 편과 붐비는 편.
 *
 * **화면이 고르는 것은 문장뿐이고 수는 전부 서버가 냈다.** 펀딩을 내는지 받는지는 부호 둘을
 * 맞대는 것이라 계산이 아니다 — 변화율의 ▲▼ 를 고르는 것과 같은 종류다.
 */
function Crowd({ 방향, outliers }: { 방향: Direction; outliers?: Outliers }) {
  if (!outliers) {
    return null;
  }
  const 펀딩 = outliers.metrics.find((metric) => metric.metric === "FUNDING_RATE");

  return (
    <div className="mt-3 space-y-1 border-t border-line-soft pt-2 text-xs">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="text-ink-3">내 방향</span>
        <span className={`font-medium ${방향 === "LONG" ? "text-up" : "text-down"}`}>
          {DIRECTION[방향]}
        </span>
        <span className="text-ink-4">·</span>
        <span className="text-ink-3">붐비는 쪽</span>
        <span className="tabular-nums font-medium text-up">롱 {outliers.crowdedLong}</span>
        <span className="text-ink-4">·</span>
        <span className="tabular-nums font-medium text-down">숏 {outliers.crowdedShort}</span>
      </div>
      {펀딩 && (
        <p className="text-ink-2">
          펀딩 <span className="tabular-nums">{percent(펀딩.current)}</span> —{" "}
          {펀딩표기(방향, 펀딩.side)}
        </p>
      )}
    </div>
  );
}

/**
 * 펀딩을 내는가 받는가. **8시간마다 실제로 오가는 돈이고, 그 방향은 정의로 정해진다** —
 * 펀딩비가 양수면 롱이 숏에게 내므로 숏인 나는 받는다.
 *
 * 얼마를 받는지는 적지 않는다. 명목 × 펀딩비를 화면에서 곱하면 `docs/adr/020` 이 금지한
 * 계산이 되고, 다음 정산까지 남은 시간을 우리가 모르므로 그 수는 어차피 반쪽이다.
 */
function 펀딩표기(방향: Direction, side: string): string {
  if (side !== "LONG" && side !== "SHORT") {
    return "양쪽 다 내지 않는다";
  }
  return side === 방향
    ? "내 쪽이 내고 있다. 8시간마다 나간다"
    : "반대쪽이 내고 있다. 8시간마다 들어온다";
}

function Cell({
  label,
  hint,
  value,
  tone = "plain",
}: {
  label: string;
  hint: string;
  value: string;
  tone?: "plain" | "up" | "down";
}) {
  return (
    <div>
      <Term label={label} hint={hint} />
      <dd
        className={`mt-0.5 text-base ${
          tone === "up" ? "text-up" : tone === "down" ? "text-down" : "text-ink"
        }`}
      >
        {value}
      </dd>
    </div>
  );
}

/** 기록과 어긋난 이유. 자세한 좌우 대조는 아래 「기록과 거래소」가 이어서 말한다. */
const DISCREPANCY: Record<string, string> = {
  RECORDED_ONLY: "거래소에 없다",
  EXCHANGE_ONLY: "기록에 없다",
  QUANTITY_DIFFERS: "기록과 수량이 다르다",
};
