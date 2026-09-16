import { money, price, quantity } from "../../format";
import type { components } from "../../api/schema";

type Protection = components["schemas"]["PositionProtectionResponse"];

/**
 * **손절이 걸려 있나.** 규칙 R1 — 손절 없는 포지션은 존재할 수 없다
 * (`docs/spec/exit-automation.md`).
 *
 * 이 저장소를 만든 −2,000 은 신호를 잘못 읽어서 난 것이 아니다. 64,000 숏에 손절이 없었고,
 * 78,000 까지 갔다. 2% 손절 하나였으면 −183 이다. **그 사실이 화면에 없었다** — 현황 화면은
 * 포지션을 보여 주면서 그것이 보호되고 있는지는 말하지 않았다.
 *
 * **판정은 전부 서버가 냈다.** 추격 손절을 손절로 칠지, 수량이 모자란 것을 어떻게 볼지가
 * 화면에 있으면 규칙이 두 곳에 생긴다(`docs/adr/020`).
 *
 * **모르는 것과 없는 것을 가른다.** 주문을 못 읽었을 때 아무것도 안 그리면 그 침묵이
 * "손절이 걸려 있다" 로 읽힌다 — 이 화면에서 가장 나쁜 고장은 경고가 안 뜨는 것이다.
 */
export function StopLossGuard({
  protection,
  failed = false,
}: {
  protection?: Protection;
  /** 주문을 읽지 못했다. 손절이 없다는 뜻이 아니다. */
  failed?: boolean;
}) {
  if (!protection) {
    return (
      <p className="mt-3 border-t border-line-soft pt-2 text-xs text-ink-3">
        {failed
          ? "손절이 걸려 있는지 확인하지 못했다 — 걸려 있다는 뜻이 아니다."
          : "손절이 걸려 있는지 확인하는 중"}
      </p>
    );
  }

  if (protection.coverage === "FULL") {
    return (
      <p className="mt-3 border-t border-line-soft pt-2 text-xs text-ink-2">
        손절 걸려 있다 — 전량. {트리거말(protection)}
      </p>
    );
  }

  return (
    <div className="mt-3 rounded-md border border-warn/50 bg-warn/5 p-2.5 text-xs">
      <p className="font-medium text-warn">{제목(protection)}</p>
      {protection.takeProfitWithoutStopLoss && (
        <p className="mt-1 text-ink-2">익절만 걸려 있다 — 버는 쪽만 준비돼 있다.</p>
      )}
      <Planned protection={protection} />
    </div>
  );
}

/**
 * 있어야 할 손절가와, 거기까지 갔을 때 잃는 돈.
 *
 * **지어내지 않는다.** 기록에 계획이 있으면 그 손절가를 그대로 적고 없으면 없다고 적는다 —
 * 손절 거리의 기본값은 이 저장소가 아직 잰 적이 없고(`exit-automation.md` § 4), 재지 않은
 * 수를 여기 놓으면 사람이 그것을 기준으로 읽는다.
 */
function Planned({ protection }: { protection: Protection }) {
  if (protection.plannedStopLoss === null) {
    return (
      <p className="mt-1 text-ink-2">
        기록에 계획이 없어 있어야 할 손절가를 말할 수 없다 — 앱 밖에서 연 포지션이다.
      </p>
    );
  }

  return (
    <p className="mt-1 tabular-nums text-ink-2">
      계획한 손절가 <span className="text-ink">{price(protection.plannedStopLoss)}</span>
      {protection.exposureWithoutStop !== null && (
        <>
          {" · 덮이지 않은 만큼이 여기까지 가면 "}
          <span className="text-ink">−{money(protection.exposureWithoutStop)}</span> USDT
        </>
      )}
    </p>
  );
}

function 제목(protection: Protection): string {
  if (protection.coverage === "NONE") {
    return "손절이 걸려 있지 않다";
  }
  return `손절이 ${quantity(protection.stoppedQuantity)} BTC 만 덮는다 — 보유 ${quantity(protection.quantity)} BTC`;
}

/** 걸려 있는 손절의 트리거. 추격 손절은 미리 정해진 값이 없어 그렇게 적는다. */
function 트리거말(protection: Protection): string {
  const 손절 = protection.orders.filter((order) => order.kind !== "TAKE_PROFIT");
  const 값 = 손절.map((order) =>
    order.triggerPrice === null ? "추격" : price(order.triggerPrice),
  );
  return 값.join(" · ");
}
