import { useQuery } from "@tanstack/react-query";

import { get } from "../api/client";
import { ApiFailure } from "../api/problem";
import { SmallButton } from "../shared/SmallButton";
import { MyPositionCard } from "../features/overview/MyPositionCard";
import { ReadoutPanel } from "../features/readout/ReadoutPanel";
import { WatchScreen } from "../features/watch/WatchScreen";
import { Section } from "./Section";

const SYMBOL = "BTCUSDT";

/**
 * 계좌를 다시 묻는 주기. **시장 블록과 같은 3초다.**
 *
 * 미실현 손익과 청산까지 남은 거리는 가격이 움직이면 같이 움직인다. 그 둘을 15초마다
 * 갱신하면서 바로 아래 현재가를 3초마다 갱신하면, **같은 화면의 두 수가 다른 시점의
 * 사실이 된다** — "청산까지 7% 남았다" 옆의 가격이 그 계산에 쓰인 가격이 아니다.
 *
 * 서명 요청이 늘지만 감당된다. `BinanceServerClock` 이 거래소 시각을 15분 캐시하므로 폴링
 * 한 번이 왕복 하나다.
 */
const ACCOUNT_POLL_MS = 3_000;

/**
 * 지표를 다시 묻는 주기.
 *
 * **호가보다 느린 이유는 값이 그만큼 자주 달라지지 않기 때문이다.** 가장 짧은 15분봉도 한
 * 봉이 열려 있는 동안 구름과 밴드는 거의 움직이지 않는다 — 3초로 두면 같은 판독을 백 번
 * 받으면서 캔들을 세 주기 분량으로 매번 다시 채우게 된다.
 *
 * 15초는 15분봉 하나의 1% 다. 이 주기에서 판독이 바뀌는 것을 놓치려면 가격이 한 봉 안에서
 * 구름을 넘나들어야 하는데, 그때는 어차피 호가와 현재가가 먼저 말한다.
 */
const READOUT_POLL_MS = 15_000;

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
 * **아래에 있던 두 블록은 걷어냈다.**
 *
 * 「기록과 거래소」가 보여 주던 네 수(평단·수량·청산가·미실현)는 전부 이 카드에 이미 있었고,
 * 그중 청산가와 미실현은 *기록에 대응물이 없어* 애초에 맞댈 상대가 없었다. 되풀이가 아니던
 * 것 하나 — 기록에만 열려 있는 포지션 — 은 이 카드로 옮겼다.
 *
 * 그것을 빼자 「내 포지션」에 남는 것이 진행 중인 거래 목록 하나였는데, **그 목록은 「매매」의
 * 기록 옆에 동작 버튼까지 달고 이미 있다.** 여기 것은 누를 수 없는 사본이었다 — 이 화면이
 * 기록을 고치지 않기로 했기 때문에 그렇게 될 수밖에 없었다.
 *
 * 질의를 여기서 한 번 더 부르는 것이 낭비가 아닌 이유는 **키가 같기 때문이다.** react-query 가
 * 같은 키를 합쳐 주므로 요청은 여전히 각각 하나이고, 폴링 주기도 아래 화면들이 정한 것을
 * 그대로 쓴다.
 */
export function NowScreen() {
  /*
    **폴링이 이 화면으로 옮겨 왔다.** 이 질의를 그리는 것이 「내 자리」 카드뿐이기 때문이다 —
    예전에는 아래 「내 포지션」이 주기를 갖고 있었는데 거기서 그리는 것은 없어졌다.

    비용은 서명 요청 하나다. `BinanceServerClock` 이 거래소 시각을 15분 캐시하므로 폴링 한
    번이 왕복 하나이고, 분당 넷은 개인 엔드포인트 한도에 견줘 무시할 수 있다.

    **탭이 숨겨지면 멈춘다.** `refetchIntervalInBackground` 를 켜지 않은 것이 그 뜻이다.
    **오류가 나면 멈춘다** — 키가 없어서 503 인 경우가 대부분이고 그것은 기다린다고 달라지지
    않는다. 다시 켜는 자리는 사람이 누르는 "다시 시도" 다.
  */
  const positions = useQuery({
    queryKey: ["account", "positions"],
    queryFn: () => get("/api/account/positions"),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : ACCOUNT_POLL_MS),
  });
  /*
    **지표 판독.** 실패해도 다른 블록을 죽이지 않는다 — 이 질의만 거래소에서 캔들을 채우므로
    셋 중 가장 느리고, 가장 먼저 실패할 자리이기도 하다.
  */
  const readout = useQuery({
    queryKey: ["readout", SYMBOL],
    queryFn: () => get("/api/readout/{symbol}", { path: { symbol: SYMBOL } }),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : READOUT_POLL_MS),
  });
  const outliers = useQuery({
    queryKey: ["markets", SYMBOL, "outliers"],
    queryFn: () => get("/api/markets/{symbol}/outliers", { path: { symbol: SYMBOL } }),
    retry: false,
  });

  return (
    <div className="space-y-8">
      {positions.data ? (
        <MyPositionCard
          reconciliation={positions.data}
          outliers={outliers.data}
          onRefresh={() => positions.refetch()}
          refreshing={positions.isFetching}
          failed={Boolean(positions.error)}
        />
      ) : (
        /*
          **폴백을 두지 않는다.** 키가 없을 때 "포지션 없음" 을 그리면 그것은 거짓말이다 —
          비어 있는 것과 알 수 없는 것은 다른 사실이고, 이 기능은 정확히 그 구분을 위해 있다.

          **다시 시도 버튼이 여기로 왔다.** 예전에는 아래 「내 포지션」이 갖고 있었는데 그쪽이
          이 질의를 더는 부르지 않는다. 실패를 보여 주는 자리와 다시 켜는 자리가 갈라져 있으면
          사람은 실패를 본 화면에서 아무것도 할 수 없다.
        */
        <section
          aria-label="내 자리"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface p-4 text-sm"
        >
          <h2 className="text-sm font-medium text-ink">내 자리</h2>
          <p className="text-ink-2">
            {positions.isPending
              ? "거래소에 물어보는 중"
              : positions.error instanceof ApiFailure
                ? positions.error.problem.detail
                : "거래소 포지션을 가져오지 못했다"}
          </p>
          {/* 폴링은 오류에서 멈춰 있다. 이 버튼이 그것을 다시 켜는 유일한 자리다. */}
          {positions.error && (
            <SmallButton onClick={() => positions.refetch()}>다시 시도</SmallButton>
          )}
        </section>
      )}

      {/*
        **지표를 시장보다 위에 놓는다.** 이 화면에서 진입 판단에 가장 가까운 것이 이 표다 —
        아래 「시장」은 지금 무슨 일이 벌어지는가이고, 이쪽은 그 일이 차트의 어디에서
        벌어지는가다. 순서를 뒤집으면 사람이 매번 스크롤로 되짚는다.
      */}
      <Section
        title="지표"
        hint="15분 · 1시간 · 4시간에서 지금 가격이 어디에 서 있나. 무엇을 하라고는 말하지 않는다."
      >
        {readout.data ? (
          <ReadoutPanel readouts={readout.data} />
        ) : (
          <section
            aria-label="지표 판독"
            className="flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface p-3 text-sm"
          >
            <span className="text-ink-2">
              {readout.isPending
                ? "캔들을 받아 지표를 계산하는 중"
                : readout.error instanceof ApiFailure
                  ? readout.error.problem.detail
                  : "지표를 계산하지 못했다"}
            </span>
            {readout.error && (
              <SmallButton onClick={() => readout.refetch()}>다시 시도</SmallButton>
            )}
          </section>
        )}
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
