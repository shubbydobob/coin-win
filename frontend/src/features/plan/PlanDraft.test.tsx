import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../../test/msw/server";
import { renderScreen } from "../../../test/render";
import { PlanScreen } from "./PlanScreen";
import type { components } from "../../api/schema";

const ANALYSIS = "/api/position-plans/analysis";

const DRAFT: components["schemas"]["PlanDraftResponse"] = {
  direction: "SHORT",
  entries: [
    { price: 62000, allocation: 50 },
    { price: 63000, allocation: 50 },
  ],
  stopLoss: 64000,
  takeProfit: 58000,
  leverage: 5,
};

const 꺼짐 = http.post(origin + "/api/ai/plan-draft", () =>
  HttpResponse.json(
    {
      title: "AI 기능이 설정되지 않았다",
      status: 503,
      detail: "AI 기능이 설정되지 않았다. OPENAI_API_KEY 가 필요하다",
      instance: "/api/ai/plan-draft",
    },
    { status: 503 },
  ),
);

describe("계획 초안 (AI 보조)", () => {
  it("읽어낸 값으로 칸을 채울 뿐 제출하지 않는다", async () => {
    let 제출됨 = false;
    server.use(
      http.post(origin + "/api/ai/plan-draft", () => HttpResponse.json(DRAFT)),
      http.post(origin + ANALYSIS, () => {
        제출됨 = true;
        return HttpResponse.json({ fillStates: [], requiredMargin: 0, riskRewardRatio: 0, weakRiskReward: false, marginExceedsBalance: false });
      }),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await user.type(screen.getByLabelText("계획 문장"), "62000, 63000 반반 숏, 손절 64000, 익절 58000, 5배");
    await user.click(screen.getByRole("button", { name: "칸 채우기" }));

    expect(await screen.findByLabelText("1회차 진입가")).toHaveValue(62000);
    expect(screen.getByLabelText("손절가")).toHaveValue(64000);
    // 사용자가 본 다음에 제출한다. AI 가 읽어낸 값이 확인 없이 기록이 되면 안 된다.
    expect(제출됨).toBe(false);
  });

  it("AI 가 꺼져 있어도 계획 계산은 그대로 된다", async () => {
    server.use(
      꺼짐,
      http.post(origin + ANALYSIS, () =>
        HttpResponse.json({
          fillStates: [{ filledEntries: 1, averageEntryPrice: 60000, quantity: 0.005, liquidationPrice: 54216.87, maxLoss: 10.67 }],
          requiredMargin: 31.47,
          riskRewardRatio: 2.33,
          weakRiskReward: false,
          marginExceedsBalance: false,
        }),
      ),
    );
    const user = userEvent.setup();
    renderScreen(<PlanScreen />);

    await user.click(screen.getByRole("button", { name: "칸 채우기" }));
    expect(await screen.findByRole("status")).toHaveTextContent("OPENAI_API_KEY 가 필요하다");

    // AI 실패로 계획을 못 세우는 일이 있으면 안 된다.
    await user.click(screen.getByRole("button", { name: "계산" }));
    expect(await screen.findByText("54,216.87")).toBeVisible();
  });
});
