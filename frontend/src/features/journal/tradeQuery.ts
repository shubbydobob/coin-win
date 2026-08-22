import { dayStart } from "../../form";
import type { operations } from "../../api/schema";

/**
 * 조회 조건의 타입은 이제 **오퍼레이션의 질의 파라미터**에서 온다.
 *
 * `TradeQueryParams` 라는 컴포넌트 스키마는 더 이상 없다 — 그것이 있었다는 사실 자체가
 * 문서가 와이어와 다르다는 증거였다. 질의 문자열에는 중첩이 없으므로 조건도 평평하다.
 */
type Params = NonNullable<operations["closedTrades"]["parameters"]["query"]>;

export interface FilterDraft {
  readonly closedFrom: string;
  readonly closedTo: string;
  readonly direction: "" | NonNullable<Params["direction"]>;
  readonly exitReason: "" | NonNullable<Params["exitReason"]>;
  readonly followedPlan: "" | "true" | "false";
}

export const NO_FILTER: FilterDraft = {
  closedFrom: "",
  closedTo: "",
  direction: "",
  exitReason: "",
  followedPlan: "",
};

/** 질의 문자열에 실을 값들. `undefined` 는 실리지 않으므로 "조건 없음" 이 된다. */
export type TradeQuery = Record<string, string | boolean | undefined>;

/**
 * 조회 조건 하나를 만든다. **목록과 집계가 이 결과를 함께 쓴다.**
 *
 * 두 요청에 각각 조건을 만들면 언젠가 갈리고, 그러면 집계가 목록과 다른 모집단을 보게 된다 —
 * 화면이 거짓말을 하는 자리다(§ 6.3). 만드는 곳을 하나로 두는 것이 그것을 막는 유일한 방법이다.
 */
export function toQuery(draft: FilterDraft): TradeQuery {
  return {
    closedFrom: dayStart(draft.closedFrom),
    closedTo: dayStart(draft.closedTo),
    direction: draft.direction || undefined,
    exitReason: draft.exitReason || undefined,
    followedPlan: draft.followedPlan === "" ? undefined : draft.followedPlan === "true",
  };
}
