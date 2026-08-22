import { dayStart, decimal } from "../../form";
import type { components } from "../../api/schema";

type Request = components["schemas"]["RunBacktestRequest"];

type CapitalMode = components["schemas"]["AccountRequest"]["capitalMode"];

export interface BacktestForm {
  readonly symbol: string;
  readonly interval: string;
  readonly from: string;
  readonly to: string;
  readonly pivotLookback: string;
  readonly clusterMultiple: string;
  readonly minTouches: string;
  readonly atrPeriod: string;
  readonly stopBufferMultiple: string;
  readonly minRiskReward: string;
  readonly indicatorFilter: boolean;
  readonly initialCapital: string;
  readonly riskPercent: string;
  readonly leverage: string;
  readonly capitalMode: CapitalMode;
}

/**
 * 처음 화면에 놓이는 **입력값**이다. 정책이 아니다.
 *
 * 이 경계가 § 3 에서 가장 애매한 자리다. 대 설정의 기본값은 도메인
 * (`ZoneSettings.standard()`)에도 있으므로 사본으로 보인다. 그럼에도 두는 이유는
 * **화면에 보이는 수가 곧 요청에 실리는 수**이기 때문이다 — 사용자가 보는 것과 서버가 받는
 * 것이 언제나 같으므로 갈라질 값이 없다. 손익비 1.5 같은 **판정 기준**을 복창하는 것과는
 * 다르다. 그쪽은 화면에 보이지 않는 규칙이라 갈라져도 알 수 없다.
 *
 * 값의 출처는 `.claude/docs/roadmap.md` Phase 6 의 결론이다 — 기본값은 5 / 0.5 / 2 / 14 로
 * 둔다. 그 표의 최고 조합을 기본값으로 박는 것이 과최적화의 정의 그 자체다.
 */
export const STARTING_FORM: BacktestForm = {
  symbol: "BTCUSDT",
  interval: "4h",
  from: "",
  to: "",
  pivotLookback: "5",
  clusterMultiple: "0.5",
  minTouches: "2",
  atrPeriod: "14",
  stopBufferMultiple: "1.0",
  minRiskReward: "1.5",
  indicatorFilter: false,
  initialCapital: "800",
  riskPercent: "2",
  leverage: "10",
  capitalMode: "COMPOUND",
};

export function toRequest(form: BacktestForm): Request {
  return {
    symbol: form.symbol,
    interval: form.interval,
    from: dayStart(form.from) ?? "",
    to: dayStart(form.to) ?? "",
    zones: {
      pivotLookback: decimal(form.pivotLookback),
      clusterMultiple: decimal(form.clusterMultiple),
      minTouches: decimal(form.minTouches),
      atrPeriod: decimal(form.atrPeriod),
    },
    rules: {
      stopBufferMultiple: decimal(form.stopBufferMultiple),
      minRiskReward: decimal(form.minRiskReward),
      indicatorFilter: form.indicatorFilter,
    },
    account: {
      initialCapital: decimal(form.initialCapital),
      riskPercent: decimal(form.riskPercent),
      leverage: decimal(form.leverage),
      capitalMode: form.capitalMode,
    },
  };
}
