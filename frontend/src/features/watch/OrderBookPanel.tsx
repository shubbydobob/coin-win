import { money, percent, price, quantity, ratio } from "../../format";
import { SplitMeter } from "../../shared/Meter";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Book = components["schemas"]["OrderBookResponse"];

type Level = components["schemas"]["PriceLevelResponse"];

type Wall = components["schemas"]["OrderWallResponse"];

/**
 * 호가. 매도를 위에, 매수를 아래에 놓는다 — 거래소 화면과 같은 배치라야 눈이 옮겨 가지 않는다.
 *
 * **방향을 말하지 않는다.** 불균형이 양수라는 것은 매수 잔량이 더 많다는 사실이고, 그것이
 * 오른다는 뜻은 아니다 — 호가는 취소될 수 있고 큰 벽은 오히려 미끼인 경우가 많다.
 *
 * **단 목록은 접혀 있다.** 요약(스프레드·잔량·불균형)은 밖에 남으므로 접어도 잃는 사실이
 * 없고, 40줄이 이 탭에서 가장 긴 블록이었다.
 *
 * 막대 길이는 `format/` 을 거치지 않는다. **표시되는 수가 아니라 그리기 좌표**이기 때문이다 —
 * 사람이 읽는 값은 전부 옆의 숫자이고, 그것은 서버가 낸 값을 `format/` 이 옮긴 것이다.
 */
export function OrderBookPanel({ book }: { book: Book }) {
  const 최대잔량 = Math.max(
    ...book.bids.map((level) => level.quantity),
    ...book.asks.map((level) => level.quantity),
  );

  return (
    <section aria-label="호가" className="rounded-lg border border-line bg-surface p-3">
      <h2 className="text-sm font-medium text-ink">호가</h2>
      <p className="mt-0.5 text-xs leading-snug text-ink-3">
        지금 이 가격에 얼마나 걸려 있나. 유동성이 <b>얇은 쪽</b>으로 가격이 빨리 움직인다 —
        그것은 예측이 아니라 체결의 성질이다. 다만 호가는 취소될 수 있고 큰 벽은 미끼인 경우가
        많아, 이 수치로 방향을 읽으면 안 된다.
      </p>

      <div className="mt-3 flex justify-between rounded bg-surface-2 px-1 py-1.5 text-sm tabular-nums">
        <span className="text-ink-2">스프레드</span>
        <span>
          {money(book.spread)}{" "}
          <span className="text-ink-3">({percent(book.spreadPercent)})</span>
        </span>
      </div>

      {/*
        **단 목록을 접어 둔다.** 40줄이 이 탭에서 가장 긴 블록이고, 그것 때문에 아래의 지표와
        일정이 스크롤 밖으로 밀려났다. 접어도 잃는 것이 없는 이유는 **요약이 밖에 남기**
        때문이다 — 스프레드·잔량·불균형은 언제나 보이고, 펼치는 것은 "어느 가격에 벽이 있나"
        를 실제로 볼 때뿐이다.
      */}
      <Walls bid={book.bidWall} ask={book.askWall} />

      <details className="group mt-1">
        <summary className="cursor-pointer list-none text-xs text-ink-3 hover:text-ink-2">
          <span className="group-open:hidden">단 {book.asks.length + book.bids.length}개 펼치기</span>
          <span className="hidden group-open:inline">접기</span>
        </summary>
        <div className="mt-2 space-y-0.5">
          {[...book.asks].reverse().map((level) => (
            <Row key={`ask-${level.price}`} level={level} max={최대잔량} tone="ask" />
          ))}
          <div className="my-1 border-t border-line-soft" />
          {book.bids.map((level) => (
            <Row key={`bid-${level.price}`} level={level} max={최대잔량} tone="bid" />
          ))}
        </div>
      </details>

      <div className="mt-3 space-y-1">
        <div className="flex justify-between text-xs">
          <span className="text-up">매수 {quantity(book.bidVolume)}</span>
          <span className="text-ink-3">{두께(book.imbalance)}</span>
          <span className="text-down">{quantity(book.askVolume)} 매도</span>
        </div>
        {/* 0.0910 을 읽고 "매수가 9% 두껍다" 로 옮기는 일을 사람이 하지 않게 한다. */}
        <SplitMeter
          left={book.bidVolume}
          right={book.askVolume}
          label={`매수 잔량 ${quantity(book.bidVolume)} 대 매도 잔량 ${quantity(book.askVolume)}, 불균형 ${ratio(book.imbalance)}`}
        />
        <dl className="grid grid-cols-[1fr_auto] items-start gap-x-4 pt-1 text-sm tabular-nums">
          <Term
            label="불균형"
            hint="(매수 − 매도) ÷ 합. 보이는 단수까지만 센다. 방향을 뜻하지 않는다."
          />
          <dd className="text-right">{ratio(book.imbalance)}</dd>
        </dl>
      </div>
    </section>
  );
}

/**
 * 호가 매물대 — 평균보다 유난히 두꺼운 한 단.
 *
 * **접힌 목록 밖에 둔다.** 단 40줄을 펼쳐야만 벽이 보이면 그 사실은 사실상 없는 것과 같고,
 * 이 블록을 접은 이유가 "어느 가격에 벽이 있나는 실제로 볼 때만 펼친다" 였기 때문이다 —
 * 그 질문의 답 하나는 밖에 남겨 둔다.
 *
 * **없으면 없다고 적는다.** 빈 자리로 두면 기능이 없는 것인지 지금 벽이 없는 것인지 알 수
 * 없다. 그렇다고 언제나 최댓값을 내지는 않는다 — 늘 떠 있는 표시는 아무것도 경고하지 않는다.
 *
 * **판독 화면의 매물대와 다른 것이다.** 저쪽은 **이미 체결된** 물량이고 이쪽은 **아직 체결되지
 * 않은** 주문이다. 체결된 것은 사라지지 않지만 호가는 한순간에 취소되므로 훨씬 약한 증거다.
 */
function Walls({ bid, ask }: { bid: Wall | null; ask: Wall | null }) {
  return (
    <div className="mt-1 rounded bg-surface-2 px-1 py-1.5">
      <div className="flex items-baseline justify-between">
        <Term
          label="두꺼운 단"
          hint="같은 쪽 한 단 평균의 3배를 넘는 단. 취소될 수 있어 방향의 근거가 아니다."
        />
        {!bid && !ask && <span className="text-xs text-ink-4">지금 없다</span>}
      </div>
      {(bid || ask) && (
        <div className="mt-1 space-y-0.5">
          <WallLine wall={ask} label="매도" tone="text-down" />
          <WallLine wall={bid} label="매수" tone="text-up" />
        </div>
      )}
    </div>
  );
}

function WallLine({ wall, label, tone }: { wall: Wall | null; label: string; tone: string }) {
  if (!wall) {
    return (
      <div className="flex justify-between text-xs text-ink-4">
        <span>{label}</span>
        <span>고르다</span>
      </div>
    );
  }
  return (
    <div className="flex justify-between text-sm tabular-nums">
      <span className={tone}>
        {label} {price(wall.price)}
      </span>
      <span className="text-ink-2">
        {quantity(wall.quantity)}{" "}
        <span className="text-ink-3">평균의 {ratio(wall.multipleOfAverage)}배</span>
      </span>
    </div>
  );
}

function 두께(imbalance: number): string {
  if (imbalance > 0) {
    return "매수 두꺼움";
  }
  return imbalance < 0 ? "매도 두꺼움" : "균형";
}

/** 한 단. 잔량을 막대로도 보인다 — 어느 쪽이 두꺼운지는 숫자보다 길이가 빨리 읽힌다. */
function Row({ level, max, tone }: { level: Level; max: number; tone: "bid" | "ask" }) {
  const 길이 = max === 0 ? 0 : (level.quantity / max) * 100;

  return (
    <div className="relative flex justify-between px-1 text-sm tabular-nums">
      <div
        className={`absolute inset-y-0 right-0 rounded-sm ${tone === "bid" ? "bg-up/20" : "bg-down/20"}`}
        style={{ width: `${길이}%` }}
        aria-hidden="true"
      />
      <span className={`relative ${tone === "bid" ? "text-up" : "text-down"}`}>
        {price(level.price)}
      </span>
      <span className="relative text-ink-2">{quantity(level.quantity)}</span>
    </div>
  );
}
