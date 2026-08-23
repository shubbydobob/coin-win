import { OverviewScreen } from "../features/overview/OverviewScreen";
import { WatchScreen } from "../features/watch/WatchScreen";
import { Section } from "./Section";

/**
 * 지금. **내 포지션과 시장이 한 화면에 있다.**
 *
 * 둘을 합친 이유는 같은 순간을 묻기 때문이다 — "내가 무엇을 들고 있나" 와 "지금 무슨 일이
 * 벌어지고 있나" 는 따로 볼 때보다 나란히 볼 때 답이 된다. 미실현 손익이 흔들리는 이유가
 * 바로 아래 호가와 이상치에 있다.
 *
 * 내 포지션을 위에 둔다. **돈이 걸린 쪽이 먼저다.**
 */
export function NowScreen() {
  return (
    <div className="space-y-8">
      <Section title="내 포지션" hint="기록과 거래소를 나란히 놓는다. 어긋나면 드러난다.">
        <OverviewScreen />
      </Section>

      <Section
        title="시장"
        hint="지금 무슨 일이 벌어지고 있고 무엇이 예정돼 있나. 무엇을 하라고는 말하지 않는다."
      >
        <WatchScreen />
      </Section>
    </div>
  );
}
