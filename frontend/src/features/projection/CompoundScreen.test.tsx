import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, delay, http } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { CompoundScreen } from "./CompoundScreen";
import type { components } from "../../api/schema";

/** 서버 예제와 같은 조건 — 800 USDT, 월 5%, 12개월, 10배 × 투입 20%(실효 2배), 월 20건. */
const RESULT: components["schemas"]["CompoundTargetResponse"] = {
  equity: [800, 840, 882, 926.1, 972.41, 1021.03, 1072.08, 1125.68, 1181.96, 1241.06, 1303.12,
    1368.27, 1436.69],
  finalEquity: 1436.69,
  grossProfit: 1366.57,
  totalProfit: 636.69,
  months: 12,
  totalReturn: 79.5856,
  totalCost: 729.88,
  notional: 1600,
  effectiveLeverage: 2,
  totalTrades: 240,
  netPerTrade: 0.2442,
  costPerTrade: 0.28,
  grossPerTrade: 0.5242,
  priceMovePerTrade: 0.2621,
  costShare: 53.4147,
  won: {
    wonPerUsdt: 1370,
    observedAt: "2026-08-23T15:04:04Z",
    finalEquity: 1968265,
    totalProfit: 872265,
    totalCost: 999936,
    notional: 2192000,
  },
};

const 환율_없음: components["schemas"]["CompoundTargetResponse"] = { ...RESULT, won: null };

function 계산이(결과: components["schemas"]["CompoundTargetResponse"]) {
  server.use(
    http.post(origin + "/api/projections/compound-target", () => HttpResponse.json(결과)),
  );
}

async function 계산을_누른다() {
  const user = userEvent.setup();
  renderScreen(<CompoundScreen />);
  await user.click(screen.getByRole("button", { name: "계산" }));
}

describe("목표 복리", () => {
  it("먼저 답한다 — 12개월 뒤 얼마가 되는가", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("12개월 뒤");
    expect(답).toHaveTextContent("1,436.69");
  });

  /**
   * 첫 판은 순이익 옆에 비용만 놓았고, 그러면 "636 을 버는데 3,649 를 낸다" 로 읽혀
   * **손실처럼 보인다.** 번 돈이 함께 있어야 4,286 − 3,649 = 636 이 눈으로 닫힌다.
   */
  it("번 돈 · 낸 돈 · 남는 돈이 순서대로 서서 뺄셈이 닫힌다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("번 돈");
    expect(답).toHaveTextContent("+1,366.57");
    expect(답).toHaveTextContent("낸 돈");
    expect(답).toHaveTextContent("−729.88");
    expect(답).toHaveTextContent("남는 돈");
    expect(답).toHaveTextContent("+636.69");
  });

  it("같은 금액을 원화로도 낸다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("1,968,265원");
    expect(답).toHaveTextContent("+872,265원");
  });

  /**
   * 세 금액을 각각 원 단위로 반올림하면 뺄셈이 1원 어긋난다. 닫히라고 만든 사슬이
   * 안 닫히는 것으로 보이므로, 가운데 두 줄에는 원화를 붙이지 않는다.
   */
  it("사슬 가운데 두 줄에는 원화를 붙이지 않는다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("+1,366.57");
    expect(답).not.toHaveTextContent("999,936");
  });

  /** 도착점만 크게 띄우면 복리 계산기가 늘 보여 주는 기분 좋은 곡선이 된다. */
  it("도착점과 같은 칸에 거래당 필요 가격 변동을 적는다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("0.2621%");
    expect(답).toHaveTextContent("240건");
  });

  it("자세히 쪽에 복리와 비용의 몫이 있다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    // 월 5% × 12 = 60% 가 아니다
    expect(await screen.findByText("79.5856%")).toBeVisible();
    expect(screen.getByText("53.4147%")).toBeVisible();
    expect(screen.getByText("0.2800%")).toBeVisible();
  });

  /**
   * 비용이 "말이 안 되게" 커 보였던 자리. 명목은 자산 × 레버리지가 아니라
   * 자산 × 투입 비율 × 레버리지이고, 그 곱이 실효 배율이다.
   */
  it("실효 배율과 명목이 투입 비율을 반영한다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    expect(await screen.findByText("2.00배")).toBeVisible();
    expect(screen.getByText("실효 배율")).toBeVisible();
  });

  it("어느 환율로 옮긴 값인지 말한다", async () => {
    계산이(RESULT);
    await 계산을_누른다();

    expect(await screen.findByText(/업비트 KRW-USDT 1,370.00원 기준/)).toBeVisible();
  });

  /** 원화가 그냥 사라지면 기능이 없는 것인지 오늘 못 얻은 것인지 알 수 없다. */
  it("환율을 얻지 못하면 원화를 비우고 그 사실을 말한다", async () => {
    계산이(환율_없음);
    await 계산을_누른다();

    const 답 = await screen.findByRole("region", { name: "목표 복리 결과" });
    expect(답).toHaveTextContent("1,436.69");
    expect(답).not.toHaveTextContent("원");
    expect(screen.getByText(/환율을 얻지 못해/)).toBeVisible();
  });

  it("계산하는 동안 버튼이 비활성이다", async () => {
    server.use(
      http.post(origin + "/api/projections/compound-target", async () => {
        await delay(50);
        return HttpResponse.json(RESULT);
      }),
    );
    await 계산을_누른다();

    expect(screen.getByRole("button", { name: "계산하는 중" })).toBeDisabled();
    expect(await screen.findByRole("region", { name: "목표 복리 결과" })).toBeVisible();
  });

  it("422 응답의 detail 문장이 그대로 나온다", async () => {
    server.use(
      http.post(origin + "/api/projections/compound-target", () =>
        HttpResponse.json(
          {
            title: "도메인 규칙 위반",
            status: 422,
            detail: "총 거래 수는 10000 을 넘을 수 없다: 1000 × 12",
            instance: "/api/projections/compound-target",
          },
          { status: 422 },
        ),
      ),
    );
    await 계산을_누른다();

    expect(await screen.findByRole("alert")).toHaveTextContent("총 거래 수는 10000 을 넘을 수 없다");
  });
});
