import { NOTHING, percent, price, quantity, ratio } from "../../format";
import { PositionMeter } from "../../shared/Meter";
import { Sparkline } from "../../shared/Sparkline";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Outliers = components["schemas"]["MetricOutliersResponse"];

type Outlier = components["schemas"]["MetricOutlierResponse"];

/**
 * 세 가지를 한 줄에 놓는다 — **지금 얼마인가 · 평소와 견줘 어디쯤인가 · 어느 쪽으로 가는가.**
 *
 * 첫 판에는 가운데 하나뿐이었고, 그래서 **정지 화면**이었다. 계기가 된 사건(숏 스퀴즈)은
 * 미결제약정이 *급감*할 때 벌어지는데 위치만 보면 "높음" 에 머물러 있을 수도 있다.
 *
 * **가격을 맨 위에 기준선으로 놓는다.** 미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다 —
 * 포지션이 줄면서 가격이 올랐다면 청산이고, 포지션이 줄면서 가격도 내렸다면 그냥 손을 턴 것이다.
 *
 * **무엇을 하라고 말하지 않는다.** 상황 문장도 일어난 일까지만 적는다.
 */
export function OutlierPanel({ outliers }: { outliers: Outliers }) {
  return (
    <section aria-label="이상치" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">평소와 다른가</h2>
        {outliers.hasOutlier && <span className="text-xs text-warn">평소와 다른 지표가 있다</span>}
      </div>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        최근 표본에서 지금 값이 어디인가(<b>양 끝 5%</b> 면 표시), 그리고 정해진 창에서 어느
        쪽으로 갔나. 무엇을 하라는 뜻은 아니다 — 지금이 평소와 다르다는 사실뿐이다.
      </p>

      {/*
        상황 문장. **비어 있는 것이 정상이다** — 늘 떠 있으면 배경이 되고, 배경이 된 경고는
        아무것도 경고하지 않는다. 조건은 서버의 도메인 상수가 갖는다.
      */}
      {outliers.situations.length > 0 && (
        <ul className="mt-3 space-y-1 rounded border border-warn/40 bg-warn/10 p-2">
          {outliers.situations.map((situation) => (
            <li key={situation} className="text-xs leading-snug text-warn">
              {situation}
            </li>
          ))}
        </ul>
      )}

      {/* 기준선. 지표들과 같은 창의 가격이다. */}
      <div className="mt-3 rounded bg-surface-2 p-2">
        <Row metric={outliers.price} baseline />
      </div>

      <dl className="mt-2 divide-y divide-line-soft">
        {outliers.metrics.map((metric) => (
          <div key={metric.metric} className="py-2">
            <Row metric={metric} />
          </div>
        ))}
      </dl>
    </section>
  );
}

function Row({ metric, baseline = false }: { metric: Outlier; baseline?: boolean }) {
  const 위치 =
    metric.topPercent === null || metric.topPercent === undefined
      ? null
      : 1 - metric.topPercent / 100;
  const 변화 = metric.change ?? null;
  const 오름 = 변화 === null ? null : 변화 > 0;

  return (
    <div className="text-sm tabular-nums">
      <div className="flex items-baseline justify-between gap-4">
        <Term label={LABEL[metric.metric] ?? metric.metric} hint={HINT[metric.metric] ?? ""} />
        <dd className="shrink-0 text-right">
          <span className="text-ink">{현재값(metric)}</span>
          <span
            className={`mt-0.5 block text-xs font-normal ${
              오름 === null ? "text-ink-4" : 오름 ? "text-up" : "text-down"
            }`}
          >
            {변화 === null ? "변화를 말할 수 없다" : `${오름 ? "▲" : "▼"} ${변화표기(metric)}`}
          </span>
        </dd>
      </div>

      <div className="mt-1.5 grid grid-cols-[1fr_auto] items-center gap-3">
        <Sparkline
          samples={metric.samples}
          rising={오름}
          label={`${LABEL[metric.metric] ?? metric.metric} 최근 ${metric.sampleCount}개 추세`}
        />
        <span className="text-[10px] text-ink-4">최근 {metric.sampleCount}개</span>
      </div>

      {/* 위치는 기준선(가격)에는 뜻이 없다 — 가격이 최근 범위 어디인가는 24시간 막대가 답한다. */}
      {!baseline && 위치 !== null && (
        <div className="mt-1.5">
          <PositionMeter
            ratio={위치}
            outlier={metric.outlier}
            label={`${LABEL[metric.metric] ?? metric.metric}: 상위 ${percent(metric.topPercent as number)}`}
          />
          <div className="mt-0.5 flex justify-between text-[10px] text-ink-4">
            <span>낮음</span>
            <span className={metric.outlier ? "font-medium text-warn" : "text-ink-3"}>
              상위 {percent(metric.topPercent as number)}
            </span>
            <span>높음</span>
          </div>
        </div>
      )}
      {!baseline && 위치 === null && (
        <p className="mt-1 text-[10px] text-ink-4">
          표본 {metric.sampleCount}개 — 위치를 아직 말할 수 없다
        </p>
      )}
    </div>
  );
}

/**
 * 지표마다 단위가 다르다. 펀딩비는 %, 미결제약정은 BTC, 비율은 무차원, 가격은 USDT 다.
 *
 * 서버가 전부 한 필드(`current`)로 내므로 단위를 아는 것은 화면뿐이다. 이것은 계산이 아니라
 * **표시 형식의 선택**이고, 자릿수는 여전히 `format/` 이 정한다.
 */
function 현재값(metric: Outlier): string {
  switch (metric.metric) {
    case "FUNDING_RATE":
      return percent(metric.current);
    case "OPEN_INTEREST":
      return quantity(metric.current);
    case "PRICE":
      return price(metric.current);
    case "LONG_SHORT_RATIO":
    case "TAKER_RATIO":
    case "TOP_POSITION_RATIO":
      return ratio(metric.current);
    default:
      return NOTHING;
  }
}

/**
 * **펀딩비만 변화가 차이(%p)다.** 부호가 바뀌는 값에서 비율은 무너진다 — +0.001% 에서
 * −0.05% 로 갔을 때 비율은 −5000% 이고 아무 뜻이 없다. 그 판단은 서버가 하고 화면은
 * 지표 종류로 표기를 고른다.
 */
function 변화표기(metric: Outlier): string {
  const 값 = metric.change as number;
  return metric.metric === "FUNDING_RATE" ? `${percent(값)}p` : percent(값 * 100);
}

const LABEL: Record<string, string> = {
  PRICE: "가격 (기준)",
  FUNDING_RATE: "펀딩비",
  OPEN_INTEREST: "미결제약정",
  LONG_SHORT_RATIO: "롱숏비율",
  TAKER_RATIO: "테이커 매수/매도",
  TOP_POSITION_RATIO: "상위 계정 포지션",
};

const HINT: Record<string, string> = {
  PRICE: "지표들과 같은 창의 가격. 지표 변화는 이것 옆에서만 뜻이 된다.",
  FUNDING_RATE: "8시간마다 오가는 수수료. 양수면 롱이 숏에게 낸다. 변화는 최근 하루.",
  OPEN_INTEREST: "아직 닫히지 않은 계약의 합(BTC). 줄면서 가격이 움직이면 청산이다.",
  LONG_SHORT_RATIO: "롱 계정 수 ÷ 숏 계정 수. 1계정 1표다.",
  TAKER_RATIO: "시장가 매수량 ÷ 매도량. 계정 수가 아니라 실제 체결량이라 취소가 없다.",
  TOP_POSITION_RATIO: "상위 계정의 롱숏비. 계정 수가 아니라 포지션 크기 기준이다.",
};
