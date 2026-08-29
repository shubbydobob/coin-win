import { decimal } from "../../form";
import type { components } from "../../api/schema";

type Request = components["schemas"]["CompoundTargetRequest"];

export interface CompoundForm {
  readonly startingCapital: string;
  readonly monthlyTarget: string;
  readonly months: string;
  readonly leverage: string;
  readonly marginUsage: string;
  readonly feeRate: string;
  readonly slippage: string;
  readonly tradesPerMonth: string;
}

/**
 * 시작 입력값.
 *
 * 자산은 `scope.md` 의 증거금 전제(800 USDT)다. 수수료 기본값은 **바이낸스 USDⓈ-M 무기한의
 * 일반 사용자 테이커 0.05%** 이고 메이커는 0.02% 다 — 지정가로만 들어가면 그쪽을 적는다.
 *
 * **투입 비율 20% 는 슬리피지와 같은 성격의 자리표시자다.** 근거가 있는 수가 아니라 "전액을
 * 매 거래에 넣지는 않는다" 는 사실만 담은 값이고, 실제 비율은 사람이 자기 매매에서 재야
 * 한다. 100 으로 두면 옛 전제(명목 = 자산 × 레버리지)가 그대로 돌아온다.
 *
 * **이 수들은 사본이 아니다**(`docs/adr/020` § 4). 화면에 보이는 값이 곧 요청에 실리는 값이라
 * 갈라질 자리가 없고, 거래소가 수수료를 바꾸면 사람이 이 칸을 고친다. 서버에 박아 두면
 * 바뀐 날 서버만 옛 숫자를 말한다.
 */
export const STARTING_FORM: CompoundForm = {
  startingCapital: "800",
  monthlyTarget: "5",
  months: "12",
  leverage: "10",
  marginUsage: "20",
  feeRate: "0.05",
  slippage: "0.02",
  tradesPerMonth: "20",
};

export function toRequest(form: CompoundForm): Request {
  return {
    startingCapital: decimal(form.startingCapital),
    monthlyTarget: decimal(form.monthlyTarget),
    months: decimal(form.months),
    leverage: decimal(form.leverage),
    marginUsage: decimal(form.marginUsage),
    feeRate: decimal(form.feeRate),
    slippage: decimal(form.slippage),
    tradesPerMonth: decimal(form.tradesPerMonth),
  };
}
