import { percent, price, quantity } from "../../format";
import { RangeMeter } from "../../shared/Meter";
import { SmallButton } from "../../shared/SmallButton";
import type { components } from "../../api/schema";

type Book = components["schemas"]["OrderBookResponse"];

/**
 * 지금 얼마인가. 이 화면에서 가장 자주 바뀌는 값이라 맨 위에 크게 둔다.
 *
 * 변동률의 부호를 색으로 말한다. **숫자를 만들지는 않는다** — 부호는 서버가 낸 값에서 읽고
 * 반올림도 `format/` 이 한다(`docs/adr/020`).
 */
export function TickerHeader({
  book,
  refreshing,
  failed = false,
  onRefresh,
}: {
  book: Book;
  refreshing: boolean;
  /**
   * **마지막 갱신이 실패했다.** 값은 여전히 있다 — 마지막으로 성공한 것이다. 그 상태를 말하지
   * 않으면 3초마다 새로 오는 줄 알고 보는 수가 사실은 몇 분 전 값이 되고, 그것이 이 화면이
   * 스스로 금지한 것이다.
   */
  failed?: boolean;
  onRefresh: () => void;
}) {
  const 하락 = book.change24hPercent < 0;

  return (
    <section aria-label="현재가" className="rounded-lg border border-line bg-surface p-4">
      <div className="flex items-baseline justify-between gap-3">
        <div className="flex items-baseline gap-3">
          {/* 이 화면에서 가장 큰 수. 현재가를 찾느라 눈이 헤매지 않아야 한다. */}
          <span className={`text-3xl font-semibold tabular-nums ${하락 ? "text-down" : "text-up"}`}>
            {price(book.last)}
          </span>
          <span className={`text-sm tabular-nums ${하락 ? "text-down" : "text-up"}`}>
            {하락 ? "▼" : "▲"} {percent(book.change24hPercent)}
          </span>
          <span className="text-xs text-ink-3">24시간</span>
          {failed && (
            <span className="text-xs font-medium text-warn">갱신 실패 — 멈춘 값이다</span>
          )}
        </div>
        <div className="flex items-baseline gap-2 text-xs text-ink-2">
          <span>
            {book.symbol}
            {refreshing && <span className="ml-1 text-ink-3">· 갱신 중</span>}
          </span>
          <SmallButton onClick={onRefresh}>새로고침</SmallButton>
        </div>
      </div>

      {/*
        최고·최저를 숫자로만 두면 지금이 그 사이 어디인지가 안 보인다. 꼭대기에 붙어 있는
        것과 바닥에 붙어 있는 것은 같은 숫자 셋으로 표현되지만 전혀 다른 상황이다.

        **거래량을 이 축에서 뺐다.** 좌우가 최저·최고이므로 가운데에 놓인 수는 중간값처럼
        읽힌다 — 거래량은 이 축과 아무 관계가 없는데 축 위에 앉아 있었다.
      */}
      <div className="mt-3 space-y-1">
        <div className="flex items-baseline justify-between text-xs text-ink-3">
          <span>24시간 범위</span>
          <span className="tabular-nums">
            거래량 {quantity(book.volume24h)} BTC
          </span>
        </div>
        <RangeMeter
          low={book.low24h}
          high={book.high24h}
          current={book.last}
          label={`24시간 ${price(book.low24h)} ~ ${price(book.high24h)} 중 현재 ${price(book.last)}`}
        />
        <div className="flex justify-between text-xs tabular-nums text-ink-3">
          <span>{price(book.low24h)}</span>
          <span>{price(book.high24h)}</span>
        </div>
      </div>
    </section>
  );
}
