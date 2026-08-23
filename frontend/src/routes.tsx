import type { RouteObject } from "react-router";
import { Navigate } from "react-router";

import { App } from "./App";
import { NowScreen } from "./screens/NowScreen";
import { TradeScreen } from "./screens/TradeScreen";
import { VerifyScreen } from "./screens/VerifyScreen";

/**
 * 화면의 정체는 그 URL 이다. 새로고침해도 같은 화면이 나와야 하고, 그러려면 라우터가 화면
 * 하나짜리일 때부터 있어야 한다 — 나중에 얹으면 화면들이 이미 URL 없이 자란 뒤다.
 *
 * **탭이 여섯에서 셋이 됐다.** 무엇을 묻는가로 갈랐다 — 지금 / 매매 / 검증. 옛 경로는
 * 지우지 않고 새 자리로 보낸다. **북마크와 브라우저 기록이 이미 그 주소를 갖고 있고**,
 * 404 로 맞이하는 것은 "여기 없다" 가 아니라 "무엇이 잘못됐다" 로 읽힌다.
 */
export const routes: RouteObject[] = [
  {
    path: "/",
    element: <App />,
    children: [
      { index: true, element: <NowScreen /> },
      { path: "trade", element: <TradeScreen /> },
      { path: "verify", element: <VerifyScreen /> },

      // 옛 경로. 합치기 전의 여섯 탭이 가리키던 자리다.
      { path: "watch", element: <Navigate to="/" replace /> },
      { path: "plan", element: <Navigate to="/trade" replace /> },
      { path: "journal", element: <Navigate to="/trade" replace /> },
      { path: "backtest", element: <Navigate to="/verify" replace /> },
      { path: "projection", element: <Navigate to="/verify" replace /> },
    ],
  },
];
