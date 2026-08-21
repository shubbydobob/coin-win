import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("앱 껍데기", () => {
  it("프로젝트 이름이 화면에 나온다", () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: "CoinWin" })).toBeVisible();
  });
});
