import { NOTHING, percent, price, quantity, ratio } from "../../format";
import { PositionMeter, type Side } from "../../shared/Meter";
import { BG_TONE, TEXT_TONE, type Tone } from "../../shared/tone";
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
 * **네 번째를 뒤늦게 더했다 — 어느 쪽 진영인가.** 화면이 "상위 96.7%" 까지만 말하고 그것이
 * 롱 쪽인지 숏 쪽인지를 말하지 않고 있었다. 지표 다섯 중 넷은 중립점(펀딩비 0, 비율 1)을
 * 기준으로 **부호가 곧 진영**인데 그 사실이 어디에도 그려져 있지 않았다.
 *
 * **여기까지가 사실이고 그 다음은 아니다.** 펀딩비가 양수면 롱이 숏에게 낸다는 것은 정의다.
 * "그러니 숏이 유리하다" 는 정의가 아니라 예측이고, 이 저장소는 그 예측에 증거가 없다 —
 * `docs/adr/021` 이 같은 종류의 전제를 7년 15,110봉에서 반증했다. 그래서 붐비는 쪽은 세고
 * 우열은 내지 않는다.
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
        최근 표본에서 지금 값이 어디인가(<b>양 끝 5%</b> 면 표시), 어느 쪽으로 갔나, 그리고
        <b> 어느 쪽이 붐비나</b>. 붐비는 쪽이지 유리한 쪽이 아니다 — 어느 쪽이 유리한가는 이
        도구가 답하지 않는다.
      </p>

      {/*
        **범례.** 색이 무슨 뜻인지가 어디에도 없었다. 첫 판은 글로 적었는데 그것도 틀렸다 —
        "오르면 초록" 은 아무것도 말하지 않는다. 선의 모양이 이미 오른 것을 보여 주기 때문이다.

        고친 것은 범례가 아니라 **색 자체**다. 지금은 색 하나가 뜻 하나를 갖고
        (`shared/tone`), 범례는 그 넷을 점으로 보인다 — 읽는 것이 아니라 맞대어 보는 것이다.
      */}
      <div className="mt-2 rounded bg-surface-2 px-2 py-1.5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
          <span className="text-ink-4">색</span>
          <Swatch tone="long" label="롱 쪽" />
          <Swatch tone="short" label="숏 쪽" />
          <Swatch tone="outlier" label="평소와 다름" />
          <Swatch tone="none" label="방향 없음" />
        </div>
        {/* 눈금 읽는 법. 라벨을 막대 아래 제자리에 놓아 화살표 없이도 무엇을 가리키는지 보인다. */}
        <div className="mt-2 max-w-72">
          <PositionMeter ratio={0.68} outlier={false} neutral={0.42} side="LONG" label="눈금 읽는 법 예시" />
          <div className="relative mt-0.5 h-3 text-[10px] text-ink-4">
            <span className="absolute left-0">숏 쪽</span>
            <span className="absolute -translate-x-1/2" style={{ left: "42%" }}>│중립</span>
            <span className="absolute -translate-x-1/2" style={{ left: "68%" }}>●지금</span>
            <span className="absolute right-0">롱 쪽</span>
          </div>
          <p className="mt-2 text-[10px] leading-snug text-ink-4">
            칠해진 길이가 <b>중립에서 얼마나 치우쳤나</b>. 양 끝 옅은 띠는 평소와 다른 구간(각 5%).
          </p>
        </div>
      </div>

      {/*
        붐비는 쪽 셈. **두 수를 하나로 합치지 않는다** — 합치려면 지표에 가중치를 줘야 하고
        그 가중치는 검증할 방법이 없다. 셋 대 하나라는 것은 사실이고, 그래서 어느 쪽이
        유리한가는 사실이 아니다.
      */}
      <div className="mt-2 flex items-center gap-2 rounded bg-surface-2 px-2 py-1.5 text-xs">
        <span className="text-ink-3">붐비는 쪽</span>
        <span className="font-medium tabular-nums text-up">롱 {outliers.crowdedLong}</span>
        <span className="text-ink-4">·</span>
        <span className="font-medium tabular-nums text-down">숏 {outliers.crowdedShort}</span>
        <span className="ml-auto text-[10px] text-ink-4">방향 있는 지표만 센다</span>
      </div>

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
  const 위치 = 눈금위치(metric.topPercent);
  const 진영 = metric.side as Side;
  const 가운데 = 눈금위치(metric.neutralPercent) ?? undefined;
  const 변화 = metric.change ?? null;
  const 오름 = 변화 === null ? null : 변화 > 0;
  const 흐름 = 흐름색(metric, 오름);

  return (
    <div className="text-sm tabular-nums">
      <div className="flex items-baseline justify-between gap-4">
        <Term label={LABEL[metric.metric] ?? metric.metric} hint={HINT[metric.metric] ?? ""} />
        <dd className="shrink-0 text-right">
          <span className="text-ink">{현재값(metric)}</span>
          <span className={`mt-0.5 block text-xs font-normal ${TEXT_TONE[흐름]}`}>
            {변화 === null ? "변화를 말할 수 없다" : `${오름 ? "▲" : "▼"} ${변화표기(metric)}`}
          </span>
        </dd>
      </div>

      {/*
        진영. **표본과 무관하므로 언제나 있다** — 펀딩비가 양수면 롱이 숏에게 낸다는 것은
        정의이지 관측이 아니다. 기준선(가격)에는 축이 없으므로 뜨지 않는다.
      */}
      {!baseline && (
        <p className={`mt-1 text-xs font-normal ${진영색(진영)}`}>{SIDE_TEXT[metric.metric]?.[진영] ?? ""}</p>
      )}

      <div className="mt-1.5 grid grid-cols-[1fr_auto] items-center gap-3">
        <Sparkline
          samples={metric.samples}
          tone={흐름}
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
            neutral={가운데}
            side={진영}
            label={`${LABEL[metric.metric] ?? metric.metric}: 상위 ${percent(metric.topPercent as number)}${
              가운데 === undefined ? "" : `, ${SIDE_LABEL[진영]}`
            }`}
          />
          <div className="mt-0.5 flex justify-between text-[10px] text-ink-4">
            <span>{가운데 === undefined ? "낮음" : "숏 쪽"}</span>
            <span className={metric.outlier ? "font-medium text-warn" : "text-ink-3"}>
              상위 {percent(metric.topPercent as number)}
            </span>
            <span>{가운데 === undefined ? "높음" : "롱 쪽"}</span>
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

/** 색 견본 하나. 글로 설명하는 대신 실제 색을 옆에 놓는다. */
function Swatch({ tone, label }: { tone: Tone; label: string }) {
  return (
    <span className="flex items-center gap-1 text-ink-3">
      <span className={`size-2 rounded-full ${BG_TONE[tone]}`} aria-hidden="true" />
      {label}
    </span>
  );
}

/**
 * 변화가 **어느 쪽으로 간 것인가**. 화살표와 스파크라인이 이 색을 쓴다.
 *
 * **"올랐다" 가 아니라 "롱 쪽으로 갔다" 를 뜻한다.** 네 지표는 전부 값이 클수록 롱 쪽이므로
 * (펀딩비 0 초과 · 비율 1 초과) 오름은 곧 롱 쪽으로 간 것이다. 가격도 같다 — 오르면 롱 쪽으로
 * 움직인 것이다.
 *
 * **미결제약정만 회색이다.** 축이 없어서 늘든 줄든 어느 편도 아니다. 여기에 초록·빨강을 쓰면
 * "포지션이 쌓이는 것은 좋은 일" 이라는 뜻이 없는 말이 색으로 생긴다.
 *
 * 이상치는 진영보다 앞선다 — 드문 쪽이 흔한 쪽에 덮이면 경고가 사라진다.
 */
function 흐름색(metric: Outlier, 오름: boolean | null): Tone {
  if (metric.outlier) {
    return "outlier";
  }
  if (오름 === null || metric.metric === "OPEN_INTEREST") {
    return "none";
  }
  return 오름 ? "long" : "short";
}

/** 위쪽으로부터의 비율(%)을 왼쪽부터의 눈금 위치(0~1)로. 없으면 없다. */
function 눈금위치(topPercent: number | null | undefined): number | null {
  return topPercent === null || topPercent === undefined ? null : 1 - topPercent / 100;
}

function 진영색(side: Side): string {
  if (side === "LONG") {
    return "text-up";
  }
  return side === "SHORT" ? "text-down" : "text-ink-4";
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

const SIDE_LABEL: Record<Side, string> = {
  LONG: "롱 쪽",
  SHORT: "숏 쪽",
  BALANCED: "양쪽이 같다",
  NONE: "축 없음",
};

/**
 * 진영을 **그 지표의 말로** 옮긴다. 같은 "롱 쪽" 이어도 근거가 전부 다르기 때문이다 —
 * 펀딩비는 비용이고, 롱숏비율은 머릿수이고, 테이커는 체결량이고, 상위 계정은 크기다.
 * 한 문장으로 뭉뚱그리면 "롱 3" 이라는 셈이 무엇을 센 것인지가 사라진다.
 *
 * **전부 일어난 일까지만 적는다.** "롱이 숏에게 낸다" 는 정의이고 "그러니 숏이 유리하다" 는
 * 예측이다. 뒤쪽은 이 표에 없고, `어떤_문장도_행동을_지시하지_않는다` 가 그것을 지킨다.
 */
export const SIDE_TEXT: Record<string, Partial<Record<Side, string>>> = {
  FUNDING_RATE: {
    LONG: "롱이 숏에게 낸다 — 들고 있는 쪽은 롱이 비용을 문다",
    SHORT: "숏이 롱에게 낸다 — 들고 있는 쪽은 숏이 비용을 문다",
    BALANCED: "양쪽 다 내지 않는다",
  },
  OPEN_INTEREST: {
    NONE: "방향 없음 — 크기다. 위 가격과 짝지어야 뜻이 된다",
  },
  LONG_SHORT_RATIO: {
    LONG: "계정 수로는 롱이 많다",
    SHORT: "계정 수로는 숏이 많다",
    BALANCED: "계정 수가 반반이다",
  },
  TAKER_RATIO: {
    LONG: "시장가로 때린 쪽은 매수다",
    SHORT: "시장가로 때린 쪽은 매도다",
    BALANCED: "때린 양이 반반이다",
  },
  TOP_POSITION_RATIO: {
    LONG: "큰손은 롱 쪽에 실려 있다",
    SHORT: "큰손은 숏 쪽에 실려 있다",
    BALANCED: "큰손이 반반이다",
  },
};
