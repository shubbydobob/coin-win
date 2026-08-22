import { screen } from "@testing-library/react";
import { RouterProvider, createMemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";

import { renderScreen } from "../test/render";
import { routes } from "./routes";

function 열기(path: string) {
  renderScreen(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

describe("앱 껍데기", () => {
  it("프로젝트 이름이 화면에 나온다", () => {
    열기("/plan");

    expect(screen.getByRole("heading", { name: "CoinWin" })).toBeVisible();
  });

  it("계획 계산기는 자기 URL 을 갖는다", () => {
    열기("/plan");

    expect(screen.getByRole("button", { name: "계산" })).toBeVisible();
  });

  it("아직 없는 현황 화면 대신 계획 계산기로 보낸다", () => {
    열기("/");

    expect(screen.getByRole("button", { name: "계산" })).toBeVisible();
  });
});
