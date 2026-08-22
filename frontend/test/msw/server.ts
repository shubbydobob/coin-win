import { setupServer } from "msw/node";

/**
 * 화면 테스트는 네트워크 층에서 가로챈다. 그래서 화면은 실제 요청 경로를 그대로 타고,
 * 검증되지 않고 남는 것은 "백엔드가 정말 그 모양으로 응답하는가" 하나뿐이다 — 그것은
 * 스키마 검사가 타입 수준에서 본다(명세 § 10.4).
 *
 * 핸들러를 여기 미리 두지 않는다. 각 테스트가 `server.use` 로 자기 응답만 세우고, 세우지
 * 않은 요청은 `setup.ts` 의 `onUnhandledRequest: "error"` 가 실패로 만든다. 잊고 안 세운
 * 요청이 조용히 통과하는 것이 이 층에서 가장 흔한 거짓 초록이다.
 */
export const server = setupServer();

/** jsdom 이 정한 오리진. 핸들러 경로를 절대 URL 로 적기 위해 쓴다. */
export const origin = window.location.origin;
