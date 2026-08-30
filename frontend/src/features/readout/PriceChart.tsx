import { useQuery } from "@tanstack/react-query";
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  LineStyle,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from "lightweight-charts";

import { IchimokuCloud, type 구름점 } from "./IchimokuCloud";
import { useEffect, useRef, useState } from "react";

import { get } from "../../api/client";
import { percent, price } from "../../format";
import { SideChip, type ChipSide } from "../../shared/SideChip";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];
type Series = components["schemas"]["IndicatorSeriesResponse"];
type AnySeries = ISeriesApi<"Candlestick"> | ISeriesApi<"Line"> | ISeriesApi<"Histogram">;

/** 봉 시각을 차트가 쓰는 초 단위로. */
const 초 = (iso: string) => (Date.parse(iso) / 1000) as UTCTimestamp;

/**
 * 처음 띄울 때 화면에 보여 줄 봉 수.
 *
 * 받는 것은 700봉이고 그중 이만큼만 창에 넣는다. 15분이면 하루 반, 4시간이면 스물닷새,
 * 주봉이면 3년이다. 나머지는 지워지는 것이 아니라 왼쪽에 있다 — 끌거나 휠로 간다.
 */
const 보여줄봉 = 150;

/**
 * 캔들 차트와 지표 곡선.
 *
 * **수평선이던 것이 곡선이 됐다.** 앞판은 구름과 밴드를 지금 봉의 값으로 가로선을 그었다 —
 * 판독 응답에 봉 하나의 값밖에 없었기 때문이다. 이제 서버가 봉마다의 값을 주므로 원래 모양대로
 * 그린다. 자리만 맞던 것이 모양까지 맞는다.
 *
 * **칸을 셋으로 나눈다.** RSI 는 0~100 이고 MACD 는 가격의 차라 가격 축에 얹을 수 없다.
 * 억지로 얹으면 캔들이 한 줄로 눌린다.
 *
 * **기본으로 켜는 것은 둘뿐이다.** 여덟 선을 한꺼번에 그리면 캔들이 안 보이고, 그러면 띠를
 * 걷어낸 이유가 그대로 돌아온다. 볼린저는 중심선이 이동평균 20 과 **같은 값**이라 기본에서
 * 뺐다 — 중복이 아니라 사실이고, 켜면 겹쳐 보인다.
 *
 * **이 파일에 지표 계산이 없다.** 전부 서버가 낸 값이다. 넷 다 트레이딩뷰 원문과 대조해
 * 정의를 확정했고(`docs/adr/014`·`015`, RSI·MACD 는 Pine 소스), 그리는 좌표만 이쪽에서
 * 만든다(`docs/adr/020`).
 *
 * **캔버스라 자동 테스트가 닿지 않는다.** 그래서 상태를 캔버스 밖에 글로 남긴다 — 몇 봉인지,
 * 어느 지표가 왜 비었는지. 그림이 맞는지는 사람이 봐야 안다.
 */
export function PriceChart({ symbol, readout }: { symbol: string; readout: Readout }) {
  const 창 = useRef<HTMLDivElement>(null);
  const 차트 = useRef<IChartApi | null>(null);
  const 그린것 = useRef<AnySeries[]>([]);
  const 맞춘주기 = useRef<string | null>(null);
  const 이전봉수 = useRef(0);
  const [켠것, 켜기] = useState<Record<Overlay, boolean>>({
    ichimoku: true,
    bollinger: false,
    movingAverages: true,
  });

  const series = useQuery({
    queryKey: ["series", symbol, readout.interval, readout.at],
    queryFn: () =>
      get("/api/readout/{symbol}/series", {
        path: { symbol },
        query: { interval: readout.interval },
      }),
    // 마지막 봉은 아직 안 닫혔다. 판독과 같은 주기로 다시 묻는다.
    refetchInterval: 15_000,
    staleTime: 10_000,
  });

  // 차트는 한 번만 만든다. 값이 바뀔 때마다 다시 만들면 옮겨 둔 축이 매번 되돌아간다.
  useEffect(() => {
    if (!창.current || 차트.current) {
      return;
    }
    const chart = createChart(창.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#848e9c",
        attributionLogo: false,
        panes: { separatorColor: "#2b3139", separatorHoverColor: "#3b4149" },
      },
      grid: { vertLines: { color: "#21262d" }, horzLines: { color: "#21262d" } },
      rightPriceScale: { borderColor: "#2b3139" },
      timeScale: { borderColor: "#2b3139", timeVisible: true },
      crosshair: { mode: 0 },
      autoSize: true,
    });
    차트.current = chart;
    return () => {
      chart.remove();
      차트.current = null;
      그린것.current = [];
    };
  }, []);

  // 값이나 켠 것이 바뀌면 전부 다시 그린다. 시리즈가 여덟이라 부분 갱신은 얽힌다.
  useEffect(() => {
    const chart = 차트.current;
    const data = series.data;
    if (!chart || !data) {
      return;
    }
    그린것.current.forEach((s) => chart.removeSeries(s));
    그린것.current = [];

    const 선 = (color: string, pane: number, dashed = false, width: 1 | 2 = 1) => {
      const s = chart.addSeries(LineSeries, {
        color,
        lineWidth: width,
        lineStyle: dashed ? LineStyle.Dashed : LineStyle.Solid,
        priceLineVisible: false,
        lastValueVisible: false,
      }, pane);
      그린것.current.push(s);
      return s;
    };

    const candles = chart.addSeries(CandlestickSeries, {
      upColor: "#0ecb81",
      downColor: "#f6465d",
      borderUpColor: "#0ecb81",
      borderDownColor: "#f6465d",
      wickUpColor: "#0ecb81",
      wickDownColor: "#f6465d",
    }, 0);
    그린것.current.push(candles);
    candles.setData(data.candles.map((c) => ({
      time: 초(c.openTime),
      open: c.open,
      high: c.high,
      low: c.low,
      close: c.close,
    })));

    if (켠것.ichimoku) {
      // 구름은 선이 아니라 면이다. 라이브러리에 두 계열 사이를 칠하는 기능이 없어 캔버스에
      // 직접 그린다 — 근거는 IchimokuCloud 의 주석. 계열을 지우면 딸린 조각도 함께 사라진다.
      const 구름 = new IchimokuCloud();
      candles.attachPrimitive(구름);
      구름.setData(구름면(data.ichimoku));
      선(ICHIMOKU_LINE.spanA, 0).setData(점(data.ichimoku, (p) => p.leadingSpanA));
      선(ICHIMOKU_LINE.spanB, 0).setData(점(data.ichimoku, (p) => p.leadingSpanB));
      선(ICHIMOKU_LINE.conversion, 0, true).setData(점(data.ichimoku, (p) => p.conversionLine));
      선(ICHIMOKU_LINE.base, 0, true).setData(점(data.ichimoku, (p) => p.baseLine));
    }
    if (켠것.bollinger) {
      선(BOLLINGER_LINE, 0, true).setData(점(data.bollinger, (p) => p.upper));
      선(BOLLINGER_LINE, 0, true).setData(점(data.bollinger, (p) => p.lower));
    }
    if (켠것.movingAverages) {
      data.movingAverages.forEach((ma) => {
        const 선모양 = MA_LINE[ma.period] ?? { color: "#848e9c", width: 1 as const };
        선(선모양.color, 0, false, 선모양.width).setData(점(ma.points, (p) => p.value));
      });
    }

    // 지지·저항·매물대는 곡선이 아니라 자리다. 가로선이 원래 모양이다.
    수평선(readout).forEach((줄) =>
      candles.createPriceLine({
        price: 줄.price,
        color: 줄.color,
        lineWidth: 1,
        lineStyle: LineStyle.Solid,
        axisLabelVisible: true,
        title: 줄.title,
      }),
    );

    if (data.rsi.length > 0) {
      선("#a35bb5", 1, false, 2).setData(점(data.rsi, (p) => p.value));
      // 30·70 은 관습이지 검증한 수가 아니다. 그래서 눈금이지 신호가 아니다.
      [30, 70].forEach((level) => {
        선("#3a4048", 1, true).setData(data.rsi.map((p) => ({ time: 초(p.at), value: level })));
      });
    }
    if (data.macd.length > 0) {
      const bars = chart.addSeries(
        HistogramSeries, { priceLineVisible: false, lastValueVisible: false }, 2);
      그린것.current.push(bars);
      bars.setData(data.macd.map((p) => ({
        time: 초(p.at),
        value: p.histogram,
        color: p.histogram >= 0 ? "#0ecb8155" : "#f6465d55",
      })));
      선("#0094ff", 2).setData(점(data.macd, (p) => p.macd));
      선("#ff6a00", 2).setData(점(data.macd, (p) => p.signal));
    }

    // 가격 칸을 넓게. 그러지 않으면 셋이 같은 높이로 나뉘어 캔들이 눌린다.
    const panes = chart.panes();
    panes[0]?.setStretchFactor(3);
    panes[1]?.setStretchFactor(1);
    panes[2]?.setStretchFactor(1);
    /*
      **전부 한 폭에 밀어 넣지 않는다.** 700봉을 900px 에 그리면 봉 하나가 1.3px 이라 몸통과
      꼬리가 구별되지 않는다 — 그것이 캔들로 바꾼 이유를 지운다. 지표에는 700봉이 다 필요하지만
      (이동평균 300 은 300봉을 먹고서야 첫 값을 낸다) **계산에 필요한 봉 수와 눈에 보여 줄 봉
      수는 다른 것이다.** 최근 구간만 띄우고 나머지는 끌어서 본다.

      **한 번만 맞춘다.** 15초마다 다시 맞추면 사람이 옮겨 둔 축이 매번 되돌아간다. 다만 축을
      건드리지 않은 채 오른끝을 보고 있었다면 새 봉을 따라 붙는다 — 그러지 않으면 방금 그려진
      봉이 화면 밖으로 밀려난다.
    */
    const 봉수 = data.candles.length;
    const 범위 = chart.timeScale().getVisibleLogicalRange();
    const 오른끝을보고있다 = 범위 === null || 범위.to >= 이전봉수.current - 1;
    if (맞춘주기.current !== readout.interval || 오른끝을보고있다) {
      맞춘주기.current = readout.interval;
      chart.timeScale().setVisibleLogicalRange({
        from: Math.max(0, 봉수 - 보여줄봉),
        to: 봉수 + 4,
      });
    }
    이전봉수.current = 봉수;
  }, [series.data, readout, 켠것]);

  return (
    <div className="mt-2">
      <div className="mb-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
        {OVERLAYS.map(({ key, label, lines }) => (
          <label key={key} className="flex cursor-pointer items-center gap-1 text-ink-3">
            <input
              type="checkbox"
              checked={켠것[key]}
              onChange={(event) => 켜기((전) => ({ ...전, [key]: event.target.checked }))}
              className="size-3"
            />
            {label}
            {lines.map(({ color, name }) => (
              <span key={color + name} className="flex items-center gap-0.5">
                <span
                  className="inline-block h-0.5 w-3 rounded-full"
                  style={{ backgroundColor: color }}
                  aria-hidden="true"
                />
                {name && <span className="text-ink-4">{name}</span>}
              </span>
            ))}
          </label>
        ))}
      </div>

      {/*
        **높이를 화면에 맞춰 키운다.** 칸이 셋(가격 3 : RSI 1 : MACD 1)이라 440px 에서는 가격
        칸이 264px 밖에 안 되고, 그 안에 캔들·구름·밴드·이동평균 다섯이 겹쳐 앉는다. 위아래의
        글이 한 화면에 남아 있어야 하므로 뷰포트의 70% 로 두고, 낮은 화면과 아주 긴 화면
        양쪽을 30rem~58rem 으로 막는다. 폭은 본문 폭(max-w-6xl)을 그대로 따른다.
      */}
      <div ref={창} className="h-[clamp(30rem,70vh,58rem)] w-full" />

      {/*
        **판 이름을 캔버스 밖에 적는다.** 그림 안에 글자를 넣으면 축과 겹치고, 무엇보다
        스크린리더와 테스트가 못 읽는다. 아래 두 칸이 무슨 칸인지 모르면 그 칸은 무늬다.
      */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-ink-4">
        <span>가운데 칸 — <b className="text-ink-3">RSI(14)</b> 0~100, 점선은 30·70</span>
        <span>
          아래 칸 — <b className="text-ink-3">MACD(12/26/9)</b>
          <span className="ml-1 text-[#0094ff]">MACD</span>
          <span className="ml-1 text-[#ff6a00]">시그널</span>
          <span className="ml-1">막대는 둘의 차</span>
        </span>
      </div>

      {/*
        **캔버스 밖에 상태를 적는다.** 그림 안에서 벌어지는 일은 테스트도 스크린리더도 못 본다.
        어느 지표가 왜 비었는지까지 글로 남긴다 — 빈 것이 고장인지 봉이 모자란 것인지가
        화면에서 갈려야 한다.
      */}
      <p className="mt-1 text-[11px] text-ink-4" aria-live="polite">
        {series.isError
          ? "지표를 가져오지 못했다"
          : series.data
            ? `${series.data.count}봉을 계산해 최근 ${보여줄봉}봉을 띄운다 · 끌거나 휠로 과거로 · 마지막 ${price(readout.close)}${빈것(series.data)}`
            : "지표를 가져오는 중"}
      </p>

      {series.data && <Stances stances={series.data.stances} />}

      <Levels readout={readout} />
    </div>
  );
}

/**
 * 지표마다 <b>지금 어느 쪽에 서 있는가.</b>
 *
 * <b>세되 우열은 내지 않는다.</b> 감시 화면의 「붐비는 쪽」과 같은 태도다 — 다섯 중 셋이
 * 롱 쪽이라는 것은 사실이고, 그래서 롱이 유리하다는 것은 사실이 아니다. 이 저장소는 그 부류의
 * 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`).
 *
 * <b>판정은 서버가 한다.</b> 화면이 세 이동평균을 비교해 "정배열" 이라고 적으면 그 규칙이
 * 화면에 생기고, 그러면 같은 규칙이 백테스트나 연구 쪽과 갈라진다.
 *
 * <b>말할 수 없는 것은 세지 않는다.</b> 봉이 모자란 지표는 딱지 없이 이유만 적는다 —
 * 중립으로 세면 "가운데 있다" 는 없는 사실이 생긴다.
 */
function Stances({ stances }: { stances: Series["stances"] }) {
  const 위 = stances.filter((s) => s.stance === "LONG").length;
  const 아래 = stances.filter((s) => s.stance === "SHORT").length;

  return (
    <section aria-label="지표가 선 자리" className="mt-2 rounded bg-surface-2 px-2 py-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="text-ink-3">지표가 선 자리</span>
        <SideChip side="LONG" word={STANCE_WORD.LONG}>{위}</SideChip>
        <SideChip side="SHORT" word={STANCE_WORD.SHORT}>{아래}</SideChip>
        <span className="ml-auto text-[10px] text-ink-4">
          선 자리일 뿐 방향이 아니다 — 7년에서 오히려 반대로 나왔다
        </span>
      </div>
      <dl className="mt-1 divide-y divide-line-soft">
        {stances.map((stance) => (
          <div key={stance.indicator} className="flex items-baseline gap-2 py-1 text-xs">
            {/*
              **마우스 툴팁은 덤이다.** 이 저장소는 뜻을 툴팁에 숨기지 않기로 했다
              (`shared/Term`) — 올리지 않는 사람에게는 없는 것과 같기 때문이다. 그래서 같은
              정의를 아래 「지표가 무엇을 재는가」에 펼칠 수 있게 두고, 툴팁은 이미 아는
              사람이 빠르게 확인하는 용도로만 붙인다.
            */}
            <dt className="w-16 shrink-0 text-ink-3" title={정의[stance.indicator]}>
              {stance.indicator}
            </dt>
            <dd className="flex flex-wrap items-baseline gap-1.5">
              {STANCE_CHIP[stance.stance] && (
                <SideChip
                  side={STANCE_CHIP[stance.stance]!}
                  word={STANCE_WORD[STANCE_CHIP[stance.stance]!]}
                />
              )}
              <span className={stance.stance === "UNKNOWN" ? "text-ink-4" : "text-ink-2"}>
                {stance.statement}
              </span>
            </dd>
          </div>
        ))}
      </dl>

      {/*
        **정의를 화면 안에 둔다.** 접혀 있어도 이 화면 밖으로 나가지 않는 것이 요점이다 —
        다른 문서로 옮기면 찾아보지 않는 사람에게는 없는 것과 같다.
      */}
      <details className="group mt-1.5">
        <summary className="cursor-pointer list-none text-[11px] text-ink-4 hover:text-ink-3">
          지표가 무엇을 재는가
          <span aria-hidden="true" className="group-open:hidden">{" ▸"}</span>
          <span aria-hidden="true" className="hidden group-open:inline">{" ▾"}</span>
        </summary>
        <dl className="mt-1.5 space-y-1.5 text-[11px] leading-snug text-ink-4">
          {Object.entries(정의).map(([이름, 뜻]) => (
            <div key={이름}>
              <dt className="inline font-medium text-ink-3">{이름} — </dt>
              <dd className="inline">{뜻}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-2 text-[11px] leading-snug text-ink-4">
          <b>딱지가 유리한 쪽을 가리키지 않는다 — 재 봤다.</b> 4시간봉 7년(15,110봉)에서 각
          딱지 다음의 수익률을 세었더니 <b>롱 딱지 쪽이 기준선보다 나빴다.</b> 24시간 뒤 승률이
          기준선 51.68% 인데 일목 롱 49.87%(−1.81%p) · RSI 롱 50.30%(−1.38%p) ·
          MACD 롱 50.40%(−1.28%p) 이고, 숏 딱지가 그만큼 높다. <b>다섯 지표에서 방향이 같다.</b>
        </p>
        <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
          그렇다고 <b>반대로 하면 된다는 뜻도 아니다.</b> 차이가 가장 큰 자리도 승률 +1.9%p ·
          중앙값 0.17% 인데 <b>왕복 수수료·슬리피지가 0.14% 안팎</b>이라 남는 것이 거의 없고,
          다중검정 보정도 국면 분리도 하지 않은 수다. 그래서 이 화면은 <b>어느 쪽에 서 있는가
          까지만 말한다.</b> 재는 명령은 <code>gradlew crossCheck --tests "*StanceCrossCheck*"</code>.
        </p>
      </details>
    </section>
  );
}

/**
 * 지표가 무엇을 재는가. <b>정의만 적고 판단하지 않는다.</b>
 *
 * "RSI 가 70 이면 과열이니 조심하라" 같은 문장은 이 화면이 하지 않기로 한 일이다. 적는 것은
 * 서버가 그 수를 어떻게 세는가까지이고, 그 계산은 전부 트레이딩뷰 원문과 대조해 확정했다.
 */
const 정의: Record<string, string> = {
  일목: "9봉·26봉 중간값으로 전환선과 기준선을 만들고, 그 둘의 평균과 52봉 중간값을 25봉 앞으로 밀어 구름을 그린다. 변위 26 이 실제로는 25봉을 미는 것까지 트레이딩뷰 원문으로 확정했다.",
  볼린저: "20봉 단순이동평균에 표준편차의 2배를 더하고 뺀 띠. 표준편차는 모집단 기준이다. 폭이 좁아지면 최근 움직임이 작았다는 뜻이고, 그 다음이 무엇인지는 말하지 않는다.",
  이동평균: "종가의 단순 평균. 10·20·50·200·300 을 그린다. 20 은 볼린저 중심선과 같은 값이다. 짧은 것이 긴 것 위에 놓이면 최근 가격이 예전보다 높다는 뜻이고, 그것이 계속된다는 뜻은 아니다.",
  RSI: "오른 폭의 평균 ÷ 내린 폭의 평균을 0~100 으로 옮긴 것. 14봉이고 평활은 와일더 방식(RMA)이다 — EMA 로 짜면 값이 조금씩 다르면서 그럴듯해 보인다.",
  MACD: "12봉 EMA 에서 26봉 EMA 를 뺀 값과, 그것의 9봉 EMA(시그널). 막대는 둘의 차다. 가격이 아니라 가격의 차라 음수가 될 수 있다.",
};

/**
 * 딱지에 적는 말. **`롱 / 숏` 이 아니라 `위 / 아래` 다.**
 *
 * 다섯 지표가 재는 것은 전부 **무엇의 위인가 아래인가**다 — 구름 위, 밴드 위, 50 위,
 * 시그널 위, 짧은 이동평균이 위. 그것은 **자리에 대한 사실**이고 거기까지가 이 화면이 아는
 * 것이다.
 *
 * **`롱` 이라는 글자는 사람에게 "이쪽으로 가라" 로 읽힌다.** 그런데 4시간봉 7년(15,110봉)에서
 * 재 보니 그 자리들이 가리키는 방향은 **오히려 반대**였다 — 구름 위에서 하루 뒤 오를 확률이
 * 49.9%, 구름 아래에서 53.6% 로 기준선(51.7%)을 사이에 두고 갈렸다. 차이가 작아 매매에 쓸
 * 수는 없지만(왕복 비용 0.14% 를 못 넘는다) **적어도 `롱` 이라고 부를 근거는 없다.**
 *
 * 상태를 지우지는 않았다. 자리를 아는 것은 모르는 것보다 낫고, 무엇보다 그 자리가 **손절을
 * 어디에 둘지**의 재료다. 바꾼 것은 이름뿐이다.
 *
 * 색은 그대로 둔다 — `SideChip` 이 초록·빨강을 쓰는 것은 이 저장소가 상승을 초록으로 쓰기
 * 때문이지 좋다는 뜻이 아니고, 그 설명은 그쪽 주석에 이미 있다.
 */
const STANCE_WORD: Record<ChipSide, string> = {
  LONG: "위",
  SHORT: "아래",
  NEUTRAL: "가운데",
};

/** 중립과 말할 수 없음에는 딱지가 없다 — 둘 다 "어느 쪽" 이 아니기 때문이다. */
const STANCE_CHIP: Record<string, ChipSide | undefined> = {
  LONG: "LONG",
  SHORT: "SHORT",
};

type Overlay = "ichimoku" | "bollinger" | "movingAverages";

/**
 * 선의 색. **색을 가진 무리는 이동평균 하나뿐이다.**
 *
 * 앞판은 세 무리가 다 색을 갖고 있었고 그래서 서로 겹쳤다 — 이동평균 10(`#ffffff`)과
 * 20(`#eaecef`)이 사실상 같은 흰색이었고, 200 은 음봉과 **정확히 같은 빨강**(`#f6465d`)이라
 * 캔들에 묻혔으며, 선행스팬 A 와 B 가 둘 다 `#8a8f98` 이었다. **구름이 뒤집히는 것이 신호인데
 * 그 뒤집힘을 색으로 읽을 수 없었다.**
 *
 * 그래서 무리마다 역할을 준다. 캔들은 초록·빨강(가격), 일목은 무채색(맥락), 볼린저는 청록
 * 점선, 이동평균만 색이다. **색을 가진 무리가 하나뿐이면 색이 곧 "어느 이동평균인가" 를
 * 뜻하게 된다** — 화면을 반만 쓰는 좁은 창에서도 그 규칙은 무너지지 않는다.
 *
 * 구간을 바꾸면 여기도 함께 바꿔야 하고, 없는 구간은 회색으로 떨어진다.
 */
const MA_LINE: Record<number, { color: string; width: 1 | 2 }> = {
  10: { color: "#ffffff", width: 1 },
  20: { color: "#7dd3fc", width: 1 },
  50: { color: "#fbbf24", width: 1 },
  200: { color: "#f0abfc", width: 2 },
  300: { color: "#8b5cf6", width: 2 },
};

/**
 * 일목의 네 선. **무채색이고 밝기로만 가른다.**
 *
 * 선행스팬 A 를 밝게 B 를 어둡게 둔 것이 요점이다 — 둘 중 어느 쪽이 위인가가 구름의 방향이고,
 * 같은 색이면 그 사실이 화면에서 사라진다. 전환·기준은 점선이라 스팬과 섞이지 않는다.
 *
 * **넷 다 흰색에서 충분히 내려와 있어야 한다.** 첫 판은 전환선을 `#c9d1d9` 로 두었는데 그것이
 * 이동평균 10 의 흰색과 캔들 주변에서 겹쳤다 — 무채색으로 통일하는 것만으로는 부족하고,
 * **일목은 맥락이므로 뒤로 물러나 있어야** 색을 가진 이동평균이 앞에 선다.
 */
const ICHIMOKU_LINE = {
  spanA: "#7c8a97",
  spanB: "#454f59",
  conversion: "#93a0ac",
  base: "#5a6570",
} as const;

/** 볼린저. 청록 점선 하나 — 일목의 무채색과도, 이동평균의 색과도 겹치지 않는다. */
const BOLLINGER_LINE = "#3fb9ad";

/**
 * 범례. **선마다 이름을 붙인다.**
 *
 * 앞판은 무리마다 색 조각만 늘어놓고 이름은 "이동평균 10·20·50·200·300" 처럼 한 덩어리로
 * 적었다. 그러면 화면의 어떤 선이 200 인지를 색 조각과 이름 사이에서 **사람이 짝지어야
 * 한다** — 조각이 다섯이고 이름도 다섯이면 순서를 세어 맞춰야 한다는 뜻이다. 색 옆에 이름을
 * 붙여 두면 세지 않아도 된다.
 *
 * 색은 그리는 쪽과 같은 상수에서 온다. 두 벌을 두면 팔레트를 바꿀 때 범례만 옛 색으로 남는다.
 */
const OVERLAYS: { key: Overlay; label: string; lines: { color: string; name: string }[] }[] = [
  {
    key: "ichimoku",
    label: "일목",
    lines: [
      { color: ICHIMOKU_LINE.spanA, name: "선행A" },
      { color: ICHIMOKU_LINE.spanB, name: "선행B" },
      { color: ICHIMOKU_LINE.conversion, name: "전환" },
      { color: ICHIMOKU_LINE.base, name: "기준" },
    ],
  },
  {
    key: "bollinger",
    label: "볼린저 (중심은 MA20 과 같다)",
    lines: [{ color: BOLLINGER_LINE, name: "" }],
  },
  {
    key: "movingAverages",
    label: "이동평균",
    lines: Object.entries(MA_LINE).map(([period, line]) => ({
      color: line.color,
      name: period,
    })),
  },
];

/** 값이 있는 점만. `null` 을 0 으로 바꾸면 차트 바닥에 없는 선이 생긴다. */
function 점<T extends { at: string }>(points: T[], pick: (point: T) => number | null | undefined) {
  return points
    .map((point) => ({ time: 초(point.at), value: pick(point) }))
    .filter((row): row is { time: UTCTimestamp; value: number } =>
      row.value !== null && row.value !== undefined);
}

/**
 * 구름으로 칠할 구간. **두 스팬이 모두 있는 점만** 낸다 — 한쪽만 있는 구간은 위아래가
 * 정해지지 않아 칠할 면이 없다. 워밍업 구간이 그렇다.
 */
function 구름면(points: Series["ichimoku"]): 구름점[] {
  const out: 구름점[] = [];
  points.forEach((point) => {
    if (point.leadingSpanA !== null && point.leadingSpanA !== undefined
      && point.leadingSpanB !== null && point.leadingSpanB !== undefined) {
      out.push({ time: 초(point.at), a: point.leadingSpanA, b: point.leadingSpanB });
    }
  });
  return out;
}

/** 비어 있는 지표를 이름으로 적는다. 없는 것과 고장난 것은 다른 사실이다. */
function 빈것(data: Series): string {
  const 빈 = [
    data.ichimoku.length === 0 ? "일목" : null,
    data.bollinger.length === 0 ? "볼린저" : null,
    data.rsi.length === 0 ? "RSI" : null,
    data.macd.length === 0 ? "MACD" : null,
    ...data.movingAverages.filter((ma) => ma.points.length === 0).map((ma) => `MA${ma.period}`),
  ].filter(Boolean);
  return 빈.length === 0 ? "" : ` · 봉이 모자라 못 그린 것: ${빈.join(" · ")}`;
}

/**
 * 차트 위의 가로선을 **가격 순서 그대로** 아래에 적는다.
 *
 * 차트의 축 라벨은 작고 서로 겹치며 창이 좁으면 잘린다. **지금 가격을 목록 한가운데 끼워
 * 넣으면** 위/아래가 읽는 것이 아니라 보이는 것이 된다 — 자리만 정하는 것이라 새로 계산하는
 * 수는 하나도 없다(`docs/adr/020`).
 */
function Levels({ readout }: { readout: Readout }) {
  const 지금 = { price: readout.close, title: "지금", color: "#eaecef", now: true, note: "" };
  const 줄들 = [...수평선(readout).map((줄) => ({ ...줄, now: false })), 지금].sort(
    (a, b) => b.price - a.price,
  );

  return (
    <ul className="mt-2 divide-y divide-line-soft rounded bg-surface-2 px-2">
      {줄들.map((줄) => (
        <li
          key={줄.title}
          className={`flex items-baseline gap-2 py-1 text-xs tabular-nums ${
            줄.now ? "font-medium text-ink" : "text-ink-2"
          }`}
        >
          <span
            className="inline-block h-0.5 w-4 shrink-0 rounded-full"
            style={{ backgroundColor: 줄.color }}
            aria-hidden="true"
          />
          <span className={줄.now ? "" : "text-ink-3"}>{줄.title}</span>
          <span className="ml-auto">{price(줄.price)}</span>
          <span className="w-16 shrink-0 text-right text-[11px] text-ink-4">{줄.note}</span>
        </li>
      ))}
    </ul>
  );
}

/**
 * 곡선이 아니라 **자리**인 것들. 지지·저항·매물대는 시간에 따라 움직이는 값이 아니므로
 * 가로선이 원래 모양이다.
 *
 * **거리(%)는 서버가 준 것만 적는다.** 여기서 `(선 − 지금) ÷ 지금` 을 하면 화면이 수를
 * 만드는 것이 되고, 그 규칙은 이 저장소가 한 번 정해 지켜 온 것이다.
 */
function 수평선(readout: Readout) {
  const out: { price: number; color: string; title: string; note: string }[] = [];
  const 더하기 = (value: number | null | undefined, color: string, title: string, note = "") => {
    if (value !== null && value !== undefined) {
      out.push({ price: value, color, title, note });
    }
  };

  더하기(readout.support?.near, "#0ecb81", "지지", 거리(readout.support?.distancePercent));
  더하기(readout.resistance?.near, "#f6465d", "저항", 거리(readout.resistance?.distancePercent));
  더하기(readout.volume.pointOfControl, "#f0b90b", "매물대 중심");
  return out;
}

function 거리(distancePercent: number | null | undefined): string {
  return distancePercent === null || distancePercent === undefined
    ? ""
    : percent(Math.abs(distancePercent));
}
