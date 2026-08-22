import type { components } from "../api/schema";

/**
 * 열거값의 한국어 라벨.
 *
 * **`shared/` 의 첫 입주자다.** 방향 라벨을 `/plan` 이 쓰고 `/journal` 이 또 쓰기 시작한
 * 시점에 올렸다 — 두 번째 사용처가 생겼을 때만 만든다는 § 4 의 규칙 그대로다.
 *
 * 이것은 § 7 이 금지한 "화면이 짓는 문장" 이 아니다. 서버가 내는 것은 `PLANNED_STOP` 이라는
 * **식별자**이고 그것을 사람이 읽을 말로 옮기는 자리는 화면뿐이다. 다만 그 목록이 서버와
 * 갈라지지 않아야 하므로 `Record<열거타입, string>` 으로 못 박는다 — 백엔드가 값을 하나 더
 * 늘리면 **여기가 컴파일되지 않는다.**
 */
type Direction = components["schemas"]["PlanResponse"]["direction"];

type ExitReason = components["schemas"]["OutcomeResponse"]["exitReason"];

type BandPosition = components["schemas"]["EntryResultResponse"]["ichimokuPosition"];

export const DIRECTION: Record<Direction, string> = {
  LONG: "롱",
  SHORT: "숏",
};

export const EXIT_REASON: Record<ExitReason, string> = {
  PLANNED_STOP: "계획 손절",
  PLANNED_TARGET: "계획 익절",
  MANUAL_EARLY: "조기 청산",
  HELD_PAST_STOP: "손절 지나 보유",
  LIQUIDATED: "청산당함",
};

export const BAND_POSITION: Record<BandPosition, string> = {
  ABOVE: "위",
  INSIDE: "안",
  BELOW: "아래",
};
