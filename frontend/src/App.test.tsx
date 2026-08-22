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

  it("모든 화면이 자기 URL 을 갖는다", () => {
    열기("/plan");

    for (const 이름 of ["현황", "계획", "기록", "백테스트", "복리"]) {
      expect(screen.getByRole("link", { name: 이름 })).toBeVisible();
    }
  });
});
