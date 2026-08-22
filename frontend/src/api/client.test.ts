import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { origin, server } from "../../test/msw/server";
import { get, post } from "./client";
import { ApiFailure } from "./problem";
import type { components } from "./schema";

const ANALYSIS = "/api/position-plans/analysis";

const PLAN: components["schemas"]["AnalyzePositionRequest"] = {
  direction: "LONG",
  entries: [{ price: 60000, allocation: 100 }],
  stopLoss: 56000,
  takeProfit: 66000,
  leverage: 10,
  accountBalance: 800,
  riskPercent: 2,
};

/** 오류 본문은 백엔드가 내는 모양 그대로 쓴다. 타입이 그것을 못 박는다. */
function problem(
  status: number,
  title: string,
  detail: string,
): components["schemas"]["ProblemDetail"] {
  return { title, status, detail, instance: ANALYSIS };
}

function failing(status: number, title: string, detail: string) {
  return http.post(origin + ANALYSIS, () =>
    HttpResponse.json(problem(status, title, detail), {
      status,
      headers: { "Content-Type": "application/problem+json" },
    }),
  );
}

async function failureOf(call: Promise<unknown>): Promise<ApiFailure> {
  try {
    await call;
  } catch (thrown) {
    if (thrown instanceof ApiFailure) {
      return thrown;
    }
    throw thrown;
  }
  throw new Error("오류가 나야 하는데 성공했다");
}

describe("서버와 이야기하는 자리", () => {
  it("성공 응답의 본문을 그대로 낸다", async () => {
    const body = { requiredMargin: 31.47, fillStates: [] };
    server.use(http.post(origin + ANALYSIS, () => HttpResponse.json(body)));

    await expect(post(ANALYSIS, PLAN)).resolves.toEqual(body);
  });

  it("422 응답의 detail 문장이 가공 없이 그대로 올라온다", async () => {
    const 문장 = "롱 포지션의 손절가는 최저 진입가보다 낮아야 한다";
    server.use(failing(422, "도메인 규칙 위반", 문장));

    const failure = await failureOf(post(ANALYSIS, PLAN));

    expect(failure.problem.detail).toBe(문장);
    expect(failure.problem.title).toBe("도메인 규칙 위반");
  });

  it("400 과 422 는 화면에 같은 뜻이므로 같은 갈래가 된다", async () => {
    server.use(failing(400, "값이 유효하지 않다", "가격은 음수일 수 없다"));
    const 값오류 = await failureOf(post(ANALYSIS, PLAN));

    server.use(failing(422, "도메인 규칙 위반", "비중의 합이 100 이 아니다"));
    const 규칙위반 = await failureOf(post(ANALYSIS, PLAN));

    expect(값오류.problem.kind).toBe("invalid");
    expect(규칙위반.problem.kind).toBe("invalid");
  });

  it("503 은 unavailable 로 갈린다 — 화면은 그 블록만 끈다", async () => {
    server.use(failing(503, "외부 데이터를 가져오지 못했다", "거래소에 닿지 못했다"));

    const failure = await failureOf(post(ANALYSIS, PLAN));

    expect(failure.problem.kind).toBe("unavailable");
  });

  it("404 는 notFound 로 갈린다 — 화면은 목록으로 되돌린다", async () => {
    server.use(
      http.get(origin + "/api/trades/no-such-trade", () =>
        HttpResponse.json(problem(404, "대상을 찾을 수 없다", "그런 거래가 없다"), { status: 404 }),
      ),
    );

    const failure = await failureOf(get("/api/trades/{id}", { path: { id: "no-such-trade" } }));

    expect(failure.problem.kind).toBe("notFound");
    expect(failure.problem.detail).toBe("그런 거래가 없다");
  });

  it("ProblemDetail 이 아닌 본문이 와도 화면이 볼 수 있는 오류가 된다", async () => {
    server.use(
      http.post(origin + ANALYSIS, () =>
        HttpResponse.text("<html>Gateway Timeout</html>", { status: 504 }),
      ),
    );

    const failure = await failureOf(post(ANALYSIS, PLAN));

    expect(failure.problem.kind).toBe("unknown");
    expect(failure.problem.detail).not.toBe("");
  });

  it("서버에 닿지 못한 것도 오류 하나로 올라온다", async () => {
    server.use(http.post(origin + ANALYSIS, () => HttpResponse.error()));

    const failure = await failureOf(post(ANALYSIS, PLAN));

    expect(failure.problem.kind).toBe("unavailable");
    expect(failure.problem.status).toBe(0);
  });

  it("경로 변수를 채우지 않으면 요청 자체를 보내지 않는다", async () => {
    // 핸들러를 세우지 않는다. 요청이 나갔다면 onUnhandledRequest 가 이 테스트를 실패시킨다.
    await expect(get("/api/trades/{id}")).rejects.toThrow("경로 변수가 채워지지 않았다");
  });

  it("조회 조건 중 값이 없는 것은 질의 문자열에 실리지 않는다", async () => {
    let asked = "";
    server.use(
      http.get(origin + "/api/trades", ({ request }) => {
        asked = new URL(request.url).search;
        return HttpResponse.json([]);
      }),
    );

    await get("/api/trades", { query: { direction: "LONG", closeReason: undefined } });

    expect(asked).toBe("?direction=LONG");
  });
});
