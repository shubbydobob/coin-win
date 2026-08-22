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

const PLANNED: Trade = { ...CLOSED, id: "t-2", state: "PLANNED", entry: null, outcome: null };

const OPEN: Trade = { ...CLOSED, id: "t-3", state: "OPEN", outcome: null };

function 응답(trades: Trade[] = [CLOSED], summary: Summary = SUMMARY, active: Trade[] = []) {
  return [
    http.get(origin + "/api/trades/active", () => HttpResponse.json(active)),
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

  it("PLANNED 거래에는 체결 기록만, OPEN 거래에는 청산 기록만 나온다", async () => {
    server.use(...응답([], SUMMARY, [PLANNED, OPEN]));
    renderScreen(<JournalScreen />);

    expect(await screen.findByRole("button", { name: "체결 기록" })).toBeVisible();
    expect(screen.getByRole("button", { name: "청산 기록" })).toBeVisible();
    // 각 상태의 다음 동작은 정확히 하나다. 둘을 함께 내면 서버가 거절할 일을 화면이 권한다.
    expect(screen.getAllByRole("button", { name: "체결 기록" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "청산 기록" })).toHaveLength(1);
  });

  it("끝난 거래는 진행 중 목록에 있어도 다음 동작이 없다", async () => {
    server.use(...응답([], SUMMARY, [CLOSED]));
    renderScreen(<JournalScreen />);

    await screen.findByRole("rowheader", { name: "2026-08-01 00:00 UTC" });
    expect(screen.queryByRole("button", { name: "체결 기록" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "청산 기록" })).not.toBeInTheDocument();
  });

  it("entry 가 차 있어도 상태가 PLANNED 면 체결 기록이 나온다", async () => {
    // 상태는 state 로 판정한다. entry 가 null 인지로 유추하면 백엔드가 상태를 늘릴 때
    // 화면이 조용히 틀린다.
    server.use(...응답([], SUMMARY, [{ ...PLANNED, entry: CLOSED.entry }]));
    renderScreen(<JournalScreen />);

    expect(await screen.findByRole("button", { name: "체결 기록" })).toBeVisible();
  });

  it("청산 폼에는 손익 입력란이 없다", async () => {
    server.use(...응답([], SUMMARY, [OPEN]));
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);

    await user.click(await screen.findByRole("button", { name: "청산 기록" }));

    // 손익은 도메인이 체결 내역에서 계산한다. 칸을 두면 체결 내역과 손익이 어긋나도 모른다.
    expect(screen.queryByLabelText(/손익/)).not.toBeInTheDocument();
    expect(screen.getByLabelText("수수료")).toBeVisible();
    expect(screen.getByLabelText("펀딩비")).toBeVisible();
  });

  it("청산을 기록하면 목록과 집계를 다시 불러온다", async () => {
    let 청산본문: Record<string, unknown> = {};
    let 목록조회 = 0;
    // 먼저 세운 핸들러가 이긴다. 세는 핸들러를 앞에 둬야 응답() 의 것이 가려진다.
    server.use(
      http.get(origin + "/api/trades", () => {
        목록조회 += 1;
        return HttpResponse.json([]);
      }),
      ...응답([], SUMMARY, [OPEN]),
      http.post(origin + "/api/trades/t-3/closure", async ({ request }) => {
        청산본문 = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...CLOSED, id: "t-3" });
      }),
    );
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);

    await user.click(await screen.findByRole("button", { name: "청산 기록" }));
    await user.type(screen.getByLabelText("청산가"), "63000");
    await user.type(screen.getByLabelText("청산 시각 (UTC)"), "2026-08-03T12:00");
    await user.type(screen.getByLabelText("수수료"), "0.8");
    await user.type(screen.getByLabelText("펀딩비"), "0");
    const 이전 = 목록조회;
    await user.click(screen.getByRole("button", { name: "청산 저장" }));

    await waitFor(() => expect(목록조회).toBeGreaterThan(이전));
    // datetime-local 이 준 로컬처럼 보이는 값을 UTC 로 읽는다.
    expect(청산본문.exitAt).toBe("2026-08-03T12:00:00Z");
    expect(청산본문).not.toHaveProperty("realizedPnl");
  });

  it("계획 저장이 422 면 서버 문장이 그대로 나온다", async () => {
    server.use(
      ...응답(),
      http.post(origin + "/api/trades", () =>
        HttpResponse.json(
          { title: "도메인 규칙 위반", status: 422, detail: "비중의 합이 100 이 아니다", instance: "/api/trades" },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);

    await user.click(screen.getByRole("button", { name: "계획 저장", hidden: false }));

    expect(await screen.findByRole("alert")).toHaveTextContent("비중의 합이 100 이 아니다");
  });

  it("AI 만 503 이어도 기록 화면은 전부 동작한다", async () => {
    server.use(
      ...응답(),
      http.post(origin + "/api/ai/journal-query", () =>
        HttpResponse.json(
          { title: "AI 기능이 설정되지 않았다", status: 503, detail: "AI 기능이 설정되지 않았다", instance: "/api/ai/journal-query" },
          { status: 503 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);

    await user.type(screen.getByLabelText("질문"), "손실 직후에 들어간 거래는 어땠나");
    await user.click(screen.getByRole("button", { name: "묻기" }));

    expect(await screen.findByRole("status")).toHaveTextContent("AI 기능이 설정되지 않았다");
    // AI 실패로 기록을 못 남기는 일이 있으면 안 된다.
    expect(screen.getByRole("cell", { name: "계획 익절" })).toBeVisible();
    expect(screen.getByRole("button", { name: "계획 저장" })).toBeEnabled();
  });

  it("답변의 근거 거래는 목록의 그 거래를 가리킨다", async () => {
    server.use(
      ...응답(),
      http.post(origin + "/api/ai/journal-query", () =>
        HttpResponse.json({
          answer: "손실 직후 진입한 거래는 두 건이고 둘 다 계획을 어겼다",
          citedTradeIds: ["t-1"],
          retrieved: [{ tradeId: "t-1", score: 0.82, summary: "롱 · 계획 익절 · 손실 직후 아님" }],
        }),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<JournalScreen />);
    await screen.findByRole("cell", { name: "계획 익절" });

    await user.click(screen.getByRole("button", { name: "묻기" }));

    // 대조할 수 없는 답은 이 프로젝트에서 근거가 아니다.
    const 링크 = await screen.findByRole("link", { name: "t-1" });
    expect(링크).toHaveAttribute("href", "#trade-t-1");
    expect(document.getElementById("trade-t-1")).not.toBeNull();
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
