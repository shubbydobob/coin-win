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
      선("#8a8f98", 0).setData(점(data.ichimoku, (p) => p.leadingSpanA));
      선("#8a8f98", 0).setData(점(data.ichimoku, (p) => p.leadingSpanB));
      선("#c9a227", 0, true).setData(점(data.ichimoku, (p) => p.conversionLine));
      선("#3b6ea5", 0, true).setData(점(data.ichimoku, (p) => p.baseLine));
    }
    if (켠것.bollinger) {
      선("#4a7fb5", 0, true).setData(점(data.bollinger, (p) => p.upper));
      선("#4a7fb5", 0, true).setData(점(data.bollinger, (p) => p.lower));
    }
    if (켠것.movingAverages) {
      data.movingAverages.forEach((ma) => {
        선(MA_COLOR[ma.period] ?? "#848e9c", 0).setData(점(ma.points, (p) => p.value));
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
    chart.timeScale().fitContent();
  }, [series.data, readout, 켠것]);

  return (
    <div className="mt-2">
      <div className="mb-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
        {OVERLAYS.map(({ key, label, swatches }) => (
          <label key={key} className="flex cursor-pointer items-center gap-1 text-ink-3">
            <input
              type="checkbox"
              checked={켠것[key]}
              onChange={(event) => 켜기((전) => ({ ...전, [key]: event.target.checked }))}
              className="size-3"
            />
            {swatches.map((color) => (
              <span
                key={color}
                className="inline-block h-0.5 w-3 rounded-full"
                style={{ backgroundColor: color }}
                aria-hidden="true"
              />
            ))}
            {label}
          </label>
        ))}
      </div>

      <div ref={창} className="h-[440px] w-full" />

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
            ? `${series.data.count}봉 · 마지막 ${price(readout.close)}${빈것(series.data)}`
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
  const 롱 = stances.filter((s) => s.stance === "LONG").length;
  const 숏 = stances.filter((s) => s.stance === "SHORT").length;

  return (
    <section aria-label="지표가 선 자리" className="mt-2 rounded bg-surface-2 px-2 py-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="text-ink-3">지표가 선 자리</span>
        <SideChip side="LONG">{롱}</SideChip>
        <SideChip side="SHORT">{숏}</SideChip>
        <span className="ml-auto text-[10px] text-ink-4">셀 뿐 어느 쪽이 유리한지는 말하지 않는다</span>
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
                <SideChip side={STANCE_CHIP[stance.stance]!} />
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
          <b>경계는 전부 관습이다.</b> RSI 50, 이동평균 정배열, MACD 시그널 교차 —
          어느 것도 이 저장소가 재 본 수가 아니다. 그래서 <b>딱지는 어느 쪽에 서 있는가까지만
          말하고 유리한 쪽은 말하지 않는다.</b> 지지·저항 기반 규칙은 실제로 7년 15,110봉에서
          반증됐다(<code>docs/adr/021</code>).
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

/** 중립과 말할 수 없음에는 딱지가 없다 — 둘 다 "어느 쪽" 이 아니기 때문이다. */
const STANCE_CHIP: Record<string, ChipSide | undefined> = {
  LONG: "LONG",
  SHORT: "SHORT",
};

type Overlay = "ichimoku" | "bollinger" | "movingAverages";

/**
 * 이동평균 다섯 구간의 색. **짧을수록 밝다** — 다섯 선을 색 이름으로 외우지 않고 밝기로
 * 읽으라는 뜻이다. 구간을 바꾸면 여기도 함께 바꿔야 하고, 없는 구간은 회색으로 떨어진다.
 */
const MA_COLOR: Record<number, string> = {
  10: "#ffffff",
  20: "#eaecef",
  50: "#f0b90b",
  200: "#f6465d",
  300: "#8a4bff",
};

const OVERLAYS: { key: Overlay; label: string; swatches: string[] }[] = [
  { key: "ichimoku", label: "일목", swatches: ["#8a8f98", "#c9a227", "#3b6ea5"] },
  { key: "bollinger", label: "볼린저 (중심은 MA20 과 같다)", swatches: ["#4a7fb5"] },
  {
    key: "movingAverages",
    label: "이동평균 10·20·50·200·300",
    swatches: ["#ffffff", "#eaecef", "#f0b90b", "#f6465d", "#8a4bff"],
  },
];

/** 값이 있는 점만. `null` 을 0 으로 바꾸면 차트 바닥에 없는 선이 생긴다. */
function 점<T extends { at: string }>(points: T[], pick: (point: T) => number | null | undefined) {
  return points
    .map((point) => ({ time: 초(point.at), value: pick(point) }))
    .filter((row): row is { time: UTCTimestamp; value: number } =>
      row.value !== null && row.value !== undefined);
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
