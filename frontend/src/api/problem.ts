import type { components } from "./schema";

/**
 * 오류 본문의 타입. 손으로 쓰지 않고 백엔드가 낸 스키마에서 가져온다 —
 * `DomainExceptionHandler` 가 내는 모양이 바뀌면 여기가 먼저 깨진다.
 */
type ProblemDetail = components["schemas"]["ProblemDetail"];

/**
 * 화면이 분기해도 되는 유일한 축.
 *
 * 상태 코드 자체를 화면에 넘기지 않는다. 넘기면 화면마다 `res.status === 422` 같은 조건이
 * 생기고, 그것이 명세 § 7 이 `client.ts` 한 곳에서만 판별하라고 한 이유다.
 *
 * `400` 과 `422` 가 같은 `invalid` 인 것은 의도다 — 사용자에게는 둘 다 "고쳐서 다시" 이고,
 * 구분이 필요한 것은 부르는 코드지 사람이 아니다.
 */
export type ProblemKind = "invalid" | "notFound" | "unavailable" | "unknown";

export interface Problem {
  readonly kind: ProblemKind;
  readonly status: number;
  /** 서버가 쓴 짧은 분류. 없을 수 있으므로 화면은 비어 있으면 감춘다. */
  readonly title: string;
  /** 도메인이 쓴 문장. 화면은 이것을 가공 없이 그대로 보여준다(§ 7). */
  readonly detail: string;
}

export class ApiFailure extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail);
    this.name = "ApiFailure";
    this.problem = problem;
  }
}

/**
 * 프론트가 만드는 문장은 이 둘뿐이고, 둘 다 서버 문장이 존재하지 않는 경우다.
 *
 * 명세 § 7 은 "프론트는 오류 문장을 짓지 않는다" 를 규칙으로 두면서 **오류에는 언제나 서버
 * 문장이 있다** 를 전제했다. 그 전제가 성립하지 않는 자리가 둘 있다 — 요청이 서버에 닿지도
 * 못한 경우와, 응답이 우리 어드바이스를 거치지 않아 `ProblemDetail` 이 아닌 경우다.
 * 도메인 규칙의 표현이 아니라 **전송 계층의 사실**이므로 두 곳으로 갈라질 일이 없다.
 */
const UNREACHABLE = "서버에 닿지 못했다";

const UNREADABLE = "서버가 오류를 냈지만 내용을 읽을 수 없다";

const NO_STATUS = 0;

export function unreachable(): Problem {
  return { kind: "unavailable", status: NO_STATUS, title: "", detail: UNREACHABLE };
}

export function toProblem(status: number, body: unknown): Problem {
  if (!isProblemDetail(body)) {
    return { kind: kindOf(status), status, title: "", detail: UNREADABLE };
  }
  return {
    kind: kindOf(status),
    status,
    title: typeof body.title === "string" ? body.title : "",
    detail: body.detail,
  };
}

/**
 * `detail` 하나만 본다. 우리 어드바이스는 title·status·instance 까지 채우지만, 그 앞단(프록시·
 * 서블릿 컨테이너)이 낸 본문에도 문장이 있으면 그것을 버릴 이유가 없다.
 */
function isProblemDetail(body: unknown): body is ProblemDetail {
  return (
    typeof body === "object" &&
    body !== null &&
    typeof (body as { detail?: unknown }).detail === "string"
  );
}

function kindOf(status: number): ProblemKind {
  switch (status) {
    case 400:
    case 422:
      return "invalid";
    case 404:
      return "notFound";
    case 503:
      return "unavailable";
    default:
      return "unknown";
  }
}
