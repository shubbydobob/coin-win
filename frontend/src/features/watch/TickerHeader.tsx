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
  onRefresh,
}: {
  book: Book;
  refreshing: boolean;
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
        </div>
        <div className="flex items-baseline gap-2 text-xs text-ink-2">
          <span>
            {book.symbol}
            {refreshing && <span className="ml-1 text-ink-3">· 갱신 중</span>}
          </span>
          <SmallButton onClick={onRefresh}>새로고침</SmallButton>
        </div>
      </div>

      {/* 최고·최저를 숫자로만 두면 지금이 그 사이 어디인지가 안 보인다. */}
      <div className="mt-3 space-y-1">
        <RangeMeter
          low={book.low24h}
          high={book.high24h}
          current={book.last}
          label={`24시간 ${price(book.low24h)} ~ ${price(book.high24h)} 중 현재 ${price(book.last)}`}
        />
        <div className="flex justify-between text-xs tabular-nums text-ink-3">
          <span>{price(book.low24h)}</span>
          <span>24시간 거래량 {quantity(book.volume24h)} BTC</span>
          <span>{price(book.high24h)}</span>
        </div>
      </div>
    </section>
  );
}
