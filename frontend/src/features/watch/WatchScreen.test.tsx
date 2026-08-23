import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { WatchScreen } from "./WatchScreen";
import type { components } from "../../api/schema";

const BOOK: components["schemas"]["OrderBookResponse"] = {
  symbol: "BTCUSDT",
  at: "2026-08-23T09:00:00Z",
  last: 76567.5,
  change24hPercent: -1.009,
  high24h: 77590.6,
  low24h: 75588,
  volume24h: 113033.306,
  bestBid: 76567.4,
  bestAsk: 76567.5,
  spread: 0.1,
  spreadPercent: 0.0001,
  bidVolume: 22.1,
  askVolume: 18.4,
  imbalance: 0.091,
  bids: [{ price: 76567.4, quantity: 24.778 }],
  asks: [{ price: 76567.5, quantity: 12.029 }],
};

const OUTLIERS: components["schemas"]["MetricOutliersResponse"] = {
  symbol: "BTCUSDT",
  at: "2026-08-23T09:00:00Z",
  hasOutlier: true,
  metrics: [
    { metric: "FUNDING_RATE", current: 0.01, topPercent: 7.22, outlier: false, sampleCount: 90 },
    { metric: "OPEN_INTEREST", current: 106613.964, topPercent: 0, outlier: true, sampleCount: 30 },
    // 표본이 모자라 위치를 말할 수 없는 경우. 이 화면에서 유일하게 null 이 오는 자리다.
    { metric: "LONG_SHORT_RATIO", current: 1.0396, topPercent: null, outlier: false, sampleCount: 3 },
  ],
};

const CALENDAR: components["schemas"]["EventCalendarResponse"] = {
  now: "2026-08-23T09:00:00Z",
  stale: false,
  events: [
    {
      kind: "QRA",
      at: "2026-11-04T13:30:00Z",
      title: "재무부 분기 자금조달계획 — 국채 바이백 규모",
      importance: "HIGH",
      until: "PT1780H30M",
      warning: false,
    },
  ],
};

const NOTICES: components["schemas"]["NoticeListResponse"] = {
  at: "2026-08-23T09:00:00Z",
  notices: [
    {
      title: "Binance Futures Will Launch UNITREEUSDT",
      at: "2026-08-19T02:30:00Z",
      url: "https://www.binance.com/support/announcement/abc",
    },
  ],
};

const 전부성공 = [
  http.get(origin + "/api/markets/BTCUSDT/orderbook", () => HttpResponse.json(BOOK)),
  http.get(origin + "/api/markets/BTCUSDT/outliers", () => HttpResponse.json(OUTLIERS)),
  http.get(origin + "/api/watch/events", () => HttpResponse.json(CALENDAR)),
  http.get(origin + "/api/watch/notices", () => HttpResponse.json(NOTICES)),
];

const 실패 = (detail: string) => () =>
  HttpResponse.json({ title: "외부 데이터를 가져오지 못했다", status: 503, detail, instance: "/x" }, { status: 503 });

describe("감시", () => {
  it("네 블록이 함께 보인다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    // 로딩 중에는 실패 자리 표시자가 같은 영역 이름을 갖는다. 영역을 먼저 잡으면 데이터를
    // 기다리지 않고 통과할 수 있다 — 새로고침 버튼은 값이 온 뒤에만 있으므로 그것을 기다린다.
    await screen.findByRole("button", { name: "새로고침" });
    const 시세 = screen.getByRole("region", { name: "현재가" });
    expect(within(시세).getByText("76,567.50")).toBeVisible();
    expect(await screen.findByText("평소와 다른가")).toBeVisible();
    expect(await screen.findByText(/국채 바이백 규모/)).toBeVisible();
    expect(await screen.findByText(/UNITREEUSDT/)).toBeVisible();
  });

  /**
   * <b>표본이 모자라면 위치를 말하지 않는다.</b> "표본 3개 중 상위 33%" 는 수치의 모양만 갖춘
   * 거짓말이다 — 서버가 null 을 주고 화면은 왜 비었는지를 말한다.
   */
  it("표본이 모자란 지표는 위치 대신 이유를 말한다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });
    expect(within(블록).getByText("표본 3개 — 아직 말할 수 없다")).toBeVisible();
    expect(within(블록).getByText("상위 7.2200%")).toBeVisible();
  });

  /**
   * <b>블록마다 독립적으로 실패한다.</b> 공지는 문서화되지 않은 엔드포인트를 쓰므로 예고 없이
   * 죽을 수 있고, 그때도 나머지 셋은 그대로 보여야 한다.
   */
  it("공지가 죽어도 나머지 셋은 그대로다", async () => {
    // MSW 는 먼저 등록된 핸들러가 이긴다. 덮어쓰기를 앞에 두지 않으면 성공 응답이 계속 이긴다.
    server.use(
      http.get(origin + "/api/watch/notices", 실패("공지 페이지에 닿지 못했다")),
      ...전부성공,
    );
    renderScreen(<WatchScreen />);

    expect(await screen.findByText("공지 페이지에 닿지 못했다")).toBeVisible();
    await screen.findByRole("button", { name: "새로고침" });
    expect(within(screen.getByRole("region", { name: "현재가" })).getByText("76,567.50"))
        .toBeVisible();
    expect(await screen.findByText(/국채 바이백 규모/)).toBeVisible();
  });

  it("호가가 죽어도 캘린더는 그대로다", async () => {
    server.use(
      http.get(origin + "/api/markets/BTCUSDT/orderbook", 실패("거래소에 닿지 못했다")),
      ...전부성공,
    );
    renderScreen(<WatchScreen />);

    expect(await screen.findByText("거래소에 닿지 못했다")).toBeVisible();
    expect(await screen.findByText(/국채 바이백 규모/)).toBeVisible();
  });

  it("일정표가 낡으면 목록보다 먼저 말한다", async () => {
    server.use(
      http.get(origin + "/api/watch/events", () => HttpResponse.json({ ...CALENDAR, stale: true })),
      ...전부성공,
    );
    renderScreen(<WatchScreen />);

    expect(await screen.findByText(/일정표가 낡았다/)).toBeVisible();
  });

  describe("폴링", () => {
    beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
    afterEach(() => vi.useRealTimers());

    function 호가(응답: () => Response) {
      let 횟수 = 0;
      server.use(
        http.get(origin + "/api/markets/BTCUSDT/orderbook", () => {
          횟수 += 1;
          return 응답();
        }),
        ...전부성공,
      );
      return () => 횟수;
    }

    it("주기가 지나면 호가를 저절로 다시 묻는다", async () => {
      const 횟수 = 호가(() => HttpResponse.json(BOOK));
      renderScreen(<WatchScreen />);

      await screen.findByRole("button", { name: "새로고침" });
      expect(횟수()).toBe(1);

      await vi.advanceTimersByTimeAsync(3_000);

      await waitFor(() => expect(횟수()).toBe(2));
    });

    it("호가가 실패하면 폴링을 멈추고 사람이 다시 켠다", async () => {
      let 실패중 = true;
      const 횟수 = 호가(() =>
        실패중 ? 실패("거래소에 닿지 못했다")() : HttpResponse.json(BOOK));
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderScreen(<WatchScreen />);

      expect(await screen.findByText("거래소에 닿지 못했다")).toBeVisible();

      await vi.advanceTimersByTimeAsync(3_000 * 5);
      expect(횟수()).toBe(1);

      실패중 = false;
      await user.click(within(screen.getByRole("region", { name: "현재가" })).getByRole("button", { name: "다시 시도" }));

      await screen.findByRole("button", { name: "새로고침" });
      expect(
        within(screen.getByRole("region", { name: "현재가" })).getByText("76,567.50"),
      ).toBeVisible();
    });
  });
});
