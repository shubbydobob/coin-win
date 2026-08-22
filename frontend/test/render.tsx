import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";

/**
 * 화면 하나를 서버 상태 배선과 함께 띄운다.
 *
 * 재시도를 끈다. 켜 두면 오류 케이스가 여러 번 요청하고, 테스트가 실패를 기다리다 느려지거나
 * 타임아웃으로 끝난다 — 재시도는 운영의 정책이지 테스트가 볼 것이 아니다.
 */
export function renderScreen(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}
