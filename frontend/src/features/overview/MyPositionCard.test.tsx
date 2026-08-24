import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MyPositionCard } from "./MyPositionCard";
import type { components } from "../../api/schema";

type Reconciliation = components["schemas"]["PositionReconciliationResponse"];
type Outliers = components["schemas"]["MetricOutliersResponse"];

type Actual = NonNullable<components["schemas"]["PositionMatchResponse"]["actual"]>;

const 거래소: Actual = {
  symbol: "BTCUSDT",
  entryPrice: 76940.67,
  markPrice: 77560,
  quantity: 0.13,
  liquidationPrice: 84273.55,
  liquidationDistancePercent: 8.6559,
  notional: 10082.8,
  unrealizedPnl: -68.8,
};

/**
 * 실계좌에서 실제로 나온 모양이다 — 숏이 열려 있고 기록에는 없다.
 *
 * 덮어쓸 칸을 인자로 받는다. 만들어 놓고 `matches[0]` 를 고치는 방식은 그 접근이
 * `undefined` 일 수 있어 타입이 거부한다.
 */
const 숏 = (덮어쓰기: Partial<Actual> = {}): Reconciliation => ({
  matches: [
    {
      direction: "SHORT",
      outcome: "EXCHANGE_ONLY",
      discrepancy: true,
      recorded: null,
      actual: { ...거래소, ...덮어쓰기 },
    },
  ],
  consistent: false,
  observedAt: "2026-08-23T14:27:28Z",
});

const 지표 = (
  metric: components["schemas"]["MetricOutlierResponse"]["metric"],
  current: number,
  side: components["schemas"]["MetricOutlierResponse"]["side"],
): components["schemas"]["MetricOutlierResponse"] => ({
  metric,
  current,
  topPercent: 50,
  outlier: false,
  sampleCount: 30,
  change: 0.01,
  changeWindow: 6,
  side,
  neutralPercent: 50,
  samples: [1, 2],
});

const 시장 = (fundingSide: components["schemas"]["MetricOutlierResponse"]["side"]): Outliers => ({
  symbol: "BTCUSDT",
  at: "2026-08-23T14:27:00Z",
  hasOutlier: false,
  crowdedLong: 3,
  crowdedShort: 1,
  price: 지표("PRICE", 77560, "NONE"),
  situations: [],
  metrics: [지표("FUNDING_RATE", 0.01, fundingSide)],
});

describe("내 자리", () => {
  /**
   * <b>수량이 아니라 명목이 위험의 크기다.</b> 0.13 BTC 라는 수는 얼마를 걸었는지를 말해
   * 주지 않는다 — 이 프로젝트를 만든 손실이 정확히 그 자리에서 났다.
   */
  it("명목과 청산까지의 거리를 함께 놓는다", () => {
    render(<MyPositionCard reconciliation={숏()} outliers={시장("LONG")} />);
    const 카드 = screen.getByRole("region", { name: "내 자리" });

    expect(within(카드).getByText("10,082.80")).toBeVisible();
    expect(within(카드).getByText("8.6559% 남았다")).toBeVisible();
  });

  /** 서버가 낸 값을 그대로 쓴다. 화면에서 다시 재면 규칙이 두 곳에 생긴다 — `docs/adr/020`. */
  it("청산 거리를 화면에서 다시 계산하지 않는다", () => {
    render(
      <MyPositionCard
        reconciliation={숏({ liquidationDistancePercent: 1.2345 })}
        outliers={시장("LONG")}
      />,
    );

    // 가격 셋(평단·표시가·청산가)은 그대로인데 표시는 서버가 준 수를 따른다.
    expect(screen.getByText("1.2345% 남았다")).toBeVisible();
  });

  /**
   * 거래소가 청산 지점을 말할 수 없으면 <b>비운다.</b> 0% 로 채우면 화면이 "임박" 으로 읽는다.
   */
  it("청산가가 없으면 거리를 지어내지 않는다", () => {
    render(
      <MyPositionCard
        reconciliation={숏({ liquidationPrice: null, liquidationDistancePercent: null })}
        outliers={시장("LONG")}
      />,
    );

    expect(screen.getByText(/청산 지점을 말하지 않는다/)).toBeVisible();
    expect(screen.queryByText(/남았다/)).toBeNull();
  });

  /**
   * <b>펀딩의 방향은 정의로 정해진다.</b> 펀딩비가 양수면 롱이 숏에게 내므로 숏인 나는 받는다.
   * 얼마를 받는지는 적지 않는다 — 그 곱셈은 서버의 일이고, 다음 정산까지 남은 시간도 모른다.
   */
  it("내 방향과 펀딩을 맞대어 내는지 받는지를 말한다", () => {
    render(<MyPositionCard reconciliation={숏()} outliers={시장("LONG")} />);

    expect(screen.getByText(/반대쪽이 내고 있다/)).toBeVisible();
  });

  it("내 쪽이 붐비면 내가 내는 쪽이다", () => {
    render(<MyPositionCard reconciliation={숏()} outliers={시장("SHORT")} />);

    expect(screen.getByText(/내 쪽이 내고 있다/)).toBeVisible();
  });

  /**
   * <b>대조까지가 이 카드의 일이고 판정은 아니다.</b> 내 방향과 붐비는 쪽을 나란히 놓지만
   * 어느 쪽이 유리한지는 말하지 않는다 — 그 문장은 예측이고 증거가 없다(`docs/adr/021`).
   */
  it("어느 쪽이 유리한지는 말하지 않는다", () => {
    render(<MyPositionCard reconciliation={숏()} outliers={시장("LONG")} />);
    const 카드 = screen.getByRole("region", { name: "내 자리" });

    expect(within(카드).getByText("롱 3")).toBeVisible();
    expect(within(카드).getByText("숏 1")).toBeVisible();
    expect(within(카드).queryByText(/유리|불리|위험하다|줄여라|닫아라|버텨/)).toBeNull();
  });

  /**
   * <b>"없다" 와 "기록에만 있다" 는 다른 사실이다.</b> 뒤쪽은 청산을 적지 않았다는 뜻일 수
   * 있고, 그것이 이 기능이 막으려는 상태 그 자체다.
   */
  it("거래소가 비었는데 기록이 열려 있으면 그것을 말한다", () => {
    const 기록만: Reconciliation = {
      matches: [
        {
          direction: "LONG",
          outcome: "RECORDED_ONLY",
          discrepancy: true,
          recorded: {
            tradeId: "t-9",
            averageEntryPrice: 60050,
            quantity: 0.005,
            fullyFilled: true,
            openedAt: "2026-08-20T02:00:00Z",
          },
          actual: null,
        },
      ],
      consistent: false,
      observedAt: "2026-08-23T14:27:28Z",
    };

    render(<MyPositionCard reconciliation={기록만} outliers={시장("LONG")} />);

    /*
      **한 줄짜리 경고가 제 몫의 카드가 됐다.** 아래 「기록과 거래소」가 지워지면서 이 사실을
      아는 자리가 여기 하나만 남았고, 그러면 "1건" 이라는 셈만 말하는 것으로는 부족하다 —
      무엇이 얼마나 열려 있다고 적혀 있는지가 이 화면 밖에는 없다.
    */
    expect(screen.getByText("거래소에 없다")).toBeVisible();
    expect(screen.getByText(/청산을 기록했는가/)).toBeVisible();
    // 기록에 적힌 값. 거래소가 모르므로 청산가도 미실현도 없다.
    expect(screen.getByText("60,050.00")).toBeVisible();
    expect(screen.getByText("0.00500000")).toBeVisible();
    expect(screen.queryByText("미실현")).not.toBeInTheDocument();
  });

  /** 시장을 못 읽어도 포지션은 보여야 한다. 두 사실은 서로 다른 엔드포인트에서 온다. */
  it("시장 지표가 없어도 포지션은 그대로 그린다", () => {
    render(<MyPositionCard reconciliation={숏()} />);

    expect(screen.getByText("10,082.80")).toBeVisible();
    expect(screen.queryByText(/붐비는 쪽/)).toBeNull();
  });
});
