import { instant, money, percent, ratio, won } from "../../format";
import { EquityChart } from "../../shared/EquityChart";
import { Term } from "../../shared/Term";
import type { components } from "../../api/schema";

type Result = components["schemas"]["CompoundTargetResponse"];

type WonAmounts = components["schemas"]["WonAmountsResponse"];

/**
 * 목표 복리 결과.
 *
 * **답이 먼저다.** 사람이 이 화면에 와서 묻는 것은 "800으로 1년 뒤에 얼마가 되나" 하나이고,
 * 그 답이 아홉 개의 수 사이에 끼어 있으면 답이 아니다. 위쪽 한 칸이 그것만 말하고, 나머지는
 * 전부 아래로 내린다.
 *
 * **그래도 값은 같은 칸에 적고, 뺄셈이 눈으로 닫혀야 한다.** 첫 판은 "벌어들이는 돈
 * +6,332" 옆에 "수수료로 9,681 을 냅니다" 라고 적었는데, 그렇게 놓으면 누구든
 * 6,332 − 9,681 을 해서 **손실로 읽는다.** 실제로는 16,014 를 벌어 9,681 을 내고 6,332 가
 * 남는 것이다 — 번 돈 · 낸 돈 · 남는 돈 세 줄을 부호와 함께 쌓아 그 순서를 보인다.
 *
 * **원화는 곁들임이다.** 계산은 전부 USDT 에서 끝나고 원화는 감을 잡기 위한 표시일 뿐이다.
 * 환율을 얻지 못하면 조용히 빠진다 — 옛 환율로 환산한 금액이 지금 값처럼 뜨는 것보다 낫다.
 */
export function CompoundResult({ result }: { result: Result }) {
  return (
    <div className="space-y-6">
      <section
        className="rounded-lg border border-line bg-surface p-4"
        aria-label="목표 복리 결과"
      >
        <h3 className="text-xs text-ink-2">{result.months}개월 뒤</h3>

        <div className="mt-2">
          <Headline
            usdt={money(result.finalEquity)}
            krw={result.won && won(result.won.finalEquity)}
          />
        </div>

        {/*
          **뺄셈이 화면에서 닫혀야 한다.** 순이익 옆에 비용만 놓았더니 "6,332 를 버는데
          9,681 을 낸다" 로 읽혔다 — 번 돈이 없으면 그것은 손실로 보인다. 세 줄을 부호와
          함께 세로로 쌓아 더하고 빼는 순서가 눈에 보이게 한다.
        */}
        <dl className="mt-4 max-w-md space-y-1 text-sm tabular-nums">
          {/*
            **가운데 두 줄에는 원화를 붙이지 않는다.** 세 금액을 각각 원 단위로 반올림하면
            뺄셈이 1원 어긋난다(21,939,632 − 13,263,587 = 8,676,045 인데 6,332.88 을 직접
            옮기면 8,676,046 이다). 어느 쪽도 틀린 값이 아니지만, 닫히라고 만든 사슬이
            안 닫히는 것으로 보인다. 원화는 뺄셈에 들어가지 않는 자리에만 둔다 —
            낸 돈의 원화는 아래 "기간 전체 비용" 에 그대로 있다.
          */}
          <Line label="번 돈" usdt={`+${money(result.grossProfit)}`} />
          <Line
            label="낸 돈 (수수료·슬리피지)"
            usdt={`−${money(result.totalCost)}`}
            tone="down"
          />
          <div className="border-t border-line pt-1">
            <Line
              label="남는 돈"
              usdt={`+${money(result.totalProfit)}`}
              krw={result.won && `+${won(result.won.totalProfit)}`}
              strong
            />
          </div>
        </dl>

        <p className="mt-3 text-sm leading-relaxed text-ink-2">
          거래 {result.totalTrades}건 동안, 거래마다 가격이{" "}
          {percent(result.priceMovePerTrade)} 움직여야 이렇게 됩니다.
        </p>
      </section>

      <div className="space-y-4">
        <h3 className="border-t border-line pt-4 text-xs text-ink-3">아래로 자세히</h3>

        <EquityChart equity={result.equity} label="월말마다의 자산" point="개월" />

        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm tabular-nums">
          <Term label="기간 전체 수익률" hint="월 목표 × 개월 이 아니라 복리다" />
          <dd className="text-right">{percent(result.totalReturn)}</dd>

          <Term label="기간 전체 비용" hint="수수료와 슬리피지로 나가는 총액" />
          <Amount usdt={money(result.totalCost)} krw={result.won && won(result.won.totalCost)} />

          <Term
            label="시작 시점 명목"
            hint="자산 × 투입 비율 × 레버리지. 수수료는 이쪽에 붙는다"
          />
          <Amount usdt={money(result.notional)} krw={result.won && won(result.won.notional)} />

          <Term
            label="실효 배율"
            hint="명목이 자산의 몇 배인가. 비용과 필요 가격 변동은 이 하나로 정해진다"
          />
          <dd className="text-right">{ratio(result.effectiveLeverage)}배</dd>

          <Term
            label="필요 수익 중 비용의 몫"
            hint="레버리지를 올리면 필요한 가격 변동은 작아지지만 이 몫은 커진다"
          />
          <dd className="text-right">{percent(result.costShare)}</dd>

          <Term label="거래당 필요 순수익" hint="자산 기준. 비용을 낸 뒤에 남아야 하는 몫" />
          <dd className="text-right">{percent(result.netPerTrade)}</dd>

          <Term label="거래당 비용" hint="자산 기준. 실효 배율 × (수수료 + 슬리피지) × 왕복 두 번" />
          <dd className="text-right">{percent(result.costPerTrade)}</dd>

          <Term label="거래당 필요 총수익" hint="순수익 + 비용. 자산 기준" />
          <dd className="text-right">{percent(result.grossPerTrade)}</dd>
        </dl>

        <Rate rate={result.won} />
      </div>
    </div>
  );
}

function Headline({ usdt, krw }: { usdt: string; krw?: string | null }) {
  return (
    <div>
      <p className="text-3xl tabular-nums text-ink">{usdt}</p>
      {krw && <p className="mt-0.5 text-sm tabular-nums text-ink-2">{krw}</p>}
    </div>
  );
}

/**
 * 사슬 한 줄. 왼쪽에 이름, 오른쪽에 USDT 와 원화.
 *
 * 부호를 값에 붙여 받는다 — 빼는 줄인지 더하는 줄인지가 수 자체에 있어야, 셋을 세로로
 * 쌓았을 때 계산이 눈으로 닫힌다.
 */
function Line({
  label,
  usdt,
  krw,
  tone = "plain",
  strong = false,
}: {
  label: string;
  usdt: string;
  krw?: string | null;
  tone?: "plain" | "down";
  strong?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className={strong ? "text-ink" : "text-ink-2"}>{label}</dt>
      <dd className={`text-right ${tone === "down" ? "text-down" : strong ? "text-ink" : "text-ink-2"}`}>
        <span className={strong ? "text-base font-medium" : ""}>{usdt}</span>
        {krw && <span className="block text-xs text-ink-3">{krw}</span>}
      </dd>
    </div>
  );
}

function Amount({ usdt, krw }: { usdt: string; krw?: string | null }) {
  return (
    <dd className="text-right">
      {usdt}
      {krw && <span className="block text-xs text-ink-3">{krw}</span>}
    </dd>
  );
}

/**
 * 어느 환율로 옮긴 값인지 말한다. 이것이 없으면 사람은 언제나 지금 환율로 읽는다.
 *
 * **얻지 못한 것도 말한다.** 원화가 그냥 사라지면 그것이 기능이 없는 것인지 오늘 못 얻은
 * 것인지 알 수 없다.
 */
function Rate({ rate }: { rate: WonAmounts | null }) {
  if (!rate) {
    return (
      <p className="text-xs text-ink-3">
        환율을 얻지 못해 원화는 비워 두었다. 옛 환율로 환산한 금액을 지금 값처럼 띄우지 않는다.
      </p>
    );
  }
  return (
    <p className="text-xs text-ink-3">
      원화는 업비트 KRW-USDT {money(rate.wonPerUsdt)}원 기준이다 ({instant(rate.observedAt)}).
    </p>
  );
}
