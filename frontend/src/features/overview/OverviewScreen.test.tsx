import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { OverviewScreen } from "./OverviewScreen";
import type { components } from "../../api/schema";

type Trade = components["schemas"]["TradeResponse"];

const OPEN: Trade = {
  id: "t-9",
  state: "OPEN",
  plannedAt: "2026-08-20T01:00:00Z",
  plan: {
    direction: "LONG",
    entries: [{ price: 60000, allocation: 100 }],
    stopLoss: 56000,
    takeProfit: 66000,
    leverage: 10,
    riskRewardRatio: 1.5,
    weakRiskReward: false,
  },
  entry: {
    openedAt: "2026-08-20T02:00:00Z",
    fillCount: 1,
    averageEntryPrice: 60050,
    quantity: 0.005,
    priceAtEntry: 60050,
    ichimokuPosition: "ABOVE",
    bollingerPosition: "INSIDE",
    rationale: "지지대 근단",
  },
  outcome: null,
};

const SUMMARY: components["schemas"]["JournalSummaryResponse"] = {
  totalTrades: 3,
  totalRealizedPnl: -12.5,
  planAdherence: 66.6667,
  followed: { trades: 2, realizedPnl: 20, wins: 1, losses: 1, winRate: 50 },
  broken: { trades: 1, realizedPnl: -32.5, wins: 0, losses: 1, winRate: 0 },
  lossIfEveryStopHonored: -20,
  costOfDeviation: -12.5,
  intervals: { gaps: 2, shortest: "PT8H", average: "PT30H", overlaps: 0 },
};

const METRICS: components["schemas"]["MarketMetricsResponse"] = {
  symbol: "BTCUSDT",
  at: "2026-08-22T12:00:00Z",
  fundingRatePercent: 0.0084,
  openInterest: 82345.5,
  longShortRatio: 1.24,
};

const 거래와_집계 = [
  http.get(origin + "/api/trades/active", () => HttpResponse.json([OPEN])),
  http.get(origin + "/api/trades/summary", () => HttpResponse.json(SUMMARY)),
];

describe("현황", () => {
  beforeEach(() => server.use(...거래와_집계));

  it("지금 열려 있는 것과 집계와 시장 지표를 놓는다", async () => {
    server.use(http.get(origin + "/api/markets/BTCUSDT/metrics", () => HttpResponse.json(METRICS)));
    renderScreen(<OverviewScreen />);

    expect(await screen.findByRole("rowheader", { name: "2026-08-20 01:00 UTC" })).toBeVisible();
    expect(await screen.findByText("0.0084%")).toBeVisible();
    expect(await screen.findByText("계획을 어겨서 얻은 것")).toBeVisible();
  });

  it("현황에서는 기록을 고칠 수 없다", async () => {
    server.use(http.get(origin + "/api/markets/BTCUSDT/metrics", () => HttpResponse.json(METRICS)));
    renderScreen(<OverviewScreen />);

    await screen.findByRole("rowheader", { name: "2026-08-20 01:00 UTC" });
    // 기록을 고치는 자리는 /journal 이다. 여기서도 되면 같은 일을 두 곳에서 하게 된다.
    expect(screen.queryByRole("button", { name: "청산 기록" })).not.toBeInTheDocument();
  });

  it("거래소가 안 닿아도 그 블록만 죽고 나머지는 그대로다", async () => {
    server.use(
      http.get(origin + "/api/markets/BTCUSDT/metrics", () =>
        HttpResponse.json(
          {
            title: "외부 데이터를 가져오지 못했다",
            status: 503,
            detail: "거래소에 닿지 못했다",
            instance: "/api/markets/BTCUSDT/metrics",
          },
          { status: 503 },
        ),
      ),
    );
    renderScreen(<OverviewScreen />);

    expect(await screen.findByText("거래소에 닿지 못했다")).toBeVisible();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeVisible();
    // 나머지 블록은 그대로 보인다.
    expect(await screen.findByRole("rowheader", { name: "2026-08-20 01:00 UTC" })).toBeVisible();
    expect(await screen.findByText("계획을 어겨서 얻은 것")).toBeVisible();
  });

  it("다시 시도를 누르면 다시 묻는다", async () => {
    let 시도 = 0;
    server.use(
      http.get(origin + "/api/markets/BTCUSDT/metrics", () => {
        시도 += 1;
        return 시도 === 1
          ? HttpResponse.json({ title: "외부 데이터를 가져오지 못했다", status: 503, detail: "거래소에 닿지 못했다", instance: "/x" }, { status: 503 })
          : HttpResponse.json(METRICS);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<OverviewScreen />);

    await user.click(await screen.findByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("0.0084%")).toBeVisible();
  });
});
