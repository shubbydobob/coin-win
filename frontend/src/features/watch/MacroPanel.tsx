import { percent, price } from "../../format";
import type { components } from "../../api/schema";

type Macro = components["schemas"]["MacroQuoteListResponse"];
type Quote = components["schemas"]["MacroQuoteResponse"];

/**
 * 비트코인 밖의 자산 — 주가 · 금속 · 에너지 · 국채 · 공포.
 *
 * **전부 바이낸스에 상장된 TradFi 무기한이다.** 외부 데이터 제공자를 붙이지 않은 이유가
 * 그것이다 — 같은 거래소·같은 시계라야 나란히 놓는 것이 성립한다. 출처가 다르면 "10분 전
 * 나스닥과 지금 BTC" 를 비교하게 된다.
 *
 * **묶어서 놓는다.** 다섯이 열둘이 되면서 한 줄이 목록이 됐고, 목록은 읽히지 않는다.
 * 묶음 이름은 서버가 준다 — 화면이 심볼을 보고 분류하면 같은 규칙이 두 곳에 생긴다.
 *
 * **레버리지 배수는 이름에 들어 있다.** "미 장기국채 3배" 의 +0.4% 는 국채가 0.13% 움직였다는
 * 뜻이다. 배수를 숨기면 그 줄이 국채 자체의 움직임으로 읽힌다.
 *
 * **상관관계를 말하지 않는다.** "나스닥이 오르니 BTC 도 오른다" 는 예측이고 이 프로젝트가
 * 답하지 않기로 한 질문이다(`scope.md`). 나란히 놓는 데까지만 한다 — 읽는 것은 사람이다.
 *
 * **못 읽은 종목 수는 서버가 물어본 수에서 뺀다.** 화면에 상수를 두었더니 관심 목록이 늘어난
 * 날 그 자리가 조용히 거짓말이 됐다.
 *
 * **비트코인 현물이 맨 위에 있다.** 이것만 "비트코인 밖" 이 아니고, 같은 자산의 두 가격이
 * 갈라지는 것 자체가 관측값이다 — 화면 왼쪽 위의 큰 수는 무기한 선물가다. 어느 쪽이
 * 유리한가는 여기서도 말하지 않는다.
 */
export function MacroPanel({ macro }: { macro: Macro }) {
  const 빠진것 = macro.requested - macro.quotes.length;
  const 묶음 = 묶어서(macro.quotes);

  return (
    <section aria-label="거시 자산" className="rounded-lg border border-line bg-surface p-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-medium text-ink">다른 자산들</h2>
        {빠진것 > 0 && <span className="text-xs text-ink-4">{빠진것}종목을 못 읽었다</span>}
      </div>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        24시간 변동률. <b>나란히 놓을 뿐 관계를 계산하지 않는다</b> — &quot;나스닥이 오르니 BTC 도&quot;
        는 예측이고 이 도구가 답하지 않는 질문이다. 금리 자체는 여기 없다 — 거래소에 상장돼
        있지 않고, 국채 둘이 유일한 대리물이다.
      </p>

      {macro.quotes.length === 0 ? (
        <p className="mt-2 text-sm text-ink-3">거시 시세를 가져오지 못했다</p>
      ) : (
        <div className="mt-3 space-y-2">
          {묶음.map(([label, quotes]) => (
            <div key={label}>
              <h3 className="text-[11px] text-ink-4">{label}</h3>
              <dl className="mt-1 grid grid-cols-3 gap-2">
                {quotes.map((quote) => (
                  <Tile key={quote.symbol} quote={quote} />
                ))}
              </dl>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * 자산 하나.
 *
 * **무엇이 답인가가 자산마다 다르다.** 대부분은 변동률이다 — "미 장기국채 3배" 가 31.13
 * 이라는 사실은 그 자체로 아무 말도 하지 않고, 어제보다 2.4% 올랐다는 것이 읽을 값이다.
 * 그래서 값은 작게 아래에 둔다.
 *
 * **비트코인 현물만 반대다.** 이 타일이 여기 있는 이유가 **화면 왼쪽 위의 무기한가와
 * 맞대는 것**이라서, 79,938 이라는 수 자체가 답이다. 그런데 그것이 가장 작고 가장 흐린
 * 자리에 있었다 — 맞대라고 넣은 값이 맞댈 수 없는 크기로 그려져 있었다.
 *
 * **값 자체는 전부 조금 키웠다.** 10px·가장 옅은 색은 나머지 열둘에서도 읽히지 않았다.
 * 뜻이 작다는 것과 안 보인다는 것은 다르다.
 */
function Tile({ quote }: { quote: Quote }) {
  const 하락 = quote.change24hPercent < 0;
  const 색 = 하락 ? "text-down" : "text-up";
  const 값이답 = quote.group === "CRYPTO";

  return (
    <div className="rounded bg-surface-2 p-2 text-center">
      <dt className="text-[11px] leading-tight text-ink-3">
        {quote.label}
        {/*
          **시계가 다른 것을 표시한다.** 야후에서 오는 지수·선물은 미국 장 시간에만 움직인다
          (선물은 거의 24시간이되 주말은 쉼). 같은 "24시간 변동률" 이라도 코인과 뜻이 달라서,
          그 사실을 지우면 주말에 0% 인 것이 "안 움직였다" 로 읽힌다.
        */}
        {!quote.roundTheClock && (
          <span className="ml-1 text-ink-4" title="미국 장 시간에만 움직인다">장중</span>
        )}
      </dt>
      {값이답 ? (
        <>
          <dd className="mt-1 text-base font-semibold tabular-nums text-ink">
            {price(quote.last)}
          </dd>
          <dd className={`mt-0.5 text-xs tabular-nums ${색}`}>
            {하락 ? "▼" : "▲"} {percent(quote.change24hPercent)}
          </dd>
        </>
      ) : (
        <>
          <dd className={`mt-1 text-sm font-medium tabular-nums ${색}`}>
            {하락 ? "▼" : "▲"} {percent(quote.change24hPercent)}
          </dd>
          <dd className="mt-0.5 text-[11px] tabular-nums text-ink-3">{price(quote.last)}</dd>
        </>
      )}
    </div>
  );
}

/**
 * 묶음 이름으로 모은다. **순서는 서버가 준 차례를 그대로 따른다** — 여기서 다시 정렬하면
 * "주가 다음 금속" 이라는 순서가 두 곳에 생기고, 한쪽만 바뀌는 순간 갈라진다.
 */
function 묶어서(quotes: Quote[]): [string, Quote[]][] {
  const 모음 = new Map<string, Quote[]>();
  quotes.forEach((quote) => {
    const 담을곳 = 모음.get(quote.groupLabel);
    if (담을곳) {
      담을곳.push(quote);
      return;
    }
    모음.set(quote.groupLabel, [quote]);
  });
  return [...모음.entries()];
}
