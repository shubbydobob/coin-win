import { percent, price, quantity } from "../../format";
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
    <section aria-label="현재가" className="rounded border border-slate-200 p-3">
      <div className="flex items-baseline justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <span className="text-2xl tabular-nums">{price(book.last)}</span>
          <span className={`text-sm tabular-nums ${하락 ? "text-red-700" : "text-emerald-700"}`}>
            {percent(book.change24hPercent)}
          </span>
          <span className="text-xs text-slate-400">24시간</span>
        </div>
        <div className="flex items-baseline gap-2 text-xs text-slate-500">
          <span>
            {book.symbol}
            {refreshing && <span className="ml-1 text-slate-400">· 갱신 중</span>}
          </span>
          <SmallButton onClick={onRefresh}>새로고침</SmallButton>
        </div>
      </div>

      <dl className="mt-2 grid grid-cols-3 gap-x-4 text-sm tabular-nums">
        <dt className="text-slate-500">24시간 최고</dt>
        <dt className="text-slate-500">24시간 최저</dt>
        <dt className="text-slate-500">24시간 거래량</dt>
        <dd>{price(book.high24h)}</dd>
        <dd>{price(book.low24h)}</dd>
        <dd>{quantity(book.volume24h)} BTC</dd>
      </dl>
    </section>
  );
}
