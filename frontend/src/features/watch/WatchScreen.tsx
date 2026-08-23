import { useQuery } from "@tanstack/react-query";
import type { UseQueryResult } from "@tanstack/react-query";

import { get } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import { SmallButton } from "../../shared/SmallButton";
import { EventCalendarPanel } from "./EventCalendarPanel";
import { MacroPanel } from "./MacroPanel";
import { NoticePanel } from "./NoticePanel";
import { OrderBookPanel } from "./OrderBookPanel";
import { OutlierPanel } from "./OutlierPanel";
import { TickerHeader } from "./TickerHeader";

const SYMBOL = "BTCUSDT";

/**
 * 호가를 다시 묻는 주기. 이 화면에서 초 단위로 달라지는 유일한 값이다.
 *
 * 3초면 분당 20회, 가중치로 60 이다. 바이낸스 한도 2,400/분에 견줘 2.5% 다.
 * 근거: `docs/spec/market-watch.md` § 4
 */
const BOOK_POLL_MS = 3_000;

/** 이상치의 원천 데이터가 5분 주기다. 그보다 자주 물어도 같은 답이 온다. */
const OUTLIER_POLL_MS = 300_000;

/** 공지는 하루에 몇 건이다. 1분이면 충분히 빠르다. */
const NOTICE_POLL_MS = 60_000;

/**
 * 감시. **지금 무슨 일이 벌어지고 있고 무엇이 예정돼 있나.**
 *
 * **이 화면은 손실을 막지 않는다.** 막는 것은 계획 화면의 사이징이다 — 명목이 제대로면 최악이
 * 정해져 있고, 신호를 어떻게 읽든 그 숫자는 변하지 않는다. 이 화면이 하는 일은 급등의 이유를
 * 5분 안에 찾는 것이지 급등을 미리 아는 것이 아니다. 근거: `docs/spec/market-watch.md` § 0
 *
 * **네 블록이 서로 독립적으로 실패한다.** 특히 공지는 문서화되지 않은 엔드포인트를 쓰므로
 * 예고 없이 죽을 수 있고, 그때도 호가와 캘린더는 그대로 보여야 한다.
 *
 * **알림도 소리도 자동 스크롤도 없다.** 경고는 색과 문장까지다 — 그 이상은 `scope.md` 가
 * 금지한 실시간 알림으로 미끄러지는 첫 계단이다.
 */
export function WatchScreen() {
  // 오류에서 멈추고 탭이 숨으면 멈춘다. 계좌 폴링(`OverviewScreen`)과 같은 규칙이다.
  const book = useQuery({
    queryKey: ["markets", SYMBOL, "orderbook"],
    queryFn: () => get("/api/markets/{symbol}/orderbook", { path: { symbol: SYMBOL } }),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : BOOK_POLL_MS),
  });
  const outliers = useQuery({
    queryKey: ["markets", SYMBOL, "outliers"],
    queryFn: () => get("/api/markets/{symbol}/outliers", { path: { symbol: SYMBOL } }),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : OUTLIER_POLL_MS),
  });
  // 거시 시세는 24시간 변동률이라 자주 물을 이유가 없다. 5분이면 충분히 최신이다.
  const macro = useQuery({
    queryKey: ["markets", "macro"],
    queryFn: () => get("/api/markets/macro"),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : OUTLIER_POLL_MS),
  });
  const calendar = useQuery({
    queryKey: ["watch", "events"],
    queryFn: () => get("/api/watch/events"),
    retry: false,
  });
  const notices = useQuery({
    queryKey: ["watch", "notices"],
    queryFn: () => get("/api/watch/notices"),
    retry: false,
    refetchInterval: (query) => (query.state.error ? false : NOTICE_POLL_MS),
  });

  return (
    <div className="space-y-4">
      {book.data ? (
        <>
          <TickerHeader
            book={book.data}
            refreshing={book.isFetching}
            onRefresh={() => book.refetch()}
          />
          <OrderBookPanel book={book.data} />
        </>
      ) : (
        <Failed label="현재가" query={book} fallback="호가를 가져오지 못했다" />
      )}

      {outliers.data ? (
        <OutlierPanel outliers={outliers.data} />
      ) : (
        <Failed label="이상치" query={outliers} fallback="지표 이력을 가져오지 못했다" />
      )}

      {macro.data ? (
        <MacroPanel macro={macro.data} />
      ) : (
        <Failed label="거시 자산" query={macro} fallback="거시 시세를 가져오지 못했다" />
      )}

      {calendar.data ? (
        <EventCalendarPanel calendar={calendar.data} />
      ) : (
        <Failed label="예정 이벤트" query={calendar} fallback="일정표를 읽지 못했다" />
      )}

      {notices.data ? (
        <NoticePanel notices={notices.data} />
      ) : (
        <Failed label="거래소 공지" query={notices} fallback="공지를 가져오지 못했다" />
      )}
    </div>
  );
}

/**
 * 한 블록의 실패. **영역 이름을 붙인다** — 이 화면에는 "다시 시도" 가 넷이고, 이름이 없으면
 * 사람도 스크린리더도 어느 쪽을 누르는지 알 수 없다.
 *
 * 서버가 쓴 문장을 그대로 보여 준다. 화면이 자기 문장으로 바꾸면 규칙의 표현이 두 곳에 생긴다.
 */
function Failed({
  label,
  query,
  fallback,
}: {
  label: string;
  query: UseQueryResult<unknown, Error>;
  fallback: string;
}) {
  return (
    <section
      aria-label={label}
      className="flex items-center gap-3 rounded-lg border border-line bg-surface p-3 text-sm"
    >
      <span className="text-ink-2">
        {query.isPending
          ? "가져오는 중"
          : query.error instanceof ApiFailure
            ? query.error.problem.detail
            : fallback}
      </span>
      {/* 폴링은 오류에서 멈춰 있다. 이 버튼이 다시 켜는 유일한 자리다. */}
      {query.error && <SmallButton onClick={() => query.refetch()}>다시 시도</SmallButton>}
    </section>
  );
}
