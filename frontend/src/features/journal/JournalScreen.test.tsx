import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, delay, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { JournalScreen } from "./JournalScreen";
import type { components } from "../../api/schema";

type Trade = components["schemas"]["TradeResponse"];

type Summary = components["schemas"]["JournalSummaryResponse"];

const CLOSED: Trade = {
  id: "t-1",
  state: "CLOSED",
  plannedAt: "2026-08-01T00:00:00Z",
  plan: {
    direction: "LONG",
    entries: [{ price: 60000, allocation: 100 }],
    stopLoss: 56000,
    takeProfit: 66000,
    leverage: 10,
    riskRewardRatio: 2.33,
    weakRiskReward: false,
  },
  entry: {
    openedAt: "2026-08-02T09:30:00Z",
    fillCount: 1,
    averageEntryPrice: 60000,
    quantity: 0.005,
    priceAtEntry: 60100,
    ichimokuPosition: "ABOVE",
    bollingerPosition: "INSIDE",
    rationale: "지지대 근단 반전",
  },
  outcome: {
    closedAt: "2026-08-03T12:00:00Z",
    exitPrice: 63000,
    exitReason: "PLANNED_TARGET",
    followedPlan: true,
    holdingPeriod: "PT26H30M",
    grossPnl: 15,
    realizedPnl: 14.2,
    lossIfStopHonored: -20,
    costOfDeviation: 0,
  },
};

const SUMMARY: Summary = {
  totalTrades: 3,
  totalRealizedPnl: -12.5,
  planAdherence: 66.6667,
  followed: { trades: 2, realizedPnl: 20, wins: 1, losses: 1, winRate: 50 },
  broken: { trades: 1, realizedPnl: -32.5, wins: 0, losses: 1, winRate: 0 },
  lossIfEveryStopHonored: -20,
  costOfDeviation: -12.5,
  intervals: { gaps: 2, shortest: "PT8H", average: "PT30H", overlaps: 0 },
};

function 응답(trades: Trade[] = [CLOSED], summary: Summary = SUMMARY) {
  return [
    http.get(origin + "/api/trades", () => HttpResponse.json(trades)),
    http.get(origin + "/api/trades/summary", () => HttpResponse.json(summary)),
  ];
}

describe("매매 기록", () => {
  beforeEach(() => server.use(...응답()));

  it("끝난 거래가 목록에 나온다", async () => {
    renderScreen(<JournalScreen />);

    expect(await screen.findByRole("rowheader", { name: "2026-08-02 09:30 UTC" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "계획 익절" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "14.20" })).toBeVisible();
  });

  it("보유 기간은 하루가 넘으면 일로 읽힌다", async () => {
    renderScreen(<JournalScreen />);

    // PT26H30M. 26시간 30분 이라고 쓰면 하루가 넘는지가 즉시 읽히지 않는다.
    expect(await screen.findByText("1일 2시간 30분")).toBeVisible();
  });

  it("집계는 승률이 아니라 계획을 어긴 대가를 앞에 놓는다", async () => {
    renderScreen(<JournalScreen />);

    const 집계 = await screen.findByRole("region", { name: "집계" });

    expect(집계).toHaveTextContent("계획을 어겨서 얻은 것");
    expect(집계).toHaveTextContent("-12.50");
  });

  it("목록과 집계에 같은 조회 조건이 간다", async () => {
    const 물어본것: string[] = [];
    server.use(
      http.get(origin + "/api/trades", ({ request }) => {
        물어본것.push(new URL(request.url).search);
        return HttpResponse.json([CLOSED]);
      }),
      http.get(origin + "/api/trades/summary", ({ request }) => {
        물어본것.push(new URL(request.url).search);
        return HttpResponse.json(SUMMARY);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);
    await screen.findByRole("cell", { name: "계획 익절" });

    물어본것.length = 0;
    await user.selectOptions(screen.getByLabelText("계획 준수"), "false");
    await user.type(screen.getByLabelText("청산 시작일"), "2026-08-01");
    await user.click(screen.getByRole("button", { name: "조회" }));

    await waitFor(() => expect(물어본것).toHaveLength(2));
    // 집계가 목록과 다른 모집단을 보고 있으면 화면이 거짓말을 한다.
    expect(물어본것[0]).toBe(물어본것[1]);
    expect(물어본것[0]).toContain("followedPlan=false");
    expect(물어본것[0]).toContain("closedFrom=2026-08-01T00%3A00%3A00Z");
  });

  it("조건을 걸지 않으면 질의 문자열이 비어 있다", async () => {
    let asked = "?";
    server.use(
      http.get(origin + "/api/trades", ({ request }) => {
        asked = new URL(request.url).search;
        return HttpResponse.json([CLOSED]);
      }),
    );
    renderScreen(<JournalScreen />);

    await screen.findByRole("cell", { name: "계획 익절" });
    expect(asked).toBe("");
  });

  it("조건에 드는 거래가 없으면 빈 표 대신 그렇게 말한다", async () => {
    server.use(...응답([]));
    renderScreen(<JournalScreen />);

    expect(await screen.findByText("조건에 드는 거래가 없다")).toBeVisible();
  });

  it("불러오는 동안 그렇게 말한다", async () => {
    server.use(
      http.get(origin + "/api/trades", async () => {
        await delay(50);
        return HttpResponse.json([CLOSED]);
      }),
    );
    renderScreen(<JournalScreen />);

    expect(screen.getByText("불러오는 중")).toBeVisible();
    expect(await screen.findByRole("cell", { name: "계획 익절" })).toBeVisible();
  });

  it("조회가 실패하면 서버 문장이 그대로 나온다", async () => {
    server.use(
      http.get(origin + "/api/trades", () =>
        HttpResponse.json(
          { title: "값이 유효하지 않다", status: 400, detail: "구간의 끝이 시작보다 앞이다", instance: "/api/trades" },
          { status: 400 },
        ),
      ),
    );
    renderScreen(<JournalScreen />);

    expect(await screen.findByRole("alert")).toHaveTextContent("구간의 끝이 시작보다 앞이다");
  });
});
