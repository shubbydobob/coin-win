import { describe, expectTypeOf, it } from "vitest";

import type { components } from "./schema";

type Schemas = components["schemas"];

/**
 * 생성된 타입이 사실대로 말하는지 본다. 명세 § 11 완료 조건 6 의 증거다.
 *
 * 이 단언들은 실행이 아니라 `tsc --noEmit` 에서 걸린다 — 스키마가 다시 거짓말을 하기 시작하면
 * 화면 코드보다 먼저 여기가 깨진다.
 */
describe("생성된 타입은 사실대로 말한다", () => {
  it("null 이 오는 세 필드는 타입에 null 을 담는다", () => {
    expectTypeOf<Schemas["SummaryResponse"]["profitFactor"]>().toEqualTypeOf<number | null>();
    expectTypeOf<Schemas["TradeResponse"]["entry"]>()
      .toEqualTypeOf<Schemas["EntryResultResponse"] | null>();
    expectTypeOf<Schemas["TradeResponse"]["outcome"]>()
      .toEqualTypeOf<Schemas["OutcomeResponse"] | null>();
  });

  it("나머지 응답 필드는 optional 이 아니다", () => {
    // optional 이면 타입이 `number | undefined` 가 되어 아래 단언이 깨진다.
    expectTypeOf<Schemas["SummaryResponse"]["netPnl"]>().toEqualTypeOf<number>();
    expectTypeOf<Schemas["TradeResponse"]["state"]>().toEqualTypeOf<string>();
    expectTypeOf<Schemas["PositionAnalysisResponse"]["requiredMargin"]>().toEqualTypeOf<number>();
    expectTypeOf<Schemas["PositionAnalysisResponse"]["fillStates"]>()
      .toEqualTypeOf<Schemas["FillStateResponse"][]>();
  });
});
