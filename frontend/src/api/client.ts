import { ApiFailure, toProblem, unreachable } from "./problem";
import type { paths } from "./schema";

/**
 * 서버와 이야기하는 유일한 자리.
 *
 * 두 가지를 여기 가둔다 — **응답 판별**(§ 7)과 **경로·본문 타입**(§ 5). 화면 코드가
 * `res.status` 를 보는 일도, 응답 타입을 손으로 적는 일도 없어야 한다. 손으로 적는 순간
 * 백엔드 DTO 가 바뀌어도 컴파일이 통과하고 런타임에만 틀린다.
 *
 * 경로 문자열은 생성된 `paths` 의 키로만 받는다. 오타는 컴파일 오류이고, 요청 본문과 응답
 * 타입은 그 경로에서 자동으로 따라온다.
 */
type Method = "get" | "post";

type OperationOf<P extends keyof paths, M extends Method> =
  paths[P] extends Record<M, infer Operation> ? Operation : never;

/** 그 메서드를 실제로 가진 경로만. 없는 메서드는 생성 타입에서 `get?: never` 로 나온다. */
type PathsWith<M extends Method> = {
  [P in keyof paths]: paths[P] extends Record<M, unknown> ? P : never;
}[keyof paths];

export type GetPath = PathsWith<"get">;

export type PostPath = PathsWith<"post">;

// 성공 응답 본문. springdoc 이 내는 content type 은 전부 와일드카드 하나다.
// 오류 쪽만 application/problem+json 으로 갈라져 있고, 그것은 여기서 보지 않는다.
type SuccessBody<Operation> = Operation extends { responses: { 200: { content: { "*/*": infer T } } } }
  ? T
  : Operation extends { responses: { 201: { content: { "*/*": infer T } } } }
    ? T
    : never;

type RequestBody<Operation> = Operation extends {
  requestBody: { content: { "application/json": infer T } };
}
  ? T
  : never;

/**
 * `/api/trades/{id}/fills` 같은 경로에서 `{id}` 를 뽑아 낸다.
 *
 * 값은 전부 문자열이다 — 경로에 실리는 순간 문자열이므로 그 이상 좁힐 것이 없다.
 */
type PathParams<P extends string> = P extends `${string}{${infer Key}}${infer Rest}`
  ? Record<Key, string> & PathParams<Rest>
  : Record<never, string>;

type Query = Readonly<Record<string, string | number | boolean | undefined>>;

export interface RequestOptions<P extends string> {
  readonly path?: PathParams<P>;
  readonly query?: Query;
  readonly signal?: AbortSignal;
}

export async function get<P extends GetPath>(
  path: P,
  options: RequestOptions<P & string> = {},
): Promise<SuccessBody<OperationOf<P, "get">>> {
  return send(path, options, { method: "GET" });
}

export async function post<P extends PostPath>(
  path: P,
  body: RequestBody<OperationOf<P, "post">>,
  options: RequestOptions<P & string> = {},
): Promise<SuccessBody<OperationOf<P, "post">>> {
  return send(path, options, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

async function send<T>(
  path: string,
  options: RequestOptions<string>,
  init: RequestInit,
): Promise<T> {
  const response = await call(target(path, options), { ...init, signal: options.signal });
  if (!response.ok) {
    throw new ApiFailure(toProblem(response.status, await body(response)));
  }
  return (await response.json()) as T;
}

/**
 * 서버에 닿지 못한 것도 화면에서는 오류 하나다. 취소는 오류가 아니므로 그대로 올려 보낸다 —
 * 여기서 삼키면 화면 전환으로 취소된 요청이 "서버에 닿지 못했다" 로 보인다.
 */
async function call(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (cause) {
    if (init.signal?.aborted) {
      throw cause;
    }
    throw new ApiFailure(unreachable());
  }
}

async function body(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function target(path: string, options: RequestOptions<string>): string {
  const url = new URL(fill(path, options.path), window.location.origin);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

/**
 * 경로 변수를 채운다. 안 채워진 채로 나가면 `{id}` 를 식별자로 조회해 404 가 되고, 화면에는
 * "그런 거래가 없다" 가 뜬다 — 원인과 무관한 문장이다. 요청 전에 세운다.
 */
function fill(path: string, params?: Readonly<Record<string, string>>): string {
  const filled = path.replace(/\{(\w+)\}/g, (whole, key: string) => {
    const value = params?.[key];
    return value === undefined ? whole : encodeURIComponent(value);
  });
  if (filled.includes("{")) {
    throw new Error(`경로 변수가 채워지지 않았다: ${path}`);
  }
  return filled;
}
