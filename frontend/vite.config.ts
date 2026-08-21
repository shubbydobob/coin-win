import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// 백엔드는 8080. dev 서버가 /api 와 /v3 를 프록시하므로 브라우저에서는 같은 오리진이 된다.
// 그래서 백엔드에 CORS 설정을 넣지 않는다 — 운영에 없는 설정이 개발에만 존재하게 된다.
// 근거: docs/spec/phase8-frontend.md § 9.2
const backend = "http://localhost:8080";

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
