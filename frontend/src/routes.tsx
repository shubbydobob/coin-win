import type { RouteObject } from "react-router";

import { App } from "./App";
import { BacktestScreen } from "./features/backtest/BacktestScreen";
import { OverviewScreen } from "./features/overview/OverviewScreen";
import { JournalScreen } from "./features/journal/JournalScreen";
import { PlanScreen } from "./features/plan/PlanScreen";
import { ProjectionScreen } from "./features/projection/ProjectionScreen";

/**
 * 화면의 정체는 그 URL 이다. 새로고침해도 같은 화면이 나와야 하고, 그러려면 라우터가 화면
 * 하나짜리일 때부터 있어야 한다 — 나중에 얹으면 화면들이 이미 URL 없이 자란 뒤다.
 *
 * `/` 는 현황이다. 앞의 것들을 재사용해 마지막에 얇게 지었다(§ 12 의 12단계).
 */
export const routes: RouteObject[] = [
  {
    path: "/",
    element: <App />,
    children: [
      { index: true, element: <OverviewScreen /> },
      { path: "plan", element: <PlanScreen /> },
      { path: "journal", element: <JournalScreen /> },
      { path: "backtest", element: <BacktestScreen /> },
      { path: "projection", element: <ProjectionScreen /> },
    ],
  },
];
