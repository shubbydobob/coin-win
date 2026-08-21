import openapiTS, { astToString } from "openapi-typescript";
import { describe, expect, it } from "vitest";

import documentJson from "../../openapi.json?raw";
import committed from "./schema.d.ts?raw";

/**
 * 생성물 둘을 커밋한 대가로 "손으로 쓴 사본" 과 똑같은 문제를 얻지 않기 위한 검사.
 *
 * openapi.json 이 최신인지는 자바 쪽 OpenApiSchemaFreshnessTest 가 본다. 여기서 보는 것은
 * 사슬의 다음 칸이다 — 스키마를 다시 만들고 타입 생성을 잊으면 tsc 는 여전히 통과하고
 * 런타임에만 틀린다.
 *
 * CLI 가 붙이는 머리말은 비교에서 뺀다. 본문이 같은지가 이 검사의 내용이다.
 */
const BODY_START = "export interface paths";

describe("생성된 타입", () => {
  it("커밋된 schema.d.ts 가 openapi.json 과 같다", async () => {
    const ast = await openapiTS(JSON.parse(documentJson) as never);

    expect(committed.slice(committed.indexOf(BODY_START))).toBe(astToString(ast));
  });
});
