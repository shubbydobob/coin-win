import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

const RECONCILIATION: components["schemas"]["PositionReconciliationResponse"] = {
  matches: [
    {
      direction: "SHORT",
      outcome: "EXCHANGE_ONLY",
      discrepancy: true,
      recorded: null,
      actual: {
        symbol: "BTCUSDT",
        entryPrice: 76250.7,
        quantity: 0.032,
        liquidationPrice: 83503.63,
        unrealizedPnl: 1.05,
      },
    },
  ],
  consistent: false,
  observedAt: "2026-08-23T07:06:00Z",
};

const 계좌없음 = {
  title: "외부 데이터를 가져오지 못했다",
  status: 503,
  detail: "거래소 키가 없다",
  instance: "/api/account/positions",
};

/** 폴링 주기. `OverviewScreen` 의 `ACCOUNT_POLL_MS` 와 같아야 한다. */
const 폴링 = 15_000;

const 거래와_집계 = [
  http.get(origin + "/api/trades/active", () => HttpResponse.json([OPEN])),
  http.get(origin + "/api/trades/summary", () => HttpResponse.json(SUMMARY)),
];

describe("현황", () => {
  beforeEach(() => server.use(...거래와_집계));

  /**
   * <b>시장 지표와 집계가 이 화면에서 빠졌다.</b> 여섯 탭을 셋으로 합치면서 걷어낸 자리다 —
   * 세 지표는 같은 탭의 이상치 블록이 평소와 견주어 보여 주므로 이쪽은 덜 아는 사본이었고,
   * 집계는 「매매」의 기록 옆에 있어야 무엇을 고칠지로 이어진다.
   */
  it("지금 열려 있는 것만 놓는다", async () => {
    renderScreen(<OverviewScreen />);

    expect(await screen.findByRole("rowheader", { name: "2026-08-20 10:00" })).toBeVisible();
    expect(screen.queryByText("계획을 어겨서 얻은 것")).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "시장 지표" })).not.toBeInTheDocument();
  });

  it("현황에서는 기록을 고칠 수 없다", async () => {
    server.use(http.get(origin + "/api/markets/BTCUSDT/metrics", () => HttpResponse.json(METRICS)));
    renderScreen(<OverviewScreen />);

    await screen.findByRole("rowheader", { name: "2026-08-20 10:00" });
    // 기록을 고치는 자리는 /journal 이다. 여기서도 되면 같은 일을 두 곳에서 하게 된다.
    expect(screen.queryByRole("button", { name: "청산 기록" })).not.toBeInTheDocument();
  });

  it("거래소 계좌가 안 닿아도 진행 중인 거래는 그대로다", async () => {
    server.use(http.get(origin + "/api/account/positions", () =>
      HttpResponse.json(계좌없음, { status: 503 })));
    renderScreen(<OverviewScreen />);

    expect(await screen.findByText("거래소 키가 없다")).toBeVisible();
    expect(await screen.findByRole("rowheader", { name: "2026-08-20 10:00" })).toBeVisible();
  });

  describe("거래소 계좌", () => {
    /**
     * **폴링을 가짜 시계 위에서 잰다.** 실제로 15초를 기다리면 테스트가 그만큼 느려지고,
     * 주기를 테스트용으로 줄여 두면 정작 화면이 쓰는 값은 검증되지 않는다.
     */
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
    afterEach(() => vi.useRealTimers());

    function 계좌(응답: () => Response) {
      let 물어본횟수 = 0;
      server.use(
        http.get(origin + "/api/markets/BTCUSDT/metrics", () => HttpResponse.json(METRICS)),
        http.get(origin + "/api/account/positions", () => {
          물어본횟수 += 1;
          return 응답();
        }),
      );
      return () => 물어본횟수;
    }

    it("주기가 지나면 저절로 다시 묻는다", async () => {
      const 횟수 = 계좌(() => HttpResponse.json(RECONCILIATION));
      renderScreen(<OverviewScreen />);

      await screen.findByRole("button", { name: "새로고침" });
      expect(횟수()).toBe(1);

      await vi.advanceTimersByTimeAsync(폴링);

      // 미실현 손익과 청산가는 가격이 움직이면 같이 움직인다. 멈춘 수를 띄워 두면
      // 사람이 그것을 현재로 읽는다.
      await waitFor(() => expect(횟수()).toBe(2));
    });

    it("새로고침을 누르면 주기를 기다리지 않고 묻는다", async () => {
      const 횟수 = 계좌(() => HttpResponse.json(RECONCILIATION));
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderScreen(<OverviewScreen />);

      await user.click(await screen.findByRole("button", { name: "새로고침" }));

      await waitFor(() => expect(횟수()).toBe(2));
    });

    it("실패하면 폴링을 멈추고 사람이 다시 켠다", async () => {
      let 실패 = true;
      const 횟수 = 계좌(() =>
        실패 ? HttpResponse.json(계좌없음, { status: 503 }) : HttpResponse.json(RECONCILIATION),
      );
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderScreen(<OverviewScreen />);

      expect(await screen.findByText("거래소 키가 없다")).toBeVisible();

      // 키가 없는 것은 기다린다고 달라지지 않는다. 15초마다 같은 실패를 되풀이하면
      // 로그가 그 실패로 덮인다.
      await vi.advanceTimersByTimeAsync(폴링 * 4);
      expect(횟수()).toBe(1);

      실패 = false;
      await user.click(within(screen.getByRole("region", { name: "거래소 대조" })).getByRole("button", { name: "다시 시도" }));

      expect(await screen.findByRole("button", { name: "새로고침" })).toBeVisible();
    });
  });

});
