import { instant, money, percent, price, quantity } from "../../format";
import { RangeMeter } from "../../shared/Meter";
import { SmallButton } from "../../shared/SmallButton";
import { SideChip } from "../../shared/SideChip";
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
  onRefresh,
  refreshing = false,
  failed = false,
}: {
  reconciliation: Reconciliation;
  outliers?: Outliers;
  onRefresh?: () => void;
  refreshing?: boolean;
  /** 마지막 갱신이 실패했다. 아래 수는 그때 값이고, 계좌 폴링은 멈춰 있다. */
  failed?: boolean;
}) {
  const 열린것 = reconciliation.matches.filter((match) => match.actual);
  const 기록에만 = reconciliation.matches.filter((match) => !match.actual && match.recorded);

  return (
    <section aria-label="내 자리" className="rounded-lg border border-line bg-surface p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 className="text-sm font-medium text-ink">내 자리</h2>
        {/*
          **언제 물어본 값인지를 이 카드가 말한다.** 예전에는 아래 「기록과 거래소」가 그 시각을
          갖고 있었는데, 정작 수를 보여 주는 것은 이 카드였다 — 값과 그 값의 시각이 스크롤
          하나만큼 떨어져 있으면 사람은 언제나 지금 값으로 읽는다.
        */}
        <div className="flex items-baseline gap-2 text-xs text-ink-2">
          <span>
            거래소에 물어본 시각 {instant(reconciliation.observedAt)}
            {/*
              갱신 중임을 버튼이 아니라 이 자리에 적는다. 버튼의 글자를 바꾸면 15초마다
              라벨이 흔들리고, 그러면 사람이 누르려던 순간에 대상이 달라진다.
            */}
            {refreshing && <span className="ml-1 text-ink-3">· 갱신 중</span>}
          </span>
          {failed && <span className="font-medium text-warn">갱신 실패 — 멈춘 값이다</span>}
          {onRefresh && <SmallButton onClick={onRefresh}>새로고침</SmallButton>}
        </div>
      </div>

      {열린것.length === 0 && 기록에만.length === 0 ? (
        <p className="mt-2 text-sm text-ink-2">거래소에도 기록에도 열려 있는 포지션이 없다.</p>
      ) : (
        <div className="mt-3 space-y-3">
          {열린것.map((match) => (
            <Open key={match.direction} match={match} outliers={outliers} />
          ))}
          {기록에만.map((match) => (
            <RecordedOnly key={match.direction} match={match} />
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * 기록에만 열려 있는 포지션. **"없다" 와 "기록에만 있다" 는 다른 사실이다** — 뒤쪽은 청산을
 * 적지 않았다는 뜻일 수 있고, 그것이 이 기능이 막으려는 상태 그 자체다.
 *
 * **이 자리가 사라질 뻔했다.** 원래는 아래 「기록과 거래소」가 이것을 말했는데 그 블록이
 * 거래소 값을 되풀이하느라 통째로 지워졌다. 되풀이가 아닌 것은 이것 하나였다 — 거래소에
 * 없는 포지션은 위 목록에 뜰 수 없으므로, 여기 없으면 **화면 어디에도 없다.**
 *
 * 거래소가 아는 것이 없으므로 청산가도 미실현도 없다. 적을 수 있는 것은 내가 적어 둔 것뿐이다.
 */
function RecordedOnly({ match }: { match: Match }) {
  const 기록 = match.recorded;
  if (!기록) {
    return null;
  }
  const 방향 = match.direction as Direction;

  return (
    <div className="rounded-lg border border-warn/50 bg-warn/5 p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-base font-medium">
          <SideChip side={방향} />{" "}
          <span className="tabular-nums">{quantity(기록.quantity)}</span>{" "}
          <span className="text-xs text-ink-3">BTC</span>
        </span>
        <span className="text-xs text-warn">거래소에 없다</span>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm tabular-nums">
        <Cell
          label="평단 (기록)"
          hint="적어 둔 체결 내역으로 다시 계산한 평균 진입가."
          value={price(기록.averageEntryPrice)}
        />
        <Cell label="진입 (기록)" hint="첫 체결 시각." value={instant(기록.openedAt)} />
      </dl>

      <p className="mt-3 border-t border-line-soft pt-2 text-xs text-warn">
        거래소에 이 포지션이 없다 — 청산을 기록했는가? 손절이 체결됐을 수 있다.
      </p>
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
          <SideChip side={방향} />{" "}
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
        <SideChip side={방향} />
        <span className="text-ink-4">·</span>
        <span className="text-ink-3">붐비는 쪽</span>
        <SideChip side="LONG">{outliers.crowdedLong}</SideChip>
        <SideChip side="SHORT">{outliers.crowdedShort}</SideChip>
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
