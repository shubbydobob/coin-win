import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// 백엔드는 8080. dev 서버가 /api 와 /v3 를 프록시하므로 브라우저에서는 같은 오리진이 된다.
// 그래서 백엔드에 CORS 설정을 넣지 않는다 — 운영에 없는 설정이 개발에만 존재하게 된다.
// 근거: docs/spec/phase8-frontend.md § 9.2
//
// COINWIN_BACKEND 로 바꿀 수 있다. **git worktree 를 쓰면 앱이 둘 뜨기 때문이다** — 두 트리가
// 같은 8080 을 두고 다투면 나중에 뜬 쪽이 죽고, 그러면 어느 트리의 코드를 보고 있는지 알 수
// 없다. 기본값은 그대로라 평소에는 아무것도 달라지지 않는다.
const backend = process.env.COINWIN_BACKEND ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": backend,
      "/v3": backend,
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}", "test/**/*.test.{ts,tsx}"],
  },
});
