import { NOTHING, percent, price, quantity, ratio } from "../../format";
import type { Side } from "../../shared/Meter";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Outliers = components["schemas"]["MetricOutliersResponse"];

type Outlier = components["schemas"]["MetricOutlierResponse"];

/**
 * 지표마다 **신호등 하나 · 값 하나 · 화살표 하나 · 문장 하나.**
 *
 * 앞판은 지표 한 줄에 그림이 둘이었다 — 스파크라인(최근 추세)과 눈금 막대(표본 안 위치).
 * 둘 다 정확했지만 **읽히지 않았다.** 꼬불선은 모양이 이미 오른 것을 보여 주므로 색이 할 일이
 * 없었고, 눈금 막대는 "상위 96.7%" 를 그림으로 한 번 더 말한 것이라 숫자와 겹쳤다.
 * 다섯 지표 × 그림 둘 = 열 개의 그림을 훑어야 "지금 뭐가 이상한가" 하나에 닿았다.
 *
 * **남긴 것은 그 질문에 직접 답하는 것뿐이다.**
 *
 * - 신호등 — 평소인가, 치우쳤나, 평소와 다른가
 * - 값과 화살표 — 얼마이고 어느 쪽으로 갔나
 * - 문장 — 그것이 그 지표의 말로 무슨 뜻인가
 *
 * **화살표를 남긴 이유가 중요하다.** 위치만 있는 화면은 정지 화면이다 — 계기가 된 사건(숏
 * 스퀴즈)은 미결제약정이 *급감*할 때 벌어지는데 위치만 보면 "높음" 에 머물러 있을 수도 있다.
 * 그림을 걷어내면서 그 교훈까지 되돌리면 앞판의 실패를 다시 하는 것이다.
 *
 * **신호등 색은 등락과 아무 관계가 없다.** 회색 → 노랑 → 주황이고 빨강·초록을 쓰지 않는다.
 * 그 둘은 이 저장소에서 이미 롱 쪽 / 숏 쪽을 뜻한다(`shared/tone.ts`). 그래서 이 패널에서
 * 진영은 **글로만** 말한다 — 색이 두 가지 일을 겹쳐서 하면 둘 다 못 읽는다.
 *
 * **무엇을 하라고 말하지 않는다.** 문장도 일어난 일까지만 적는다.
 */
export function OutlierPanel({ outliers }: { outliers: Outliers }) {
  const 유별난것 = outliers.metrics
    .filter((metric) => metric.outlier)
    .map((metric) => LABEL[metric.metric] ?? metric.metric);
  /*
    **이상치를 위로 올린다.** 순서를 고정하면 드문 것이 흔한 것 사이에 묻히고, 그러면
    화면이 "무엇이 특이한가" 가 아니라 "다섯 개가 있다" 를 말한다. 이상치가 없으면 원래
    순서 그대로다 — `sort` 가 안정 정렬이라 그 경우 아무것도 움직이지 않는다.
  */
  const 정렬된것 = [...outliers.metrics].sort((a, b) => {
    if (a.outlier === b.outlier) {
      return 0;
    }
    return a.outlier ? -1 : 1;
  });

  return (
    <section aria-label="이상치" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">평소와 다른가</h2>
        {유별난것.length > 0 && (
          <span className="text-xs font-medium text-alert">{유별난것.join(" · ")}</span>
        )}
      </div>

      {/*
        범례를 한 줄로 편다. 앞판은 접어 두었는데, 접힌 설명은 색이 무슨 뜻인지 모르는 사람에게
        **없는 것과 같다.** 세 단계뿐이라 한 줄에 들어가므로 접을 이유가 사라졌다.
      */}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink-4">
        {(["usual", "leaning", "unusual"] as const).map((level) => (
          <span key={level} className="flex items-center gap-1">
            <Light level={level} />
            {LEVEL_TEXT[level]}
          </span>
        ))}
        <span className="text-ink-4">— 드문 정도이지 좋고 나쁨이 아니다</span>
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
        {정렬된것.map((metric) => (
          <div key={metric.metric} className="py-2">
            <Row metric={metric} />
          </div>
        ))}
      </dl>
    </section>
  );
}

function Row({ metric, baseline = false }: { metric: Outlier; baseline?: boolean }) {
  const 단계 = 신호단계(metric);
  const 변화 = metric.change ?? null;
  const 오름 = 변화 === null ? null : 변화 > 0;
  const 이름 = LABEL[metric.metric] ?? metric.metric;

  return (
    <div className="text-sm tabular-nums">
      <div className="flex items-baseline gap-2">
        {/* 기준선(가격)에는 표본 위치가 뜻이 없으므로 신호등도 없다. */}
        {!baseline && <Light level={단계} label={`${이름}: ${LEVEL_TEXT[단계]}`} />}
        <Term label={이름} hint={HINT[metric.metric] ?? ""} />
        <dd className="ml-auto shrink-0 text-right">
          <span className="text-ink">{현재값(metric)}</span>
          <span
            className={`mt-0.5 block text-xs font-normal ${변화 === null ? "text-ink-4" : "text-ink-2"}`}
          >
            {변화 === null ? "변화를 말할 수 없다" : `${오름 ? "▲" : "▼"} ${변화표기(metric)}`}
          </span>
        </dd>
      </div>

      {/*
        진영. **표본과 무관하므로 언제나 있다** — 펀딩비가 양수면 롱이 숏에게 낸다는 것은
        정의이지 관측이 아니다. 기준선(가격)에는 축이 없으므로 뜨지 않는다.
      */}
      {!baseline && (
        <p className="mt-1 pl-4 text-xs leading-snug text-ink-2">
          {SIDE_TEXT[metric.metric]?.[metric.side as Side] ?? ""}
        </p>
      )}

      {!baseline && (
        <p className="mt-0.5 pl-4 text-[11px] text-ink-4">
          {metric.topPercent === null || metric.topPercent === undefined
            ? `표본 ${metric.sampleCount}개 — 위치를 아직 말할 수 없다`
            : `${LEVEL_TEXT[단계]} · 최근 ${metric.sampleCount}개 중 상위 ${percent(metric.topPercent)}`}
        </p>
      )}
    </div>
  );
}

type Level = "usual" | "leaning" | "unusual";

const LEVEL_TEXT: Record<Level, string> = {
  usual: "평소",
  leaning: "치우침",
  unusual: "평소와 다름",
};

const LEVEL_DOT: Record<Level, string> = {
  usual: "bg-ink-4",
  leaning: "bg-warn",
  unusual: "bg-alert",
};

/**
 * 신호등 하나. **색만으로 뜻을 지지 않는다** — 옆에 언제나 글자가 붙고, 접근성 이름도 글자다.
 *
 * 색맹이 아니어도 필요하다. 회색·노랑·주황은 어두운 배경에서 서로 가까워 보이고, 이 화면은
 * 빠르게 훑는 자리다.
 */
function Light({ level, label }: { level: Level; label?: string }) {
  return (
    <span
      className={`inline-block size-2.5 shrink-0 rounded-full ${LEVEL_DOT[level]}`}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  );
}

/**
 * 세 단계의 경계. **서버가 판정한 이상치가 가장 세다.**
 *
 * 가운데 단계(치우침)는 화면이 정한다 — 양 끝 20% 다. 서버의 이상치 기준(양 끝 5%)에 닿지는
 * 않지만 평소라고 하기도 어려운 구간이 있고, 그 구간을 회색으로 칠하면 **경계 직전이 아무 일도
 * 아닌 것처럼 보인다.**
 *
 * 이 20% 는 근거 있는 수가 아니라 자리표시자다 — 슬리피지 기본값과 같은 자리다.
 */
function 신호단계(metric: Outlier): Level {
  if (metric.outlier) {
    return "unusual";
  }
  const 상위 = metric.topPercent;
  if (상위 === null || 상위 === undefined) {
    return "usual";
  }
  return 상위 <= 20 || 상위 >= 80 ? "leaning" : "usual";
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
