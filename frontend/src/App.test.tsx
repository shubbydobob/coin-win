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
    열기("/trade");

    expect(screen.getByRole("heading", { name: "CoinWin" })).toBeVisible();
  });

  it("계획 계산기는 자기 URL 을 갖는다", () => {
    열기("/trade");

    expect(screen.getByRole("button", { name: "계산" })).toBeVisible();
  });

  it("세 탭이 각각 자기 URL 을 갖는다", () => {
    열기("/trade");

    for (const 이름 of ["지금", "매매", "검증"]) {
      expect(screen.getByRole("link", { name: 이름 })).toBeVisible();
    }
  });

  /**
   * <b>옛 주소를 404 로 맞이하지 않는다.</b> 북마크와 브라우저 기록이 이미 그 주소를 갖고
   * 있고, 404 는 "여기 없다" 가 아니라 "무엇이 잘못됐다" 로 읽힌다.
   */
  it("합치기 전의 주소는 새 자리로 보낸다", () => {
    열기("/plan");
    expect(screen.getByRole("button", { name: "계산" })).toBeVisible();

    열기("/watch");
    expect(screen.getByRole("region", { name: "시장" })).toBeVisible();
  });
});
