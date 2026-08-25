import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ReadoutPanel } from "./ReadoutPanel";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];

function 판독(덮어쓸것: Partial<Readout> = {}): Readout {
  return {
    interval: "15m",
    at: "2026-08-25T01:15:00Z",
    close: 78803.1,
    atr: 492.08,
    ichimoku: "ABOVE",
    conversionLine: 79366.55,
    baseLine: 78704.8,
    cloudTop: 77420.55,
    cloudBottom: 77332.8,
    bollinger: "INSIDE",
    bollingerUpper: 79969.29,
    bollingerMiddle: 79144.86,
    bollingerLower: 78320.44,
    bandWidthPercent: 2.0833,
    support: { near: 77803, far: 77650, touches: 26, distancePercent: 1.2691 },
    fibonacci: {
      low: 64000,
      high: 79900,
      upward: true,
      levels: [
        { ratio: 0.236, price: 76147.6 },
        { ratio: 0.382, price: 73826.2 },
        { ratio: 0.5, price: 71950 },
        { ratio: 0.618, price: 79069.3 },
        { ratio: 0.65, price: 79049.03 },
        { ratio: 0.786, price: 67400.6 },
      ],
      inGoldenPocket: false,
    },
    resistance: null,
    volume: {
      pointOfControl: 79200,
      below: { near: 77650, far: 77200, sharePercent: 9.24, distancePercent: 1.4632 },
      above: { near: 79900, far: 80350, sharePercent: 7.11, distancePercent: 1.3919 },
      here: null,
    },
    ...덮어쓸것,
  };
}

/**
 * 띠가 말하는 것.
 *
 * <b>그림의 좌표가 아니라 사실을 검사한다.</b> 막대의 `left: 63%` 를 단언하면 여백을 손보는
 * 날마다 테스트가 깨지고, 그렇다고 그 수가 맞는지도 증명하지 못한다. 띠는 값을
 * `aria-label` 에 그대로 싣고 있으므로 <b>화면이 말하는 사실과 검사하는 사실이 같은 문장</b>이다.
 */
function 띠(주기: string): string {
  return screen.getByRole("img", { name: new RegExp(주기) }).getAttribute("aria-label") ?? "";
}

describe("지표 판독", () => {
  /**
   * <b>세 주기가 세로로 읽혀야 한다.</b> 이 화면이 답하는 질문은 "15분은 어떤가" 가 아니라
   * "세 주기가 같은 말을 하는가" 다. 세 띠의 지금 선이 세로로 정렬돼 있어야 그 비교가
   * 눈에서 일어난다.
   */
  it("주기마다 띠를 하나씩 놓는다", () => {
    render(
      <ReadoutPanel
        readouts={[판독({ interval: "15m" }), 판독({ interval: "1h" }), 판독({ interval: "4h" })]}
      />,
    );

    ["15분", "1시간", "4시간"].forEach((이름) => {
      expect(screen.getByText(이름)).toBeVisible();
    });
    expect(screen.getAllByRole("img")).toHaveLength(3);
  });

  /** 위치는 요약이고 값은 띠가 갖는다. 구름 위라는 것만으로는 아슬아슬한지 한참인지 모른다. */
  it("위치는 글로, 값은 띠로 낸다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    expect(screen.getByText("구름 위")).toBeVisible();
    expect(screen.getByText("밴드 안")).toBeVisible();
    expect(띠("15분")).toMatch(/지금 78,803\.10/);
    expect(띠("15분")).toMatch(/ATR 492\.08/);
  });

  /**
   * <b>대가 없는 것과 대가 0 인 것은 다르다.</b> 위쪽에 사람이 반응한 적 있는 자리가 아직
   * 없다는 사실을 0 이나 창 끝 값으로 채우면 없는 대가 생긴다.
   */
  it("저항이 없으면 아무것도 그리지 않는다", () => {
    render(<ReadoutPanel readouts={[판독({ resistance: null })]} />);

    expect(띠("15분")).not.toMatch(/저항/);
    // 지지는 그대로 있어야 한다. "저항이 없다" 가 "아무것도 없다" 로 번지면 안 된다.
    expect(띠("15분")).toMatch(/지지 77,803\.00/);
  });

  it("지지와 저항을 방향과 함께 적는다", () => {
    render(
      <ReadoutPanel
        readouts={[
          판독({ resistance: { near: 80500, far: 80900, touches: 4, distancePercent: 2.1521 } }),
        ]}
      />,
    );

    expect(띠("15분")).toMatch(/지지 77,803\.00 1\.2691% 아래/);
    expect(띠("15분")).toMatch(/저항 80,500\.00 2\.1521% 위/);
  });

  /**
   * <b>매물대는 대와 다른 것을 잰다.</b> 위아래를 함께 적는 이유는 이 값의 쓸모가 "이쪽으로
   * 가려면 무엇을 지나야 하나" 이기 때문이다 — 한쪽만 적으면 반쪽이 된다.
   */
  it("매물대를 위아래로 함께 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    expect(띠("15분")).toMatch(/아래 77,650\.00 두께 9\.2400%/);
    expect(띠("15분")).toMatch(/위 79,900\.00 두께 7\.1100%/);
  });

  /** POC 는 매물대의 일부다. 가장 두껍게 거래된 한 자리는 주기마다 하나뿐이다. */
  it("POC 를 함께 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    expect(띠("15분")).toMatch(/POC 79,200\.00/);
  });

  /**
   * <b>매물대가 없는 것과 매물대 한가운데에 있는 것은 정반대다.</b> 위아래만 보면 둘이 같은
   * 화면이 되는데, 뒤쪽은 어느 쪽으로 움직이든 물린 물량을 지나야 한다는 뜻이다.
   */
  it("매물대 안에 있으면 그렇게 적는다", () => {
    render(
      <ReadoutPanel
        readouts={[
          판독({
            volume: {
              pointOfControl: 79200,
              below: null,
              above: null,
              here: { near: 78600, far: 79100, sharePercent: 12.4, distancePercent: 0.2 },
            },
          }),
        ]}
      />,
    );

    expect(띠("15분")).toMatch(/매물대 78,600\.00~79,100\.00 두께 12\.4000% 지금 이 안/);
  });

  /** 두꺼운 칸이 하나도 없으면 매물대가 없는 것이고, 그것도 사실이다. */
  it("두꺼운 구간이 없으면 없다고 적는다", () => {
    render(
      <ReadoutPanel
        readouts={[
          판독({ volume: { pointOfControl: 79200, below: null, above: null, here: null } }),
        ]}
      />,
    );

    expect(띠("15분")).toMatch(/매물대 없음/);
  });

  /**
   * <b>골든 포켓도 낮은 값부터 적는다.</b> 서버는 비율 순서(0.618 → 0.65)로 주는데 오른
   * 스윙의 되돌림은 고점에서 아래로 재므로 0.65 가 더 낮은 가격이다. 그대로 이어 붙이면
   * 오른 스윙과 내린 스윙이 서로 다른 규칙으로 적힌다.
   */
  it("골든 포켓을 스윙 방향과 무관하게 낮은 값부터 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    expect(띠("15분")).toMatch(/골든 포켓 79,049\.03~79,069\.30/);
  });

  /**
   * <b>화면에서 숫자를 덜어낸 것이지 사실을 덜어낸 것이 아니다.</b> 띠에는 점선만 그리므로
   * 값이 `aria-label` 에도 없으면 손절을 그 경계에 두려는 사람이 화면에서 읽을 수 없다.
   */
  it("스윙이 없으면 골든 포켓도 없다고 적는다", () => {
    render(<ReadoutPanel readouts={[판독({ fibonacci: null })]} />);

    expect(띠("15분")).toMatch(/골든 포켓 없음/);
  });

  /**
   * <b>띠 아래 한 줄이 그 띠를 읽어 준다.</b> 그림만으로는 "가까운가" 가 눈대중이 되고,
   * 눈대중은 주기마다 창 폭이 달라 서로 견줄 수 없다.
   */
  it("띠를 거리로 한 줄 읽어 준다", () => {
    render(
      <ReadoutPanel
        readouts={[
          판독({ resistance: { near: 80500, far: 80900, touches: 4, distancePercent: 2.1521 } }),
        ]}
      />,
    );

    expect(screen.getByText(/아래 지지 1\.2691% · 매물대 1\.4632%/)).toBeVisible();
    expect(screen.getByText(/위 저항 2\.1521% · 매물대 1\.3919%/)).toBeVisible();
  });

  /** 그 방향에 아무것도 없다는 것도 사실이다. 빈 자리로 두면 무슨 뜻인지 알 수 없다. */
  it("없는 쪽은 없다고 읽어 준다", () => {
    render(
      <ReadoutPanel
        readouts={[
          판독({
            resistance: null,
            volume: { pointOfControl: 79200, below: null, above: null, here: null },
          }),
        ]}
      />,
    );

    expect(screen.getByText("위 없음")).toBeVisible();
  });

  /**
   * <b>글의 방향과 띠의 방향이 어긋나면 안 된다.</b> 처음에는 매물대를 아래·위 중 하나만
   * 골라 적었고, 그래서 띠에는 오른쪽(위)에 칠해져 있는데 글은 "아래" 라고 말하는 화면이
   * 나왔다 — 둘 다 사실인데 글이 눈에 안 보이는 쪽을 골랐다.
   */
  it("매물대가 위아래 모두 있으면 양쪽에 다 적는다", () => {
    render(<ReadoutPanel readouts={[판독({ resistance: null })]} />);

    expect(screen.getByText(/아래 지지 1\.2691% · 매물대 1\.4632%/)).toBeVisible();
    expect(screen.getByText(/위 매물대 1\.3919%/)).toBeVisible();
  });

  /**
   * <b>읽는 법은 접혀 있다.</b> 펼쳐 두면 화면에서 가장 긴 덩어리가 되고, 그러면 매일 보는
   * 값들이 한 번 읽고 마는 글에 밀린다.
   */
  it("읽는 법을 접어 둔다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    expect(screen.getByText("띠 읽는 법")).toBeVisible();
    expect(screen.getByText("띠 읽는 법").closest("details")).not.toHaveAttribute("open");
  });

  /**
   * <b>어떤 문장도 방향을 지시하지 않는다.</b> 감시 화면의 상황 문장이 지키는 것과 같은
   * 경계이고, 이쪽은 그 유혹이 더 크다 — 지표는 곧바로 매매 신호처럼 읽히기 때문이다.
   */
  it("무엇을 하라고 말하지 않는다", () => {
    render(<ReadoutPanel readouts={[판독(), 판독({ interval: "1h", ichimoku: "BELOW" })]} />);

    expect(screen.getByRole("region", { name: "지표 판독" }).textContent).not.toMatch(
      /유리|불리|추천|신호|기회|노려|잡아|들어가|진입하|매수하|매도하|사라|팔아/,
    );
  });
});
