import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, delay, http } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { PlanScreen } from "./PlanScreen";
import type { components } from "../../api/schema";

const ANALYSIS = "/api/position-plans/analysis";

/**
 * 픽스처를 생성된 타입으로 못 박는다. 백엔드 DTO 에서 필드가 사라지면 화면 코드보다 먼저
 * 여기가 컴파일되지 않는다(§ 5.3).
 */
const RESULT: components["schemas"]["PositionAnalysisResponse"] = {
  fillStates: [
    {
      filledEntries: 1,
      averageEntryPrice: 60000,
      quantity: 0.00266667,
      liquidationPrice: 54216.87,
      maxLoss: 10.67,
    },
    {
      filledEntries: 2,
      averageEntryPrice: 59000,
      quantity: 0.00533333,
      liquidationPrice: 53313.25,
      maxLoss: 16,
    },
  ],
  requiredMargin: 31.47,
  riskRewardRatio: 2.33,
  weakRiskReward: false,
  marginExceedsBalance: false,
};

function respondWith(analysis: components["schemas"]["PositionAnalysisResponse"]) {
  return http.post(origin + ANALYSIS, () => HttpResponse.json(analysis));
}

async function 계산한다(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("1회차 진입가"), "60000");
  await user.type(screen.getByLabelText("2회차 진입가"), "58000");
  await user.type(screen.getByLabelText("손절가"), "56000");
  await user.type(screen.getByLabelText("익절가"), "66000");
  await user.type(screen.getByLabelText("레버리지"), "10");
  await user.type(screen.getByLabelText("잔고"), "800");
  await user.type(screen.getByLabelText("리스크 비율"), "2");
  await user.click(screen.getByRole("button", { name: "계산" }));
}

describe("포지션 계획 계산기", () => {
  it("체결 상태 표는 응답에 온 행 수만큼 나온다", async () => {
    server.use(respondWith(RESULT));
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    expect(await screen.findByRole("rowheader", { name: "1/2" })).toBeVisible();
    expect(screen.getByRole("rowheader", { name: "2/2" })).toBeVisible();
  });

  it("응답에 온 수가 서버 스케일 그대로 화면에 나온다", async () => {
    server.use(respondWith(RESULT));
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    // JSON 을 지나오며 지워진 자릿수를 되살린 것 말고는 아무것도 하지 않는다.
    expect(await screen.findByText("60,000.00")).toBeVisible();
    expect(screen.getByText("0.00266667")).toBeVisible();
    expect(screen.getByText("54,216.87")).toBeVisible();
    expect(screen.getByText("10.67")).toBeVisible();
    expect(screen.getByText("16.00")).toBeVisible();
    expect(screen.getByText("31.47")).toBeVisible();
    expect(screen.getByText("2.33")).toBeVisible();
  });

  it("손익비가 기준에 못 미치면 경고가 보이지만 제출은 막히지 않는다", async () => {
    server.use(respondWith({ ...RESULT, weakRiskReward: true, marginExceedsBalance: true }));
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    expect(await screen.findByText("손익비가 기준에 못 미친다")).toBeVisible();
    expect(screen.getByText("필요 증거금이 잔고를 넘는다")).toBeVisible();
    expect(screen.getByRole("button", { name: "계산" })).toBeEnabled();
  });

  it("422 응답의 detail 문장이 그대로 나오고 입력은 지워지지 않는다", async () => {
    const 문장 = "롱 포지션의 손절가는 최저 진입가보다 낮아야 한다";
    server.use(
      http.post(origin + ANALYSIS, () =>
        HttpResponse.json(
          { title: "도메인 규칙 위반", status: 422, detail: 문장, instance: ANALYSIS },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(문장);
    expect(screen.getByLabelText("손절가")).toHaveValue(56000);
  });

  it("계산 중에는 버튼이 비활성이다", async () => {
    server.use(
      http.post(origin + ANALYSIS, async () => {
        await delay(50);
        return HttpResponse.json(RESULT);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    expect(screen.getByRole("button", { name: "계산 중" })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole("button", { name: "계산" })).toBeEnabled());
  });

  it("수량 입력란이 없다", () => {
    renderScreen(<PlanScreen />);

    // 수량은 손절가와 리스크 예산이 결정하는 출력이다. 칸을 두면 리스크 사이징을 건너뛴다.
    expect(screen.queryByLabelText(/수량/)).not.toBeInTheDocument();
  });

  it("보낸 요청에 수량이 없다", async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(origin + ANALYSIS, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(RESULT);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await 계산한다(user);

    await waitFor(() => expect(Object.keys(sent)).not.toHaveLength(0));
    expect(sent).not.toHaveProperty("quantity");
    expect(sent.entries).toEqual([
      { price: 60000, allocation: 50 },
      { price: 58000, allocation: 50 },
    ]);
  });

  it("비중 합계는 보여 주되 유효한지는 말하지 않는다", async () => {
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    expect(screen.getByText("비중 합계 100")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "회차 추가" }));
    await user.type(screen.getByLabelText("3회차 비중"), "30");

    expect(screen.getByText("비중 합계 130")).toBeVisible();
    // 130 은 성립하지 않는 계획이지만 그 판정은 서버가 한다.
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("빈 칸은 0 으로 채우지 않고 서버에 없는 값으로 보낸다", async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(origin + ANALYSIS, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(RESULT);
      }),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await user.click(screen.getByRole("button", { name: "계산" }));

    await waitFor(() => expect(Object.keys(sent)).not.toHaveLength(0));
    // 0 으로 채우면 "잔고 0" 이라는 있지도 않은 값이 서버에 간다.
    expect(sent.accountBalance).toBeNull();
  });
});
