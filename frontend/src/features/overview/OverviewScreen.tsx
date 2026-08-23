import { useQuery } from "@tanstack/react-query";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { ActiveTrades } from "../../shared/ActiveTrades";
import { PositionReconciliationPanel } from "../../shared/PositionReconciliation";
import { SmallButton } from "../../shared/SmallButton";

/**
 * 계좌를 다시 묻는 주기.
 *
 * **비용은 서명 요청 하나다.** `BinanceServerClock` 이 거래소 시각을 15분 캐시하므로 폴링 한
 * 번이 왕복 하나이고, 분당 넷은 개인 엔드포인트 한도에 견줘 무시할 수 있다.
 *
 * **탭이 숨겨지면 멈춘다.** `refetchIntervalInBackground` 를 켜지 않은 것이 그 뜻이다 — 아무도
 * 보고 있지 않은 화면 때문에 서명 요청을 계속 쓸 이유가 없다.
 *
 * **오류가 나면 멈춘다.** 키가 없어서 503 인 경우가 대부분인데 그것은 기다린다고 달라지지
 * 않고, 15초마다 같은 실패를 되풀이하면 로그가 그 실패로 덮인다. 다시 켜는 자리는 사람이
 * 누르는 "다시 시도" 다.
 */
const ACCOUNT_POLL_MS = 15_000;

/**
 * 내 포지션. **지금 무엇이 열려 있는가에만 답한다.**
 *
 * 진행 중인 거래에 동작 버튼을 두지 않는다. 기록을 고치는 자리는 「매매」이고, 여기서도 되면
 * 같은 일을 두 곳에서 하게 된다.
 *
 * **시장 지표와 집계가 여기 없다.** 여섯 탭을 셋으로 합치면서 걷어낸 두 자리다 —
 * 펀딩비·미결제약정·롱숏비율은 같은 탭의 이상치 블록이 **같은 세 지표를 평소와 견주어**
 * 보여 주므로 이쪽은 덜 아는 사본이었고, 집계는 「매매」의 기록 옆에 있어야 "무엇을 고칠까"
 * 로 이어진다.
 */
export function OverviewScreen() {
  const active = useQuery({
    queryKey: ["trades", "active"],
    queryFn: () => get("/api/trades/active"),
  });
  // 거래소 계정은 키가 있어야 붙는다. 없으면 503 이고, 그때는 이 블록만 비활성이다.
  // 재시도하지 않는다 — 키가 없는 것은 기다린다고 달라지지 않는다.
  const positions = useQuery({
    queryKey: ["account", "positions"],
    queryFn: () => get("/api/account/positions"),
    retry: false,
    // 이 블록에서 유일하게 **매 순간 달라지는** 값이라 혼자 폴링한다. 미실현 손익과 청산가는
    // 가격이 움직이면 같이 움직이고, 멈춘 수를 띄워 두면 사람이 그것을 현재로 읽는다.
    refetchInterval: (query) => (query.state.error ? false : ACCOUNT_POLL_MS),
  });

  return (
    <div className="space-y-4">
      <section aria-label="진행 중">{active.data && <ActiveTrades trades={active.data} />}</section>

      {positions.data ? (
        <PositionReconciliationPanel
          reconciliation={positions.data}
          onRefresh={() => positions.refetch()}
          refreshing={positions.isFetching}
        />
      ) : (
        positions.error && (
          // 오류일 때도 이름표를 붙인다. 이 탭에는 "다시 시도" 가 여럿이고, 영역 이름이
          // 없으면 사람도 스크린리더도 어느 쪽을 누르는지 알 수 없다.
          <section aria-label="거래소 대조" className="flex items-center gap-3 text-sm">
            <span className="text-ink-2">
              {positions.error instanceof ApiFailure
                ? positions.error.problem.detail
                : "거래소 포지션을 가져오지 못했다"}
            </span>
            {/* 폴링은 오류에서 멈춰 있다. 이 버튼이 그것을 다시 켜는 유일한 자리다. */}
            <SmallButton onClick={() => positions.refetch()}>다시 시도</SmallButton>
          </section>
        )
      )}
    </div>
  );
}
