import { NOTHING, percent, quantity, ratio } from "../../format";
import { PositionMeter } from "../../shared/Meter";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Outliers = components["schemas"]["MetricOutliersResponse"];

type Outlier = components["schemas"]["MetricOutlierResponse"];

/**
 * 세 지표가 평소와 얼마나 다른가.
 *
 * **배수가 아니라 위치로 말한다.** 펀딩비는 부호가 바뀌어 배수가 무너진다 — 평소가 +0.001%
 * 인데 지금 −0.05% 면 배수는 −50 이고 그 수는 아무 뜻이 없다.
 *
 * **모르는 것은 이상치가 아니다.** 표본이 모자라면 위치가 `null` 로 오고, 그때는 경고하지
 * 않는다. 모른다는 이유로 경고를 띄우면 그 경고는 곧 배경이 된다.
 *
 * **무엇을 하라고 말하지 않는다.** 펀딩비가 상위 2% 라는 것은 사실이고, 그것이 매수 신호인지는
 * 이 프로젝트가 답하지 않기로 한 질문이다.
 */
export function OutlierPanel({ outliers }: { outliers: Outliers }) {
  return (
    <section aria-label="이상치" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">평소와 다른가</h2>
        {outliers.hasOutlier && (
          <span className="text-xs text-warn">평소와 다른 지표가 있다</span>
        )}
      </div>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        최근 표본에서 지금 값이 어디쯤인가. <b>양 끝 5%</b> 안이면 표시한다. 무엇을 하라는
        뜻은 아니다 — 지금이 평소와 다르다는 사실뿐이다.
      </p>

      <dl className="mt-3 space-y-3">
        {outliers.metrics.map((metric) => (
          <Row key={metric.metric} metric={metric} />
        ))}
      </dl>
    </section>
  );
}

function Row({ metric }: { metric: Outlier }) {
  const 위치 = metric.topPercent === null || metric.topPercent === undefined
    ? null
    : 1 - metric.topPercent / 100;

  return (
    <div className="text-sm tabular-nums">
      <div className="flex items-baseline justify-between gap-4">
        <Term label={LABEL[metric.metric] ?? metric.metric} hint={HINT[metric.metric] ?? ""} />
        <dd className="shrink-0 text-right">
          <span className="text-ink">{현재값(metric)}</span>
          <span
            className={`mt-0.5 block text-xs font-normal ${
              metric.outlier ? "font-medium text-warn" : "text-ink-3"
            }`}
          >
            {위치 === null
              ? `표본 ${metric.sampleCount}개 — 아직 말할 수 없다`
              : `상위 ${percent(metric.topPercent as number)}`}
          </span>
        </dd>
      </div>

      {/* 위치를 눈금 위에 찍는다. 양 끝의 옅은 띠가 "양 끝 5%" 그 자체다. */}
      {위치 !== null && (
        <div className="mt-1.5">
          <PositionMeter
            ratio={위치}
            outlier={metric.outlier}
            label={`${LABEL[metric.metric] ?? metric.metric}: 상위 ${percent(metric.topPercent as number)}`}
          />
          <div className="mt-0.5 flex justify-between text-[10px] text-ink-4">
            <span>낮음</span>
            <span>평소</span>
            <span>높음</span>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 지표마다 단위가 다르다. 펀딩비는 %, 미결제약정은 BTC, 롱숏비율은 무차원이다.
 *
 * 서버가 셋을 한 필드(`current`)로 내므로 단위를 아는 것은 화면뿐이다. 이것은 § 3 이 금지한
 * 계산이 아니라 **표시 형식의 선택**이고, 자릿수는 여전히 `format/` 이 정한다.
 */
function 현재값(metric: Outlier): string {
  switch (metric.metric) {
    case "FUNDING_RATE":
      return percent(metric.current);
    case "OPEN_INTEREST":
      return quantity(metric.current);
    case "LONG_SHORT_RATIO":
      return ratio(metric.current);
    default:
      return NOTHING;
  }
}

const LABEL: Record<string, string> = {
  FUNDING_RATE: "펀딩비",
  OPEN_INTEREST: "미결제약정",
  LONG_SHORT_RATIO: "롱숏비율",
};

const HINT: Record<string, string> = {
  FUNDING_RATE: "8시간마다 오가는 수수료. 양수면 롱이 숏에게 낸다. 최근 30일과 견준다.",
  OPEN_INTEREST: "아직 닫히지 않은 계약의 합(BTC). 최근 2시간 반과 견준다.",
  LONG_SHORT_RATIO: "롱 계정 수 ÷ 숏 계정 수. 최근 2시간 반과 견준다.",
};
