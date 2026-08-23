import { useQuery } from "@tanstack/react-query";

import { get } from "../api/client";
import { ApiFailure } from "../api/problem";
import { MyPositionCard } from "../features/overview/MyPositionCard";
import { OverviewScreen } from "../features/overview/OverviewScreen";
import { WatchScreen } from "../features/watch/WatchScreen";
import { Section } from "./Section";

const SYMBOL = "BTCUSDT";

/**
 * 지금. **내 포지션과 시장이 한 화면에 있다.**
 *
 * 둘을 합친 이유는 같은 순간을 묻기 때문이다 — "내가 무엇을 들고 있나" 와 "지금 무슨 일이
 * 벌어지고 있나" 는 따로 볼 때보다 나란히 볼 때 답이 된다. 미실현 손익이 흔들리는 이유가
 * 바로 아래 호가와 이상치에 있다.
 *
 * **그런데 나란히 놓는 것만으로는 부족했다.** 두 블록이 같은 탭에 있어도 청산가 84,273 과
 * 현재가 77,560 을 눈으로 오가며 "8% 쯤 남았나" 를 매번 세어야 했고, 내가 숏이라는 사실과
 * 붐비는 쪽이 롱이라는 사실은 스크롤 두 번 떨어져 있었다. **대조가 화면이 아니라 사람
 * 머릿속에서 일어나고 있었다.** 「내 자리」 카드가 그 자리다.
 *
 * **카드가 두 블록을 대신하지 않는다.** 카드는 지금 열려 있는 것만 말하고, 기록과의 좌우
 * 대조는 그 아래 「내 포지션」이 이어서 말한다 — 카드가 요약이고 아래가 원본이다.
 *
 * 질의를 여기서 한 번 더 부르는 것이 낭비가 아닌 이유는 **키가 같기 때문이다.** react-query 가
 * 같은 키를 합쳐 주므로 요청은 여전히 각각 하나이고, 폴링 주기도 아래 화면들이 정한 것을
 * 그대로 쓴다.
 */
export function NowScreen() {
  const positions = useQuery({
    queryKey: ["account", "positions"],
    queryFn: () => get("/api/account/positions"),
    retry: false,
  });
  const outliers = useQuery({
    queryKey: ["markets", SYMBOL, "outliers"],
    queryFn: () => get("/api/markets/{symbol}/outliers", { path: { symbol: SYMBOL } }),
    retry: false,
  });

  return (
    <div className="space-y-8">
      {positions.data ? (
        <MyPositionCard reconciliation={positions.data} outliers={outliers.data} />
      ) : (
        /*
          **폴백을 두지 않는다.** 키가 없을 때 "포지션 없음" 을 그리면 그것은 거짓말이다 —
          비어 있는 것과 알 수 없는 것은 다른 사실이고, 이 기능은 정확히 그 구분을 위해 있다.
          다시 시도 버튼은 아래 「내 포지션」이 갖는다. 여기도 두면 같은 일을 하는 버튼이 둘이다.
        */
        <section aria-label="내 자리" className="rounded-lg border border-line bg-surface p-4 text-sm">
          <h2 className="text-sm font-medium text-ink">내 자리</h2>
          <p className="mt-1 text-ink-2">
            {positions.isPending
              ? "거래소에 물어보는 중"
              : positions.error instanceof ApiFailure
                ? positions.error.problem.detail
                : "거래소 포지션을 가져오지 못했다"}
          </p>
        </section>
      )}

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
