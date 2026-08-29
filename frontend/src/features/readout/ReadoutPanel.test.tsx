import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ReadoutPanel } from "./ReadoutPanel";
import type { components } from "../../api/schema";

/**
 * <b>차트는 흉내로 바꿔 둔다.</b> 캔들은 캔버스에 그려지므로 jsdom 에서 아무것도 조회할 수
 * 없다 — 그림의 내용은 이 그물에 걸리지 않는다. 그것을 인정하고 <b>차트 바깥</b>을 검사한다:
 * 세 주기 요약, 어느 주기를 고르고 있나, 없는 값을 없다고 적는가.
 *
 * <b>이 파일이 초록인 것이 차트가 맞다는 뜻이 아니다.</b> 이 저장소는 화면 테스트로 거짓
 * 초록을 두 번 잡았고, 차트는 그때보다 더 안 보이는 자리다. 실제 확인은 사람이 띄워 보는
 * 것뿐이다.
 */
vi.mock("./PriceChart", () => ({
  PriceChart: ({ readout }: { readout: { interval: string } }) => (
    <div data-testid="chart">{readout.interval} 차트 자리</div>
  ),
}));

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
        { ratio: 0.618, price: 79069.3 },
        { ratio: 0.65, price: 79049.03 },
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

const 셋 = [
  판독(),
  판독({ interval: "1h", ichimoku: "BELOW", bollinger: "ABOVE" }),
  판독({ interval: "4h", ichimoku: "INSIDE", bollinger: "BELOW" }),
];

describe("지표 판독", () => {
  /**
   * <b>세 주기를 한눈에 쌓는다.</b> 이 화면이 답하는 질문은 "15분은 어떤가" 가 아니라
   * "세 주기가 같은 말을 하는가" 다. 차트가 한 번에 하나만 보여 주므로 요약은 셋 다 있어야 한다.
   */
  it("세 주기 요약을 함께 놓는다", () => {
    render(<ReadoutPanel readouts={셋} symbol="BTCUSDT" />);

    ["15분", "1시간", "4시간"].forEach((이름) => {
      expect(screen.getByRole("button", { name: new RegExp(이름) })).toBeVisible();
    });
    expect(screen.getByText("구름 위")).toBeVisible();
    expect(screen.getByText("구름 아래")).toBeVisible();
    expect(screen.getByText("구름 안")).toBeVisible();
  });

  /** 처음에는 가장 짧은 주기를 연다 — 진입 자리는 15분에서 본다. */
  it("처음에는 첫 주기를 고른 상태다", () => {
    render(<ReadoutPanel readouts={셋} symbol="BTCUSDT" />);

    expect(screen.getByRole("button", { name: /15분/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("chart")).toHaveTextContent("15m 차트 자리");
  });

  it("주기를 고르면 그 주기의 차트로 바뀐다", async () => {
    const user = userEvent.setup();
    render(<ReadoutPanel readouts={셋} symbol="BTCUSDT" />);

    await user.click(screen.getByRole("button", { name: /4시간/ }));

    expect(screen.getByRole("button", { name: /4시간/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("chart")).toHaveTextContent("4h 차트 자리");
  });

  /**
   * <b>없는 것과 0 은 다른 사실이다.</b> 빈 자리로 두면 "0% 떨어져 있다" 로 읽히고, 그것은
   * 지금 그 자리에 있다는 뜻이 된다.
   */
  it("없는 대는 없다고 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} symbol="BTCUSDT" />);

    expect(screen.getByText(/지지 1\.2691%/)).toBeVisible();
    expect(screen.getByText(/저항 없다/)).toBeVisible();
  });

  it("매물대 중심을 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} symbol="BTCUSDT" />);

    expect(screen.getByText(/매물대 중심 79,200/)).toBeVisible();
  });

  /** 읽는 법은 한 번 읽으면 되는 것이다. 펼쳐 두면 매일 보는 값들이 그 글에 밀린다. */
  it("읽는 법을 접어 둔다", () => {
    render(<ReadoutPanel readouts={셋} symbol="BTCUSDT" />);

    expect(screen.getByText(/가로선은 전부/)).not.toBeVisible();
    expect(screen.getByText("차트 읽는 법")).toBeVisible();
  });

  /**
   * 서버의 <code>어떤_문장도_행동을_지시하지_않는다</code> 와 짝이다. 구름 위라는 것은
   * 사실이고 "그러니 롱" 은 예측이며, 이 저장소는 그 종류의 전제를 7년 15,110봉에서
   * 반증했다(<code>docs/adr/021</code>).
   */
  it("무엇을 하라고 말하지 않는다", () => {
    const { container } = render(<ReadoutPanel readouts={셋} symbol="BTCUSDT" />);

    expect(container.textContent).not.toMatch(/유리|불리|추천|노려|잡아|들어가|진입하|매수하|매도하/);
  });
});
