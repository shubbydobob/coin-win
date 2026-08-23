import { BacktestScreen } from "../features/backtest/BacktestScreen";
import { ProjectionScreen } from "../features/projection/ProjectionScreen";
import { Section } from "./Section";

/**
 * 검증. **이 규칙이 통하는가.**
 *
 * 둘 다 실제 돈이 걸리지 않은 자리다 — 백테스트는 과거로 재고, 복리는 같은 조건을 여러 번
 * 반복해서 잰다. 그래서 한 탭이다.
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
        hint="같은 기댓값에서도 경로에 따라 결과가 갈린다. 그 갈림이 이 화면의 요점이다."
      >
        <ProjectionScreen />
      </Section>
    </div>
  );
}
