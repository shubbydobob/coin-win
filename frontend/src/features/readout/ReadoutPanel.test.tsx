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
type 위치값 = Readout["ichimoku"]["position"];

function 판독(덮어쓸것: Partial<Readout> = {}): Readout {
  return {
    interval: "15m",
    at: "2026-08-25T01:15:00Z",
    close: 78803.1,
    atr: 492.08,
    ichimoku: {
      position: "ABOVE",
      conversionLine: 79366.55,
      baseLine: 78704.8,
      cloudTop: 77420.55,
      cloudBottom: 77332.8,
      bullishCloud: true,
      cloudThickness: 0.18,
      conversionGap: 1.34,
      baseLineGap: 0.2,
      laggingSpanGap: 0.85,
    },
    bollinger: {
      position: "INSIDE",
      upper: 79969.29,
      middle: 79144.86,
      lower: 78320.44,
      bandWidthPercent: 2.0833,
      ratio: 0.2929,
      bandWidthRank: 18.5053,
      bandWalk: 0,
    },
    rsi: { value: 62.41, change3: -4.12 },
    macd: { histogram: 0.31, change: -0.04, aboveZero: true, barsSinceCross: 7 },
    movingAverage: { spread: 2.4 },
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

/** 위치만 바꿔 끼운다. 나머지 값은 이 테스트가 묻지 않는 것이라 기본 픽스처 그대로다. */
function 위치(readout: Readout, 구름: 위치값, 밴드: 위치값): Readout {
  return {
    ...readout,
    ichimoku: { ...readout.ichimoku, position: 구름 },
    bollinger: { ...readout.bollinger, position: 밴드 },
  };
}

const 셋 = [
  판독(),
  위치(판독({ interval: "1h" }), "BELOW", "ABOVE"),
  위치(판독({ interval: "4h" }), "INSIDE", "BELOW"),
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

  /**
   * <b>위치 딱지가 접어 버리던 것들.</b> 「구름 위」 는 아슬아슬하게 위인지 한참 위인지를,
   * 「밴드 안」 은 하단에 붙어 있는 것과 상단 바로 아래인 것을 같은 사실로 만든다.
   * 근거는 <code>docs/spec/indicator-usage.md</code> § 4.
   */
  it("구름과 밴드의 값을 ATR 배수로 함께 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} symbol="BTCUSDT" />);

    expect(screen.getByText("구름 두께")).toBeVisible();
    expect(screen.getByText("0.18 ATR")).toBeVisible();
    expect(screen.getByText("1.34 ATR")).toBeVisible();
    expect(screen.getByText("0.20 ATR")).toBeVisible();
    expect(screen.getByText("0.2929")).toBeVisible();
  });

  /**
   * <b>말할 수 없는 것을 빈칸으로 두지 않는다.</b> 밴드 폭이 0 이면 「밴드 안 어디」 가
   * 성립하지 않는데, 빈칸이면 그것이 0(하단에 붙어 있다)으로 읽힌다.
   */
  it("밴드 안 위치가 없으면 없다고 적는다", () => {
    const 없는것 = 판독();
    render(
      <ReadoutPanel
        readouts={[{ ...없는것, bollinger: { ...없는것.bollinger, ratio: null } }]}
        symbol="BTCUSDT"
      />,
    );

    expect(screen.getByText("밴드 안 어디").nextElementSibling).toHaveTextContent("—");
  });

  /**
   * <b>한 봉만 봐서는 나오지 않는 것들.</b> 밴드폭이 창 안에서 몇 번째인지, 밖에서 몇 봉째
   * 걷고 있는지, 시그널 위에 선 지 얼마나 됐는지 — 전부 딱지에서 통째로 빠져 있던 값이다.
   */
  it("이력이 있어야 나오는 값들을 함께 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} symbol="BTCUSDT" />);

    expect(screen.getByText("밴드폭 순위").nextElementSibling).toHaveTextContent("18.5053%");
    expect(screen.getByText("밴드 밖 연속").nextElementSibling).toHaveTextContent("0봉");
    expect(screen.getByText("후행스팬").nextElementSibling).toHaveTextContent("0.85 ATR");
    expect(screen.getByText("RSI").nextElementSibling).toHaveTextContent("62.4100%");
    expect(screen.getByText("RSI 3봉").nextElementSibling).toHaveTextContent("-4.1200%p");
    expect(screen.getByText("MACD 이어진 봉").nextElementSibling).toHaveTextContent("7봉");
    expect(screen.getByText("20−200").nextElementSibling).toHaveTextContent("2.40 ATR");
  });

  /** 200 이동평균이 없는 주기가 실제로 있다. 0 으로 적으면 두 선이 붙어 있다는 뜻이 된다. */
  it("이동평균 간격이 없으면 없다고 적는다", () => {
    const 없는것 = 판독();
    render(
      <ReadoutPanel
        readouts={[{ ...없는것, movingAverage: { spread: null } }]}
        symbol="BTCUSDT"
      />,
    );

    expect(screen.getByText("20−200").nextElementSibling).toHaveTextContent("—");
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
