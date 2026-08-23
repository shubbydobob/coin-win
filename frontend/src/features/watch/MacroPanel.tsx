import { percent, price } from "../../format";
import type { components } from "../../api/schema";

type Macro = components["schemas"]["MacroQuoteListResponse"];

/**
 * 비트코인 밖의 자산 다섯 — 나스닥 · 금 · 원유 · 국채 · 변동성.
 *
 * **전부 바이낸스에 상장된 TradFi 무기한이다.** 외부 데이터 제공자를 붙이지 않은 이유가
 * 그것이다 — 같은 거래소·같은 시계라야 나란히 놓는 것이 성립한다. 출처가 다르면 "10분 전
 * 나스닥과 지금 BTC" 를 비교하게 된다.
 *
 * **상관관계를 말하지 않는다.** "나스닥이 오르니 BTC 도 오른다" 는 예측이고 이 프로젝트가
 * 답하지 않기로 한 질문이다(`scope.md`). 나란히 놓는 데까지만 한다 — 읽는 것은 사람이다.
 *
 * **못 읽은 종목은 목록에서 빠진다.** 다섯이 셋으로 오는 것이 정상적인 실패 모양이고,
 * 그것을 화면에 적어 둔다 — 적지 않으면 사람이 원래 셋인 줄 안다.
 */
export function MacroPanel({ macro }: { macro: Macro }) {
  const 빠진것 = 5 - macro.quotes.length;

  return (
    <section aria-label="거시 자산" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">다른 자산들</h2>
        {빠진것 > 0 && <span className="text-xs text-ink-4">{빠진것}종목을 못 읽었다</span>}
      </div>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        24시간 변동률. <b>나란히 놓을 뿐 관계를 계산하지 않는다</b> — "나스닥이 오르니 BTC 도"
        는 예측이고 이 도구가 답하지 않는 질문이다.
      </p>

      {macro.quotes.length === 0 ? (
        <p className="mt-2 text-sm text-ink-3">거시 시세를 가져오지 못했다</p>
      ) : (
        <dl className="mt-3 grid grid-cols-5 gap-2">
          {macro.quotes.map((quote) => {
            const 하락 = quote.change24hPercent < 0;
            return (
              <div key={quote.symbol} className="rounded bg-surface-2 p-2 text-center">
                <dt className="text-[11px] text-ink-3">{quote.label}</dt>
                <dd
                  className={`mt-1 text-sm font-medium tabular-nums ${하락 ? "text-down" : "text-up"}`}
                >
                  {하락 ? "▼" : "▲"} {percent(quote.change24hPercent)}
                </dd>
                <dd className="mt-0.5 text-[10px] tabular-nums text-ink-4">
                  {price(quote.last)}
                </dd>
              </div>
            );
          })}
        </dl>
      )}
    </section>
  );
}
