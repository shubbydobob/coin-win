import { BacktestScreen } from "../features/backtest/BacktestScreen";
import { CompoundScreen } from "../features/projection/CompoundScreen";
import { Section } from "./Section";

/**
 * 검증. **이 규칙이 통하는가.**
 *
 * 둘 다 실제 돈이 걸리지 않은 자리다 — 백테스트는 과거로 재고, 복리는 목표를 정해 놓고
 * 거기에 무엇이 필요한지를 잰다. 그래서 한 탭이다.
 *
 * **여기서 나온 수를 신호로 읽으면 안 된다.** 이 저장소가 그것을 수치로 확인해 두었다 —
 * 8개월 표에서 손익비 1.33 이던 조합이 7년에서는 0.80 이다(`docs/adr/021`).
 */
export function VerifyScreen() {
  return (
    <div className="space-y-8">
      <Section
        title="백테스트"
        tone="warn"
        hint="한 구간에서 최고 조합을 고르는 것은 측정이 아니라 고르기다. 구간 밖에서 다시 재기 전까지는 아무것도 증명하지 않는다."
      >
        <BacktestScreen />
      </Section>

      <Section
        title="복리"
        hint="지는 거래를 세지 않는다. 모든 거래가 목표대로 끝난다는 가정 위의 산수이므로, 나온 수는 최선의 경우에 필요한 최소치다 — 지는 거래가 섞이면 이기는 거래는 이보다 더 크게 벌어야 한다."
      >
        <CompoundScreen />
      </Section>
    </div>
  );
}
