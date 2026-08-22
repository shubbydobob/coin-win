import { decimal } from "../../form";
import type { components } from "../../api/schema";

type Request = components["schemas"]["AnalyzePositionRequest"];

export type Direction = Request["direction"];

export interface EntryRow {
  readonly price: string;
  readonly allocation: string;
}

export interface PlanForm {
  readonly direction: Direction;
  readonly entries: readonly EntryRow[];
  readonly stopLoss: string;
  readonly takeProfit: string;
  readonly leverage: string;
  readonly accountBalance: string;
  readonly riskPercent: string;
}

/**
 * 처음 상태는 **50% 분할 두 회차**다. `scope.md` 의 매매 방식 전제가 그것이고, 회차 수를
 * 0 에서 시작하면 이 도구가 무엇을 위한 것인지가 화면에서 사라진다. 값이 아니라 칸의 개수를
 * 미리 두는 것이므로 계산이 아니다.
 */
export const EMPTY_FORM: PlanForm = {
  direction: "LONG",
  entries: [
    { price: "", allocation: "50" },
    { price: "", allocation: "50" },
  ],
  stopLoss: "",
  takeProfit: "",
  leverage: "",
  accountBalance: "",
  riskPercent: "",
};

export function toRequest(form: PlanForm): Request {
  return {
    direction: form.direction,
    entries: form.entries.map((entry) => ({
      price: decimal(entry.price),
      allocation: decimal(entry.allocation),
    })),
    stopLoss: decimal(form.stopLoss),
    takeProfit: decimal(form.takeProfit),
    leverage: decimal(form.leverage),
    accountBalance: decimal(form.accountBalance),
    riskPercent: decimal(form.riskPercent),
  };
}

/**
 * 사용자가 방금 타이핑한 비중의 합.
 *
 * **이것은 산술이지 판정이 아니다.** 100 인지 아닌지를 여기서 말하지 않는다 — 비중 합 규칙은
 * `EntryLadder` 에 있고, 화면이 같은 규칙을 두면 초안이 통과했는데 계획 API 가 거절하는 일이
 * 생긴다. 합계는 보여 주되 판정은 서버에 맡긴다(§ 3).
 */
export function allocationSum(entries: readonly EntryRow[]): number {
  return entries.reduce((sum, entry) => {
    const value = decimal(entry.allocation);
    return Number.isNaN(value) ? sum : sum + value;
  }, 0);
}
