// @vitest-environment node
import { ESLint } from "eslint";
import { describe, expect, it } from "vitest";

/**
 * 린트 규칙이 **실제로 발동하는지** 본다.
 *
 * 규칙을 적어 두는 것과 규칙이 무언가를 잡는 것은 다르다. 선택자 오타 하나면 규칙은 조용히
 * 아무것도 매칭하지 않고, 그 상태는 초록으로 보인다 — Phase 0 이 ArchUnit 에 대해 위반
 * 픽스처를 상주시킨 이유가 그것이다.
 *
 * 프로젝트의 `eslint.config.js` 를 그대로 쓴다. 테스트용 설정을 따로 만들면 증명되는 것이
 * 사본이지 게이트가 아니다.
 */
async function 위반(file: string): Promise<string[]> {
  const [result] = await new ESLint().lintFiles([`eslint-fixture/${file}`]);
  return (result?.messages ?? []).map((message) => message.ruleId ?? "");
}

describe("린트가 지키는 세 규칙", () => {
  it("서버가 정한 스케일을 화면이 다시 굴리면 잡는다", async () => {
    const rules = await 위반("rounds-a-response.ts");

    // toFixed · Math.round 는 프로퍼티 규칙, Intl.NumberFormat 은 구문 규칙이 잡는다.
    expect(rules).toContain("no-restricted-properties");
    expect(rules).toContain("no-restricted-syntax");
  });

  it("응답 값을 수로 되돌리면 잡는다", async () => {
    const rules = await 위반("parses-a-response.ts");

    expect(rules.filter((rule) => rule === "no-restricted-syntax")).toHaveLength(2);
  });

  it("feature 끼리 직접 부르면 잡는다", async () => {
    const rules = await 위반("features/backtest/reaches-into-journal.ts");

    expect(rules).toContain("no-restricted-imports");
  });

  it("규칙이 걸리는 자리는 픽스처가 어긴 그 자리뿐이다", async () => {
    // 같은 파일이 세 규칙에 전부 걸리면 선택자가 너무 넓다는 뜻이다.
    const rounding = await 위반("rounds-a-response.ts");
    const parsing = await 위반("parses-a-response.ts");

    expect(rounding).not.toContain("no-restricted-imports");
    expect(parsing).not.toContain("no-restricted-properties");
  });
});
