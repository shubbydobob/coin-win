import { useQuery } from "@tanstack/react-query";
import {
  CandlestickSeries,
  ColorType,
  LineStyle,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from "lightweight-charts";
import { useEffect, useRef } from "react";

import { get } from "../../api/client";
import { price } from "../../format";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];

/**
 * 캔들 차트. **띠를 대신한다.**
 *
 * 앞판은 가격 축 하나에 지지·저항·매물대·포켓을 칠한 가로 띠였다. 값은 전부 맞았지만
 * **가격이 어떻게 거기까지 왔는지가 없었다** — 지금 79,305 가 저항 79,974 아래라는 사실은,
 * 그 저항을 방금 찍고 내려온 것인지 사흘째 못 닿고 있는 것인지에 따라 전혀 다른 뜻이다.
 * 캔들은 그 차이를 그림 한 장으로 말한다.
 *
 * **선은 전부 서버가 낸 값이다.** 이 파일에 지표 계산이 없다 — 일목과 볼린저는 자바가
 * 트레이딩뷰 원문과 대조해 확정한 것이고(`docs/adr/014`·`015`), 여기서 다시 구현하면 두
 * 정의가 갈라진다. 그리는 좌표만 이쪽에서 만든다(`docs/adr/020`).
 *
 * **지금은 가로선이다.** 구름과 밴드는 원래 시간에 따라 움직이는데, 판독 응답은 **지금 봉
 * 하나의 값**만 준다. 그래서 이 판에서는 그 값들을 수평선으로 놓는다 — 자리는 정확하고
 * 모양은 아직 아니다. 시계열을 서버가 내주면 이 파일의 `createPriceLine` 자리가 선 시리즈로
 * 바뀐다.
 *
 * **캔버스라 자동 테스트가 닿지 않는다.** 이 저장소는 화면 테스트로 두 번 거짓 초록을 잡았고
 * (`findByText`·`findByRole`), 이 그림은 그 그물 밖에 있다. 그래서 **차트가 비었을 때와
 * 실패했을 때를 DOM 으로 말한다** — 최소한 "값이 왔는가" 는 테스트가 볼 수 있다.
 */
export function PriceChart({
  symbol,
  readout,
  bars = 200,
}: {
  symbol: string;
  readout: Readout;
  bars?: number;
}) {
  const 창 = useRef<HTMLDivElement>(null);
  const 차트 = useRef<IChartApi | null>(null);
  const 캔들 = useRef<ISeriesApi<"Candlestick"> | null>(null);

  const 범위 = 조회범위(readout.interval, bars);
  const candles = useQuery({
    queryKey: ["candles", symbol, readout.interval, 범위.from],
    queryFn: () =>
      get("/api/markets/{symbol}/candles", {
        path: { symbol },
        query: { interval: readout.interval, from: 범위.from, to: 범위.to },
      }),
    staleTime: 60_000,
  });

  // 차트는 한 번만 만든다. 값이 바뀔 때마다 다시 만들면 사용자가 옮겨 둔 축이 매번 되돌아간다.
  useEffect(() => {
    if (!창.current || 차트.current) {
      return;
    }
    const chart = createChart(창.current, {
      layout: {
        background: { type: ColorType.Solid, color: "transparent" },
        textColor: "#848e9c",
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: "#21262d" },
        horzLines: { color: "#21262d" },
      },
      rightPriceScale: { borderColor: "#2b3139" },
      timeScale: { borderColor: "#2b3139", timeVisible: true },
      crosshair: { mode: 0 },
      height: 260,
      autoSize: true,
    });
    차트.current = chart;
    캔들.current = chart.addSeries(CandlestickSeries, {
      upColor: "#0ecb81",
      downColor: "#f6465d",
      borderUpColor: "#0ecb81",
      borderDownColor: "#f6465d",
      wickUpColor: "#0ecb81",
      wickDownColor: "#f6465d",
    });
    return () => {
      chart.remove();
      차트.current = null;
      캔들.current = null;
    };
  }, []);

  // 캔들과 선을 값이 올 때마다 다시 얹는다.
  useEffect(() => {
    const series = 캔들.current;
    if (!series || !candles.data) {
      return;
    }
    series.setData(
      candles.data.candles.map((candle) => ({
        time: (Date.parse(candle.openTime) / 1000) as UTCTimestamp,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
      })),
    );
    const lines = 선들(readout).map((선) =>
      series.createPriceLine({
        price: 선.price,
        color: 선.color,
        lineWidth: 1,
        lineStyle: 선.dashed ? LineStyle.Dashed : LineStyle.Solid,
        axisLabelVisible: true,
        title: 선.title,
      }),
    );
    차트.current?.timeScale().fitContent();
    return () => lines.forEach((line) => series.removePriceLine(line));
  }, [candles.data, readout]);

  return (
    <div className="mt-2">
      <div ref={창} className="h-[260px] w-full" />
      {/*
        **캔버스 밖에 상태를 적는다.** 그림 안에서 벌어지는 일은 테스트도 스크린리더도 못 본다.
        비었는지·실패했는지·몇 봉인지는 글로 남긴다.
      */}
      <p className="mt-1 text-[11px] text-ink-4" aria-live="polite">
        {candles.isError
          ? "캔들을 가져오지 못했다"
          : candles.data
            ? `${candles.data.count}봉 · 마지막 ${price(readout.close)}`
            : "캔들을 가져오는 중"}
      </p>
    </div>
  );
}

/** 차트에 얹을 가로선. **없는 값은 선도 없다** — 0 으로 그리면 바닥에 가짜 선이 생긴다. */
function 선들(readout: Readout) {
  const out: { price: number; color: string; title: string; dashed?: boolean }[] = [];
  const 더하기 = (value: number | null | undefined, color: string, title: string, dashed = false) => {
    if (value !== null && value !== undefined) {
      out.push({ price: value, color, title, dashed });
    }
  };

  더하기(readout.cloudTop, "#5e6673", "구름 위", true);
  더하기(readout.cloudBottom, "#5e6673", "구름 아래", true);
  더하기(readout.bollingerUpper, "#3b6ea5", "밴드 상단", true);
  더하기(readout.bollingerMiddle, "#3b6ea5", "밴드 중심", true);
  더하기(readout.bollingerLower, "#3b6ea5", "밴드 하단", true);

  // 서버가 이미 **가장 가까운 대 하나씩만** 준다. 여럿을 다 그리면 선이 열 개를 넘어
  // 캔들이 안 보이는데, 그 판단은 이미 서버 쪽에서 끝나 있다.
  더하기(readout.support?.near, "#0ecb81", "지지");
  더하기(readout.resistance?.near, "#f6465d", "저항");
  더하기(readout.volume.pointOfControl, "#f0b90b", "매물대 중심");

  return out;
}

/**
 * 몇 봉을 볼 것인가를 시각 범위로 옮긴다.
 *
 * 서버가 `from`·`to` 를 받으므로 봉 수를 시간으로 바꿔야 한다. 여유를 두 배로 잡는 이유는
 * 거래소가 빈 구간을 돌려주는 경우가 있어서다 — 모자라면 화면이 짧아지고, 남으면 서버가 자른다.
 */
function 조회범위(interval: string, bars: number) {
  const 분 = INTERVAL_MINUTES[interval] ?? 15;
  const to = new Date();
  const from = new Date(to.getTime() - 분 * 60_000 * bars * 2);
  return { from: from.toISOString(), to: to.toISOString() };
}

const INTERVAL_MINUTES: Record<string, number> = {
  "1m": 1,
  "5m": 5,
  "15m": 15,
  "1h": 60,
  "4h": 240,
  "1d": 1440,
};
