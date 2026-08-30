import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Stances } from "./Stances";
import type { components } from "../../api/schema";

type Series = components["schemas"]["IndicatorSeriesResponse"];
type Stance = Series["stances"][number];

/**
 * <b>재는 것은 세는 방식 하나다.</b> 이 화면이 지난 판에서 틀렸던 것은 값이 아니라 산술이었다 —
 * 다섯 딱지를 한 번에 세고 있었고, 종가가 밴드 상단 위인 것은 되돌림에게 과열이고 추세에게
 * 돌파인데 둘 다 「위」로 적힌다. 근거는 `docs/spec/indicator-usage.md` § 2.
 *
 * <b>차트에서 떼어 낸 이유가 이것이다.</b> 붙어 있는 동안은 캔버스를 흉내 내지 않고는 이
 * 블록에 닿을 수 없었고, 그래서 세는 규칙에 테스트가 하나도 없었다.
 */
function 딱지(
  indicator: string,
  family: Stance["family"],
  stance: Stance["stance"],
  statement = "그렇게 서 있다",
): Stance {
  return { indicator, family, stance, statement };
}

/** 추세 셋이 전부 위, 되돌림 둘이 전부 아래인 자리. 합쳐서 세면 3 대 2 로 뭉개진다. */
const 갈리는_자리: Stance[] = [
  딱지("일목", "TREND", "LONG", "종가가 구름 위에 있다"),
  딱지("볼린저", "REVERSION", "SHORT", "종가가 하단 아래에 있다"),
  딱지("이동평균", "TREND", "LONG", "20 > 50 > 200 으로 놓여 있다"),
  딱지("RSI", "REVERSION", "SHORT", "31.2 로 50 아래에 있다"),
  딱지("MACD", "TREND", "LONG", "시그널 위에 있다"),
];

const 센것 = (이름: string) => screen.getByLabelText(이름).textContent;

describe("지표가 선 자리", () => {
  it("추세와 되돌림을 따로 센다", () => {
    render(<Stances stances={갈리는_자리} />);

    expect(센것("추세 위")).toBe("3");
    expect(센것("추세 아래")).toBe("0");
    expect(센것("되돌림 위")).toBe("0");
    expect(센것("되돌림 아래")).toBe("2");
  });

  /**
   * <b>합친 수가 화면에 있으면 갈라 놓은 것이 아무 일도 하지 않는다.</b> 사람은 큰 수를
   * 먼저 읽으므로 「위 3」이 어디엔가 무리 없이 떠 있으면 그것이 결론으로 읽힌다.
   */
  it("두 무리를 합친 수를 만들지 않는다", () => {
    render(<Stances stances={갈리는_자리} />);

    expect(screen.getAllByLabelText(/(위|아래)$/)).toHaveLength(4);
  });

  it("지표는 자기 무리 안에만 적힌다", () => {
    render(<Stances stances={갈리는_자리} />);

    const 추세 = within(screen.getByLabelText("추세 무리"));
    const 되돌림 = within(screen.getByLabelText("되돌림 무리"));

    expect(추세.getByText("일목")).toBeTruthy();
    expect(되돌림.queryByText("일목")).toBeNull();
    expect(되돌림.getByText("RSI")).toBeTruthy();
  });

  /**
   * <b>말할 수 없는 것은 세지 않는다.</b> 중립으로 세면 "가운데 있다" 는 없는 사실이 생기고,
   * 위나 아래로 세면 없는 근거가 생긴다.
   */
  it("봉이 모자란 지표는 어느 쪽으로도 세지 않는다", () => {
    render(
      <Stances
        stances={[
          딱지("일목", "TREND", "UNKNOWN", "봉이 모자라 말할 수 없다"),
          딱지("MACD", "TREND", "LONG", "시그널 위에 있다"),
        ]}
      />,
    );

    expect(센것("추세 위")).toBe("1");
    expect(센것("추세 아래")).toBe("0");
    expect(screen.getByText("봉이 모자라 말할 수 없다")).toBeTruthy();
  });

  /**
   * <b>"무엇을 하라고 말하지 않는다" 는 여기서 재지 않는다.</b> 문장을 만드는 것은 서버이고
   * `StanceReadoutTest` 가 그 자리에서 검사한다. 이 화면의 글에는 <b>그러지 않는다고 적은
   * 문장</b>이 함께 있어서 — "딱지가 유리한 쪽을 가리키지 않는다" — 낱말로 훑으면 설명문이
   * 걸린다. 실제로 걸렸고, 그것은 화면이 틀린 것이 아니라 재는 자리가 틀린 것이었다.
   */
});
