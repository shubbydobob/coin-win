import { JournalScreen } from "../features/journal/JournalScreen";
import { PlanScreen } from "../features/plan/PlanScreen";
import { Section } from "./Section";

/**
 * 매매. **얼마나 걸 것인가 → 무엇을 했나.**
 *
 * 계획과 기록을 한 탭에 둔 이유는 <b>같은 하나의 흐름</b>이기 때문이다. 진입 전에 수량을
 * 계산하고, 그 계획을 저장하고, 체결과 청산을 적고, 집계로 되돌아본다. 탭이 갈라져 있으면
 * 계산한 값을 다른 화면에서 다시 쳐야 한다.
 *
 * **집계가 여기 있다.** 예전에는 현황에도 같은 집계가 있었는데, "계획을 어겨서 얻은 것" 이
 * 음수라는 사실은 <b>기록 바로 옆</b>에서 읽혀야 다음에 무엇을 고칠지로 이어진다.
 */
export function TradeScreen() {
  return (
    <div className="space-y-8">
      <Section
        title="계획"
        hint="수량은 입력이 아니라 손절가와 리스크 비율이 내놓는 답이다."
      >
        <PlanScreen />
      </Section>

      <Section title="기록" hint="계획 저장 → 체결 → 청산. 그리고 집계.">
        <JournalScreen />
      </Section>
    </div>
  );
}
