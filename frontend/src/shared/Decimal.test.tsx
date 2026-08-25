import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Decimal } from "./Decimal";

/**
 * 자릿수를 **줄이지 않고 덜 튀게만** 한다.
 *
 * **이 테스트의 요점은 "지워지지 않는다" 하나다.** `format/` 이 "스케일을 줄여서 표시하지
 * 않는다" 고 못 박아 둔 이유는 줄이는 순간 브라우저가 반올림을 하게 되고 화면의 수와 서버의
 * 수가 갈라지기 때문이다. 여기서 뒷자리가 한 글자라도 사라지면 그 규칙이 무너진다.
 */
describe("흐린 뒷자리", () => {
  function 보이는글자(): string {
    return screen.getByTestId("값").textContent ?? "";
  }

  function 그린다(text: string, keep?: number) {
    render(
      <span data-testid="값">
        <Decimal text={text} keep={keep} />
      </span>,
    );
  }

  it("뒷자리를 지우지 않는다", () => {
    그린다("−0.2008%", 2);

    expect(보이는글자()).toBe("−0.2008%");
  });

  it("읽는 자리와 흐린 자리를 다른 요소로 나눈다", () => {
    그린다("−0.2008%", 2);

    const 흐린것 = screen.getByTestId("값").querySelector("span");
    expect(흐린것?.textContent).toBe("08");
  });

  /** `keep` 이 0 이면 소수점부터 흐려진다. 78,579.70 에서 눈이 읽는 것은 78,579 다. */
  it("남길 자리가 없으면 소수점째로 흐려진다", () => {
    그린다("78,579.70");

    expect(보이는글자()).toBe("78,579.70");
    expect(screen.getByTestId("값").querySelector("span")?.textContent).toBe(".70");
  });

  it("소수점이 없으면 그대로 둔다", () => {
    그린다("터치 3");

    expect(보이는글자()).toBe("터치 3");
    expect(screen.getByTestId("값").querySelector("span")).toBeNull();
  });

  /** 단위와 부호는 값의 일부다. 흐려지는 것은 소수점 뒤 숫자뿐이다. */
  it("부호와 단위는 흐리지 않는다", () => {
    그린다("+2.1521%", 2);

    const 흐린것 = screen.getByTestId("값").querySelector("span");
    expect(흐린것?.textContent).toBe("21");
    expect(보이는글자()).toBe("+2.1521%");
  });

  /** 모양을 못 알아보면 손대지 않는다. 지어내는 것보다 그대로 두는 쪽이 언제나 안전하다. */
  it("숫자가 아니면 그대로 둔다", () => {
    그린다("없다");

    expect(보이는글자()).toBe("없다");
  });
});
