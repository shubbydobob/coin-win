import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StopLossGuard } from "./StopLossGuard";
import type { components } from "../../api/schema";

type Protection = components["schemas"]["PositionProtectionResponse"];

const 기본: Protection = {
  direction: "SHORT",
  coverage: "NONE",
  quantity: 0.114,
  stoppedQuantity: 0,
  plannedStopLoss: null,
  takeProfitWithoutStopLoss: false,
  exposureWithoutStop: null,
  orders: [],
};

const 판정 = (덮어쓰기: Partial<Protection> = {}): Protection => ({ ...기본, ...덮어쓰기 });

describe("손절 보호", () => {
  it("손절이 없으면 없다고 말한다", () => {
    render(<StopLossGuard protection={판정()} />);

    expect(screen.getByText("손절이 걸려 있지 않다")).toBeInTheDocument();
  });

  /**
   * 있어야 할 손절가는 기록된 계획에서만 온다. 지어낸 수를 놓으면 사람이 그것을 기준으로
   * 읽는다 — 손절 거리는 이 저장소가 아직 잰 적이 없다.
   */
  it("계획이 없으면 있어야 할 손절가를 말하지 않는다", () => {
    render(<StopLossGuard protection={판정()} />);

    expect(screen.getByText(/있어야 할 손절가를 말할 수 없다/)).toBeInTheDocument();
    expect(screen.queryByText(/계획한 손절가/)).not.toBeInTheDocument();
  });

  it("계획이 있으면 손절가와 거기까지 갔을 때 잃는 돈을 적는다", () => {
    render(
      <StopLossGuard
        protection={판정({ plannedStopLoss: 65280, exposureWithoutStop: 183.24 })}
      />,
    );

    expect(screen.getByText("65,280.00")).toBeInTheDocument();
    expect(screen.getByText("−183.24")).toBeInTheDocument();
  });

  /** 아무것도 안 건 것과 다른 사실이다 — 버는 쪽만 준비하고 잃는 쪽을 비워 둔 것이다. */
  it("익절만 걸려 있는 것을 따로 말한다", () => {
    render(<StopLossGuard protection={판정({ takeProfitWithoutStopLoss: true })} />);

    expect(screen.getByText(/익절만 걸려 있다/)).toBeInTheDocument();
  });

  it("수량이 모자라면 얼마만 덮는지 적는다", () => {
    render(<StopLossGuard protection={판정({ coverage: "PARTIAL", stoppedQuantity: 0.05 })} />);

    expect(screen.getByText(/0.05000000 BTC 만 덮는다/)).toBeInTheDocument();
    expect(screen.getByText(/보유 0.11400000 BTC/)).toBeInTheDocument();
  });

  it("전량이 덮여 있으면 경고가 아니라 확인이다", () => {
    render(
      <StopLossGuard
        protection={판정({
          coverage: "FULL",
          stoppedQuantity: 0.114,
          orders: [{ kind: "STOP_LOSS", triggerPrice: 65280, quantity: null }],
        })}
      />,
    );

    expect(screen.getByText(/손절 걸려 있다 — 전량\. 65,280\.00/)).toBeInTheDocument();
    expect(screen.queryByText(/걸려 있지 않다/)).not.toBeInTheDocument();
  });

  /** 추격 손절은 트리거가 미리 정해져 있지 않다. 0 으로 적으면 "지금 당장" 으로 읽힌다. */
  it("추격 손절은 값 대신 추격이라고 적는다", () => {
    render(
      <StopLossGuard
        protection={판정({
          coverage: "FULL",
          stoppedQuantity: 0.114,
          orders: [{ kind: "TRAILING_STOP", triggerPrice: null, quantity: null }],
        })}
      />,
    );

    expect(screen.getByText(/손절 걸려 있다 — 전량\. 추격/)).toBeInTheDocument();
  });

  /**
   * **침묵이 "손절이 있다" 로 읽히면 안 된다.** 이 화면에서 가장 나쁜 고장은 경고가 안 뜨는
   * 것이므로, 못 읽었을 때 아무것도 안 그리는 선택지는 없다.
   */
  it("주문을 못 읽었으면 모른다고 말한다", () => {
    render(<StopLossGuard failed />);

    expect(screen.getByText(/걸려 있다는 뜻이 아니다/)).toBeInTheDocument();
  });
});
