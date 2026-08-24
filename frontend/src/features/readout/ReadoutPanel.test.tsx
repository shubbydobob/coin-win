import { render, screen, within } from "@testing-library/react";
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
    resistance: null,
    ...덮어쓸것,
  };
}

describe("지표 판독", () => {
  /**
   * <b>세 주기가 세로로 읽혀야 한다.</b> 이 화면이 답하는 질문은 "15분은 어떤가" 가 아니라
   * "세 주기가 같은 말을 하는가" 다. 주기 이름을 행 머리로 두는 이유가 그것이다.
   */
  it("주기마다 한 줄씩 놓는다", () => {
    render(<ReadoutPanel readouts={[
      판독({ interval: "15m" }),
      판독({ interval: "1h" }),
      판독({ interval: "4h" }),
    ]} />);

    ["15분", "1시간", "4시간"].forEach((이름) => {
      expect(screen.getByRole("rowheader", { name: new RegExp(이름) })).toBeVisible();
    });
  });

  /** 위치는 요약이고 선은 근거다. 구름 위라는 것만으로는 아슬아슬한지 한참인지 알 수 없다. */
  it("위치와 함께 그 근거가 되는 선 값을 적는다", () => {
    render(<ReadoutPanel readouts={[판독()]} />);

    const 행 = screen.getByRole("row", { name: /15분/ });
    expect(within(행).getByText("구름 위")).toBeVisible();
    expect(within(행).getByText(/77,332\.80 ~ 77,420\.55/)).toBeVisible();
    expect(within(행).getByText("밴드 안")).toBeVisible();
  });

  /**
   * <b>대가 없는 것과 대가 0 인 것은 다르다.</b> 위쪽에 사람이 반응한 적 있는 자리가 아직
   * 없다는 사실을 0 이나 화면 끝 값으로 채우면 없는 대가 생긴다.
   */
  it("저항이 없으면 없다고 적는다", () => {
    render(<ReadoutPanel readouts={[판독({ resistance: null })]} />);

    const 행 = screen.getByRole("row", { name: /15분/ });
    expect(within(행).getByText("없다")).toBeVisible();
    expect(within(행).queryByText("0.00")).not.toBeInTheDocument();
  });

  /** 지지는 아래, 저항은 위. 부호가 없으면 두 칸을 눈으로 비교할 수 없다. */
  it("지지는 아래쪽 거리로 저항은 위쪽 거리로 적는다", () => {
    render(<ReadoutPanel readouts={[판독({
      resistance: { near: 80500, far: 80900, touches: 4, distancePercent: 2.1521 },
    })]} />);

    const 행 = screen.getByRole("row", { name: /15분/ });
    expect(within(행).getByText(/−1\.2691%/)).toBeVisible();
    expect(within(행).getByText(/\+2\.1521%/)).toBeVisible();
  });

  /**
   * <b>어떤 문장도 방향을 지시하지 않는다.</b> 감시 화면의 상황 문장이 지키는 것과 같은
   * 경계이고, 이쪽은 그 유혹이 더 크다 — 지표는 곧바로 매매 신호처럼 읽히기 때문이다.
   */
  it("무엇을 하라고 말하지 않는다", () => {
    render(<ReadoutPanel readouts={[판독(), 판독({ interval: "1h", ichimoku: "BELOW" })]} />);

    expect(screen.getByRole("region", { name: "지표 판독" }).textContent)
      .not.toMatch(/유리|불리|추천|신호|기회|노려|잡아|들어가|진입하|매수하|매도하|사라|팔아/);
  });
});
