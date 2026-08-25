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
import { useEffect, useMemo, useRef } from "react";

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

  /*
    **범위를 판독 봉에 맞춘다. `new Date()` 로 잡으면 안 된다.**

    첫 판이 그랬고 차트가 영원히 "가져오는 중" 이었다. 렌더마다 지금 시각이 새로 나오므로
    `queryKey` 가 매번 달라지고, 그러면 요청이 끝나기도 전에 다음 요청이 시작된다 — 서버는
    멀쩡한데 화면만 멈춘 것처럼 보인다.

    `readout.at` 은 서버가 판독한 봉의 시각이라 **봉이 바뀔 때만 바뀐다.** 15초마다 다시
    물어도 같은 값이므로 키가 안정되고, 차트의 오른쪽 끝이 판독 기준과 같아지는 것은 덤이 아니라
    옳은 모양이다 — 요약 줄의 값과 차트가 다른 시점을 말하면 안 된다.
  */
  const 범위 = useMemo(() => 조회범위(readout.interval, bars, readout.at), [
    readout.interval,
    readout.at,
    bars,
  ]);
  const candles = useQuery({
    queryKey: ["candles", symbol, readout.interval, 범위.from, 범위.to],
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

  // 캔들. **축 맞추기는 여기서만 한다** — 선이 갱신될 때마다 하면 15초마다 확대가 풀린다.
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
    차트.current?.timeScale().fitContent();
  }, [candles.data]);

  // 선. 판독은 15초마다 새로 오고 그때마다 값이 조금씩 움직인다.
  useEffect(() => {
    const series = 캔들.current;
    if (!series) {
      return;
    }
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
    return () => lines.forEach((line) => series.removePriceLine(line));
  }, [readout]);

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
 * 몇 봉을 볼 것인가를 시각 범위로 옮긴다. 서버가 `from`·`to` 를 받기 때문이다.
 *
 * **끝을 지금이 아니라 판독 봉에 맞춘다.** 지금 시각을 쓰면 렌더마다 값이 달라져 요청이 끝없이
 * 새로 시작된다. 그리고 요약 줄과 차트가 같은 시점을 말해야 한다.
 */
function 조회범위(interval: string, bars: number, anchor: string) {
  const 분 = INTERVAL_MINUTES[interval] ?? 15;
  // 판독 봉은 아직 안 닫혔을 수 있다. 한 봉 더 뒤까지 달라고 해야 그 봉이 잘리지 않는다.
  const to = new Date(Date.parse(anchor) + 분 * 60_000);
  const from = new Date(to.getTime() - 분 * 60_000 * bars);
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
