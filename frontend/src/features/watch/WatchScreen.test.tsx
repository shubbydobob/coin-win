import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { SIDE_TEXT } from "./OutlierPanel";
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
  bidWall: null,
  askWall: null,
};

/** 표본은 스파크라인이 그려지도록 두 개 이상 둔다. */
const 표본 = (from: number, step: number) =>
  Array.from({ length: 6 }, (_, index) => from + step * index);

/**
 * 인자가 여덟이라 이름을 붙인다. **`side` 와 `neutralPercent` 를 여기서 계산하지 않는다** —
 * 서버가 하는 판단을 픽스처가 다시 하면 화면이 그 계산을 잘못 써도 테스트가 통과한다.
 * Phase 8 이 겪은 "픽스처가 거짓 타입으로 작성돼 전부 초록" 과 같은 함정이다.
 */
const 지표 = (fields: {
  metric: components["schemas"]["MetricOutlierResponse"]["metric"];
  current: number;
  topPercent: number | null;
  outlier?: boolean;
  change: number | null;
  side: components["schemas"]["MetricOutlierResponse"]["side"];
  neutralPercent: number | null;
  samples: number[];
}): components["schemas"]["MetricOutlierResponse"] => ({
  ...fields,
  outlier: fields.outlier ?? false,
  sampleCount: fields.samples.length,
  changeWindow: 6,
});

const OUTLIERS: components["schemas"]["MetricOutliersResponse"] = {
  symbol: "BTCUSDT",
  at: "2026-08-23T09:00:00Z",
  hasOutlier: true,
  // 방향 있는 넷 중 셋이 롱 쪽이다. 합이 다섯이 아닌 것은 미결제약정에 축이 없어서다.
  crowdedLong: 3,
  crowdedShort: 1,
  price: 지표({ metric: "PRICE", current: 76567.5, topPercent: null, change: 0.011,
    side: "NONE", neutralPercent: null, samples: 표본(76000, 100) }),
  situations: ["미결제약정이 줄면서 가격이 올랐다 — 새로 들어온 돈이 아니라 포지션이 정리되며 만들어진 움직임이다"],
  metrics: [
    // 표본이 전부 0 보다 커서 가운데선이 눈금 왼쪽 끝에 붙는다 — 30일 내내 롱 쪽이었다는 뜻.
    지표({ metric: "FUNDING_RATE", current: 0.01, topPercent: 7.22, change: 0.002,
      side: "LONG", neutralPercent: 100, samples: 표본(0.008, 0.0004) }),
    지표({ metric: "OPEN_INTEREST", current: 106613.964, topPercent: 0, outlier: true,
      change: -0.032, side: "NONE", neutralPercent: null, samples: 표본(110000, -700) }),
    // 표본이 모자라 위치를 말할 수 없는 경우. 그때도 진영은 있다.
    지표({ metric: "LONG_SHORT_RATIO", current: 1.0396, topPercent: null, change: null,
      side: "LONG", neutralPercent: null, samples: [1.03, 1.04] }),
    지표({ metric: "TAKER_RATIO", current: 1.1866, topPercent: 44.1, change: 0.05,
      side: "LONG", neutralPercent: 100, samples: 표본(1.1, 0.02) }),
    지표({ metric: "TOP_POSITION_RATIO", current: 0.87, topPercent: 58.33, change: -0.01,
      side: "SHORT", neutralPercent: 0, samples: 표본(0.9, -0.01) }),
  ],
};

const MACRO: components["schemas"]["MacroQuoteListResponse"] = {
  // 열둘을 물었는데 둘만 왔다. **원래 둘인 줄 알게 두지 않는다** — 화면이 뺄셈을 할 수
  // 있도록 물어본 수를 서버가 함께 낸다.
  requested: 12,
  quotes: [
    { symbol: "QQQUSDT", label: "나스닥 100", group: "EQUITY", groupLabel: "주가",
      last: 612.34, change24hPercent: 0.84 },
    { symbol: "XAUUSDT", label: "금", group: "METAL", groupLabel: "금속",
      last: 2410.5, change24hPercent: -0.31 },
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
  http.get(origin + "/api/markets/macro", () => HttpResponse.json(MACRO)),
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
    expect(await screen.findByText("나스닥 100")).toBeVisible();
  });

  /**
   * <b>상황 문장은 비어 있는 것이 정상이다.</b> 조건이 맞을 때만 뜨고, 문장은 일어난 일까지만
   * 적는다 — 무엇을 하라고 말하지 않는다.
   */
  it("조건이 맞으면 상황을 문장으로 말한다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    expect(await screen.findByText(/포지션이 정리되며 만들어진 움직임/)).toBeVisible();
  });

  it("거시 자산을 못 읽은 개수를 말한다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    // 열둘 중 둘만 왔다 — 원래 둘인 줄 알게 두지 않는다.
    expect(await screen.findByText("10종목을 못 읽었다")).toBeVisible();
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
    expect(within(블록).getByText("표본 2개 — 위치를 아직 말할 수 없다")).toBeVisible();
    // 위치는 단계 이름과 한 줄에 붙는다 — "평소와 다름 · 최근 N개 중 상위 7.2200%".
    expect(within(블록).getByText(/상위 7\.2200%/)).toBeVisible();
    // 변화를 말할 수 없는 것과 0 은 다른 사실이다.
    expect(within(블록).getByText("변화를 말할 수 없다")).toBeVisible();
  });

  /**
   * <b>위치만으로는 어느 쪽인지 모른다.</b> "상위 96.7%" 는 그것이 롱 쪽인지 숏 쪽인지를
   * 말하지 않는다. 지표마다 근거가 다르므로 문장도 지표의 말로 적는다 — 펀딩비는 비용이고
   * 상위 계정은 크기다.
   */
  it("지표마다 어느 쪽이 붐비는지를 그 지표의 말로 적는다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });
    // 물음표 설명에도 같은 구절이 있다. 진영 문장은 그 뒤가 다르므로 통째로 가리킨다.
    expect(within(블록).getByText("롱이 숏에게 낸다 — 들고 있는 쪽은 롱이 비용을 문다"))
        .toBeVisible();
    expect(within(블록).getByText("큰손은 숏 쪽에 실려 있다")).toBeVisible();
    // 축이 없는 것과 가운데 있는 것은 다르다.
    expect(within(블록).getByText(/방향 없음 — 크기다/)).toBeVisible();
    // 표본이 모자라 위치는 못 말해도 진영은 말한다.
    expect(within(블록).getByText("계정 수로는 롱이 많다")).toBeVisible();
  });

  /**
   * <b>셈이지 판정이 아니다.</b> 두 수를 나란히 두고 하나로 합치지 않는다 — 합치려면 지표에
   * 가중치를 줘야 하고 그 가중치는 검증할 방법이 없다(<code>docs/adr/021</code>).
   */
  it("붐비는 쪽을 세되 어느 쪽이 유리한지는 말하지 않는다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });
    expect(within(블록).getByText("롱 3")).toBeVisible();
    expect(within(블록).getByText("숏 1")).toBeVisible();
  });

  /**
   * 서버의 <code>어떤_문장도_행동을_지시하지_않는다</code> 와 짝이다. 진영 문장은 화면이
   * 갖고 있으므로 여기서 재야 한다.
   *
   * <b>화면 전체를 훑지 않는다.</b> 머리말이 "유리한 쪽이 아니다" 라고 부인하고 있어서
   * 낱말만 세면 그 부인까지 위반으로 잡힌다 — 재야 할 것은 문장 표 자체다. 뜨지 않는
   * 경우(<code>BALANCED</code>)까지 함께 걸리는 것도 이쪽이 낫다.
   */
  it("진영 문장은 어느 쪽이 유리한지를 말하지 않는다", () => {
    const 문장들 = Object.values(SIDE_TEXT).flatMap((쪽) => Object.values(쪽));

    expect(문장들.length).toBeGreaterThan(0);
    문장들.forEach((문장) => {
      expect(문장).not.toMatch(/유리|불리|추천|신호|기회|노려|잡아|들어가|진입하|매수하|매도하/);
    });
  });

  /**
   * <b>범례는 펼쳐져 있어야 한다.</b> 앞판은 접어 두었는데, 접힌 설명은 색이 무슨 뜻인지
   * 모르는 사람에게 <b>없는 것과 같다.</b> 세 단계뿐이라 한 줄에 들어가므로 접을 이유가
   * 사라졌다 — 클릭 없이 보이는 것이 이 테스트가 지키는 것이다.
   */
  it("신호등이 무엇을 뜻하는지를 화면이 스스로 말한다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });

    ["평소", "치우침", "평소와 다름"].forEach((뜻) => {
      expect(within(블록).getAllByText(뜻).length).toBeGreaterThan(0);
    });
    // 드문 정도이지 좋고 나쁨이 아니라는 것도 화면이 말한다.
    expect(within(블록).getByText(/드문 정도이지 좋고 나쁨이 아니다/)).toBeVisible();
  });

  /**
   * <b>색만으로 뜻을 지지 않는다.</b> 회색·노랑·주황은 어두운 배경에서 서로 가까워 보이고
   * 이 화면은 빠르게 훑는 자리다. 신호등마다 읽을 수 있는 이름이 붙어야 한다.
   */
  it("신호등에는 언제나 읽을 수 있는 이름이 붙는다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });

    const 등들 = within(블록).getAllByRole("img");
    expect(등들.length).toBeGreaterThan(0);
    등들.forEach((등) => {
      expect(등.getAttribute("aria-label")).toMatch(/평소|치우침|평소와 다름/);
    });
  });

  /**
   * <b>신호등 색은 등락과 아무 관계가 없다.</b> 초록·빨강은 이 저장소에서 이미 롱 쪽 / 숏 쪽을
   * 뜻한다(<code>shared/tone.ts</code>). 신호등에까지 쓰면 같은 초록이 한 화면에서 두 가지를
   * 가리키고, 그것이 그 파일이 고치려고 쓰인 문제다.
   *
   * 픽스처에서 미결제약정은 이상치(양 끝 5%)이므로 가장 센 단계여야 한다.
   */
  it("신호등에는 롱숏 색을 쓰지 않는다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    await screen.findByText("평소와 다른가");
    const 블록 = screen.getByRole("region", { name: "이상치" });

    const 미결제 = within(블록).getByRole("img", { name: /미결제약정: 평소와 다름/ });
    expect(미결제.getAttribute("class")).toContain("bg-alert");

    within(블록).getAllByRole("img").forEach((등) => {
      expect(등.getAttribute("class")).not.toContain("bg-up");
      expect(등.getAttribute("class")).not.toContain("bg-down");
    });
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

  /**
   * <b>호가 벽은 접힌 목록 밖에 있어야 한다.</b> 단 40줄을 펼쳐야만 보이면 그 사실은 사실상
   * 없는 것과 같다. 여기서 목록을 펼치지 않고 찾는 것이 그 규칙을 지킨다.
   */
  it("두꺼운 단은 목록을 펼치지 않아도 보인다", async () => {
    server.use(
      http.get(origin + "/api/markets/BTCUSDT/orderbook", () =>
        HttpResponse.json({
          ...BOOK,
          askWall: { price: 76600, quantity: 98.5, multipleOfAverage: 8.14 },
        })),
      ...전부성공,
    );
    renderScreen(<WatchScreen />);

    const 호가블록 = await screen.findByRole("region", { name: "호가" });
    await waitFor(() => expect(within(호가블록).getByText(/76,600\.00/)).toBeVisible());
    expect(within(호가블록).getByText(/평균의 8\.14배/)).toBeVisible();
  });

  /**
   * <b>없으면 없다고 적는다.</b> 빈 자리로 두면 기능이 없는 것인지 지금 벽이 없는 것인지
   * 알 수 없고, 그렇다고 늘 최댓값을 띄우면 그 표시는 아무것도 경고하지 않는다.
   */
  it("두꺼운 단이 없으면 없다고 적는다", async () => {
    server.use(...전부성공);
    renderScreen(<WatchScreen />);

    const 호가블록 = await screen.findByRole("region", { name: "호가" });
    await waitFor(() => expect(within(호가블록).getByText("지금 없다")).toBeVisible());
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
