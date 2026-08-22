import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, delay, http } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { BacktestScreen } from "./BacktestScreen";
import type { components } from "../../api/schema";

type Result = components["schemas"]["BacktestResultResponse"];

const SUMMARY: components["schemas"]["SummaryResponse"] = {
  totalTrades: 24,
  winRate: 33.3333,
  profitFactor: 0.7,
  netPnl: -80,
  finalEquity: 720,
  maxDrawdown: 12.5,
};

const RESULT: Result = {
  summary: SUMMARY,
  trades: [
    {
      direction: "LONG",
      openedAt: "2026-01-20T04:00:00Z",
      closedAt: "2026-01-21T00:00:00Z",
      averageEntryPrice: 91899.5,
      exitPrice: 90991.43,
      exitReason: "PLANNED_STOP",
      filledEntries: 2,
      realizedPnl: -17.49,
      rationale: "지지대 91800.00~91999.00 (터치 2회) 근단 반전 진입",
    },
  ],
  equityCurve: [800, 782.51],
};

describe("백테스트", () => {
  it("실행하면 요약과 거래 목록과 자산 곡선이 나온다", async () => {
    server.use(http.post(origin + "/api/backtests", () => HttpResponse.json(RESULT)));
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    await user.click(screen.getByRole("button", { name: "실행" }));

    expect(await screen.findByRole("region", { name: "백테스트 결과" })).toBeVisible();
    expect(screen.getByText("거래 1건")).toBeVisible();
    // 요약만 보고 판단하지 않도록 원자료를 함께 낸다. 근거가 왜 그 거래가 섰는지를 말한다.
    expect(screen.getByRole("cell", { name: "지지대 91800.00~91999.00 (터치 2회) 근단 반전 진입" })).toBeVisible();
  });

  it("진 거래가 없는 표본의 손익비는 0 이 아니라 — 로 나온다", async () => {
    server.use(
      http.post(origin + "/api/backtests", () =>
        HttpResponse.json({ ...RESULT, summary: { ...SUMMARY, profitFactor: null } }),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    await user.click(screen.getByRole("button", { name: "실행" }));

    // "손익비가 0" 과 "손익비를 말할 수 없다" 는 다른 사실이다.
    expect(await screen.findByLabelText("성적")).toHaveTextContent("—");
  });

  it("비교는 기준과 변경을 나란히 놓는다", async () => {
    server.use(
      http.post(origin + "/api/backtests/indicator-filter-comparison", () =>
        HttpResponse.json({
          baseline: RESULT,
          variant: { ...RESULT, summary: { ...SUMMARY, totalTrades: 12, profitFactor: 1.14, netPnl: 17 } },
          pnlDifference: 97,
          tradeDifference: -12,
        }),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    await user.click(screen.getByRole("button", { name: "지표 필터 비교" }));

    const 비교 = await screen.findByRole("region", { name: "지표 필터 비교" });
    expect(비교).toHaveTextContent("97.00");
    expect(비교).toHaveTextContent("-12");
    expect(screen.getByLabelText("기준")).toBeVisible();
    expect(screen.getByLabelText("변경")).toBeVisible();
  });

  it("캔들 동기화가 이 화면에 있다", async () => {
    let 물어본것 = "";
    server.use(
      http.post(origin + "/api/markets/BTCUSDT/candles/sync", ({ request }) => {
        물어본것 = new URL(request.url).search;
        return HttpResponse.json({ symbol: "BTCUSDT", interval: "4h", newlyStored: 1500 });
      }),
    );
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    // 캔들을 넣는 길이 화면에 없으면 브라우저만으로는 백테스트를 한 번도 돌릴 수 없다.
    await user.click(screen.getByRole("button", { name: "캔들 동기화" }));

    expect(await screen.findByText("새로 저장 1500개")).toBeVisible();
    expect(물어본것).toContain("interval=4h");
  });

  it("돌리는 동안 버튼이 비활성이다", async () => {
    server.use(
      http.post(origin + "/api/backtests", async () => {
        await delay(50);
        return HttpResponse.json(RESULT);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    await user.click(screen.getByRole("button", { name: "실행" }));

    expect(screen.getByRole("button", { name: "돌리는 중" })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("button", { name: "실행" })).toBeEnabled());
  });

  it("422 응답의 detail 문장이 그대로 나온다", async () => {
    server.use(
      http.post(origin + "/api/backtests", () =>
        HttpResponse.json(
          { title: "도메인 규칙 위반", status: 422, detail: "워밍업을 채우지 못하는 구간이다", instance: "/api/backtests" },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<BacktestScreen />);

    await user.click(screen.getByRole("button", { name: "실행" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("워밍업을 채우지 못하는 구간이다");
  });
});
