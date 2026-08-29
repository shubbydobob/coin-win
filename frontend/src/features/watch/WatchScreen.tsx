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

/**
 * 이상치와 거시 시세를 다시 묻는 주기. **호가와 같은 3초다.**
 *
 * 처음에는 5분이었고 근거는 "이상치의 원천 데이터가 5분 주기라 그보다 자주 물어도 같은 답이
 * 온다" 였다. 그 문장은 지금도 이상치에 대해서는 참이다 — 펀딩비·미결제약정·롱숏비율은
 * 바이낸스가 5분마다 갱신하므로 같은 수를 백 번 더 받게 된다.
 *
 * **그럼에도 3초로 내린 이유는 거시 목록에 비트코인 현물이 들어왔기 때문이다.** 그 값은 매
 * 순간 달라지고, 바로 옆(화면 왼쪽 위)의 무기한가는 3초마다 갱신된다. 두 수의 차이가 곧
 * 베이시스인데 한쪽이 5분 묵으면 **그 차이는 부호까지 뒤집힐 수 있다.** 0.1% 규모의 차이를
 * 재는 데 5분은 너무 길다.
 *
 * **두 질의가 같은 상수를 쓰는 것은 우연이 아니다.** 이상치의 가격 기준선도 같은 화면에서
 * 무기한가와 맞대어 읽히므로 같은 신선도여야 한다. 갈라야 할 이유가 생기면 그때 상수를
 * 둘로 나눈다.
 */
const OUTLIER_POLL_MS = 3_000;

/**
 * 공지를 다시 묻는 주기. **여기만 3초로 내리지 않았다.**
 *
 * 나머지가 전부 3초가 됐는데도 1분인 이유는 신선도가 아니라 **위험**이다. 이 엔드포인트는
 * 문서화된 공개 API 가 아니라 바이낸스 웹사이트가 쓰는 것이고(그래서 호스트도 다르다),
 * 웹사이트 쪽에 분당 20회를 계속 쏘면 IP 가 막힐 수 있다. 그런데 그 IP 는 시세·호가·계좌가
 * 전부 쓰는 같은 IP 다 — **공지 하나를 빨리 보려다 화면 전체를 잃는다.**
 *
 * 그리고 얻는 것이 없다. 상장·점검 공지는 하루에 몇 건이고, 3초 빨리 아는 것이 매매에서
 * 뜻을 갖는 종류의 사실이 아니다.
 */
const NOTICE_POLL_MS = 60_000;

/**
 * 실패한 뒤에 다시 묻는 간격.
 *
 * **처음에는 실패하면 영영 멈췄다**(`error ? false`). 계좌에는 그것이 맞다 — 키가 없어서 나는
 * 503 은 기다린다고 달라지지 않는다. 그런데 **이 화면의 넷은 키가 필요 없는 공개
 * 엔드포인트**이고, 거기서 나는 실패는 대개 잠깐이다(네트워크 끊김, 거래소 순간 오류,
 * 개발 중 백엔드 재기동).
 *
 * 그렇게 한 번 끊기면 **화면은 마지막에 성공한 값을 계속 띄운 채 조용히 멈춰 있었다.**
 * 3초마다 갱신되는 줄 알고 보는 가격이 사실은 십 분 전 값일 수 있다는 뜻이고, 그것이
 * 이 화면이 스스로 금지한 것이다 — *"멈춘 수를 띄워 두면 사람이 그것을 현재로 읽는다."*
 *
 * 30초는 로그를 실패로 덮지 않으면서 저절로 되살아나기에 충분한 간격이다. 사람이 누르는
 * "새로고침" 은 그대로 남는다 — 30초를 기다리지 않는 길이다.
 */
const RETRY_POLL_MS = 30_000;

/**
 * 폴링 주기. **실패해도 멈추지 않고 느려질 뿐이다.**
 *
 * 이 함수를 따로 뽑은 이유는 같은 판단이 네 곳에 있기 때문이다 — 한 곳만 고치면 나머지 셋은
 * 조용히 옛 규칙으로 남는다.
 */
function 주기(정상: number) {
  /*
    오류 타입을 `unknown` 으로 적으면 그것이 질의 전체의 오류 타입으로 번져 `Failed` 가 받는
    `UseQueryResult<unknown, Error>` 와 어긋난다 — `tsc` 가 그것을 잡았다.
  */
  return (query: { state: { error: Error | null } }) =>
    query.state.error ? RETRY_POLL_MS : 정상;
}

/**
 * 감시. **지금 무슨 일이 벌어지고 있고 무엇이 예정돼 있나.**
 *
 * **이 화면은 손실을 막지 않는다.** 막는 것은 계획 화면의 사이징이다 — 명목이 제대로면 최악이
 * 정해져 있고, 신호를 어떻게 읽든 그 숫자는 변하지 않는다. 이 화면이 하는 일은 급등의 이유를
 * 5분 안에 찾는 것이지 급등을 미리 아는 것이 아니다. 근거: `docs/spec/market-watch.md` § 0
 *
 * **두 단으로 놓는다.** 왼쪽은 이 시장의 가격 자체와 그 밖의 자산(호가·거시), 오른쪽은 이
 * 시장 안에서 평소와 다른 것과 예정된 것(지표·일정·공지)이다. 세로로 쌓으면 한 화면에 안 들어가고, 스크롤로 갈라진 두 사실은
 * **같은 순간의 사실이 아니게 된다.**
 *
 * **네 블록이 서로 독립적으로 실패한다.** 특히 공지는 문서화되지 않은 엔드포인트를 쓰므로
 * 예고 없이 죽을 수 있고, 그때도 호가와 캘린더는 그대로 보여야 한다.
 *
 * **알림도 소리도 자동 스크롤도 없다.** 경고는 색과 문장까지다 — 그 이상은 `scope.md` 가
 * 금지한 실시간 알림으로 미끄러지는 첫 계단이다.
 */
export function WatchScreen() {
  // 탭이 숨으면 멈춘다(`refetchIntervalInBackground` 를 켜지 않은 것이 그 뜻이다).
  // 실패하면 멈추지 않고 느려진다 — 위 `주기` 를 본다.
  const book = useQuery({
    queryKey: ["markets", SYMBOL, "orderbook"],
    queryFn: () => get("/api/markets/{symbol}/orderbook", { path: { symbol: SYMBOL } }),
    retry: false,
    refetchInterval: 주기(BOOK_POLL_MS),
  });
  const outliers = useQuery({
    queryKey: ["markets", SYMBOL, "outliers"],
    queryFn: () => get("/api/markets/{symbol}/outliers", { path: { symbol: SYMBOL } }),
    retry: false,
    refetchInterval: 주기(OUTLIER_POLL_MS),
  });
  // 거시 목록에 비트코인 현물이 있다. 나머지 열둘은 24시간 변동률이라 이 속도가 필요
  // 없지만, 한 응답으로 오므로 가장 빠른 것에 맞춘다.
  const macro = useQuery({
    queryKey: ["markets", "macro"],
    queryFn: () => get("/api/markets/macro"),
    retry: false,
    refetchInterval: 주기(OUTLIER_POLL_MS),
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
    refetchInterval: 주기(NOTICE_POLL_MS),
  });

  return (
    /*
      **두 단으로 나눈다.** 다섯 블록을 세로로 쌓으면 화면이 한 화면에 안 들어가고, 그러면
      "호가가 얇아진 것" 과 "지표가 평소와 다른 것" 을 나란히 보지 못한다 — 스크롤로 갈라진
      두 사실은 같은 순간의 사실이 아니게 된다.

      **왼쪽은 가격, 오른쪽은 가격이 아닌 것**이다. 호가와 거시 자산은 둘 다 "얼마이고 얼마나
      움직였나" 라서 눈이 이어서 읽고, 오른쪽은 "평소와 견줘 어떤가" 와 "무엇이 예정돼 있나"
      라 읽는 방식이 다르다.

      좁은 화면에서는 한 단으로 되돌아간다(`lg:` 부터 갈라진다). `items-start` 가 없으면 두
      단의 높이가 서로를 늘여 짧은 쪽 아래에 빈 칸이 생긴다.
    */
    <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
      <div className="space-y-4">
        {book.data ? (
          <>
            <TickerHeader
              book={book.data}
              refreshing={book.isFetching}
              failed={Boolean(book.error)}
              onRefresh={() => book.refetch()}
            />
            <OrderBookPanel book={book.data} />
          </>
        ) : (
          <Failed label="현재가" query={book} fallback="호가를 가져오지 못했다" />
        )}

        {/*
          **거시 자산은 호가 밑이다.** 오른쪽 지표들과 다른 질문에 답하기 때문이다 — 오른쪽은
          "이 시장 안에서 평소와 다른가" 이고 이쪽은 "이 시장 밖에서 무슨 일이 있나" 다.
          가격 옆에 두면 둘 다 값과 변동률이라 눈이 이어서 읽는다.
        */}
        {macro.data ? (
          <MacroPanel macro={macro.data} />
        ) : (
          <Failed label="거시 자산" query={macro} fallback="거시 시세를 가져오지 못했다" />
        )}
      </div>

      <div className="space-y-4">
        {outliers.data ? (
          <OutlierPanel outliers={outliers.data} />
        ) : (
          <Failed label="이상치" query={outliers} fallback="지표 이력을 가져오지 못했다" />
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
