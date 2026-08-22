import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, delay, http } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { ProjectionScreen } from "./ProjectionScreen";
import type { components } from "../../api/schema";

const CURVE: components["schemas"]["EquityCurveResponse"] = {
  equity: [800, 816, 784, 830.4],
  trades: 3,
  finalEquity: 830.4,
  maxDrawdown: 3.9216,
};

const DISTRIBUTION: components["schemas"]["MonteCarloResponse"] = {
  runs: 1000,
  tradesPerRun: 156,
  expectancyPerTrade: 0.35,
  worstEquity: 210.5,
  percentile5Equity: 480.25,
  medianEquity: 1240.75,
  percentile95Equity: 4820.5,
  bestEquity: 9100,
  medianMaxDrawdown: 18.5,
  worstMaxDrawdown: 62.25,
  lossProbability: 12.5,
};

describe("복리 · 몬테카를로", () => {
  it("표본 경로는 시드 하나가 만든 곡선을 낸다", async () => {
    server.use(http.post(origin + "/api/projections/equity-curve", () => HttpResponse.json(CURVE)));
    const user = userEvent.setup();
    renderScreen(<ProjectionScreen />);

    await user.click(screen.getByRole("button", { name: "표본 경로" }));

    const 결과 = await screen.findByRole("region", { name: "표본 경로" });
    expect(결과).toHaveTextContent("830.40");
    expect(결과).toHaveTextContent("3.9216%");
  });

  it("분포는 다섯 점을 값으로 낸다", async () => {
    server.use(http.post(origin + "/api/projections/monte-carlo", () => HttpResponse.json(DISTRIBUTION)));
    const user = userEvent.setup();
    renderScreen(<ProjectionScreen />);

    await user.click(screen.getByRole("button", { name: "분포" }));

    const 결과 = await screen.findByRole("region", { name: "몬테카를로 결과" });
    // 없는 분포 모양을 그리지 않는다. 아는 것은 다섯 지점의 값뿐이다.
    for (const 값 of ["210.50", "480.25", "1,240.75", "4,820.50", "9,100.00"]) {
      expect(결과).toHaveTextContent(값);
    }
  });

  it("답하는 질문은 얼마 버나가 아니라 운이 나쁘면 어디까지 가나다", async () => {
    server.use(http.post(origin + "/api/projections/monte-carlo", () => HttpResponse.json(DISTRIBUTION)));
    const user = userEvent.setup();
    renderScreen(<ProjectionScreen />);

    await user.click(screen.getByRole("button", { name: "분포" }));

    expect(await screen.findByText("운이 나쁘면 (하위 5%)")).toBeVisible();
    expect(screen.getByText("가장 깊었던 낙폭")).toBeVisible();
    expect(screen.getByText("62.2500%")).toBeVisible();
  });

  it("돌리는 동안 버튼이 비활성이다", async () => {
    server.use(
      http.post(origin + "/api/projections/monte-carlo", async () => {
        await delay(50);
        return HttpResponse.json(DISTRIBUTION);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<ProjectionScreen />);

    await user.click(screen.getByRole("button", { name: "분포" }));

    expect(screen.getByRole("button", { name: "돌리는 중" })).toBeDisabled();
    expect(await screen.findByRole("region", { name: "몬테카를로 결과" })).toBeVisible();
  });

  it("422 응답의 detail 문장이 그대로 나온다", async () => {
    server.use(
      http.post(origin + "/api/projections/monte-carlo", () =>
        HttpResponse.json(
          {
            title: "도메인 규칙 위반",
            status: 422,
            detail: "총 거래 수 상한을 넘었다",
            instance: "/api/projections/monte-carlo",
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<ProjectionScreen />);

    await user.click(screen.getByRole("button", { name: "분포" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("총 거래 수 상한을 넘었다");
  });
});
