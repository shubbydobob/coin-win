import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider, createBrowserRouter } from "react-router";

import { routes } from "./routes";
import "./index.css";

const root = document.getElementById("root");
if (!root) {
  throw new Error("#root 가 없다. index.html 이 바뀌었는가");
}

/**
 * 서버 상태는 전부 여기 산다. 전역 스토어를 두지 않는 이유는 이 앱의 상태가 전부 서버에 있기
 * 때문이다 — 스토어를 두면 서버의 사본이 하나 생기고, 그 사본이 언제 낡는지를 관리하는 코드가
 * 화면 수만큼 생긴다(§ 2).
 */
const queryClient = new QueryClient();

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={createBrowserRouter(routes)} />
    </QueryClientProvider>
  </StrictMode>,
);
