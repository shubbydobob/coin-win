import { useState } from "react";

import { PriceChart } from "./PriceChart";
import { NOTHING, bandRatio, orNothing, percent, price, ratio } from "../../format";
import { TEXT_TONE, type Tone } from "../../shared/tone";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];
type Position = Readout["ichimoku"]["position"];

/**
 * 지금 가격이 지표상 어디에 서 있나 — 15분 · 1시간 · 4시간.
 *
 * **띠를 캔들로 바꿨다.** 앞판은 가격 축 하나에 지지·저항·매물대를 칠한 가로 띠였다. 값은
 * 전부 맞았지만 **가격이 어떻게 거기까지 왔는지가 없었다** — 지금 값이 저항 아래라는 사실은,
 * 그 저항을 방금 찍고 내려온 것인지 사흘째 못 닿고 있는 것인지에 따라 전혀 다른 뜻이다.
 * 그 차이를 그리는 것이 캔들이고, 그것은 띠로는 할 수 없는 일이었다.
 *
 * **세 줄 요약을 남겼다.** 이 화면이 답하는 질문은 "15분은 어떤가" 가 아니라 **"세 주기가
 * 같은 말을 하는가"** 다. 차트를 탭으로 만들면 한 번에 하나만 보이므로 그 비교가 사라진다 —
 * 위에 세 줄을 세로로 쌓아 두고, 자세히 볼 하나만 아래 차트에서 연다.
 *
 * **방향을 말하지 않는다.** 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 종류의 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`).
 *
 * **수는 하나도 만들지 않는다.** 차트의 좌표는 그리기 좌표이고 사람이 읽는 수는 서버가 낸
 * 값이다(`docs/adr/020`).
 */
export function ReadoutPanel({ readouts, symbol }: { readouts: Readout[]; symbol: string }) {
  const [고른것, 고르기] = useState(readouts[0]?.interval ?? "15m");
  const 보는것 = readouts.find((readout) => readout.interval === 고른것) ?? readouts[0];

  return (
    <section aria-label="지표 판독" className="rounded-lg border border-line bg-surface p-3">
      {/*
        세 주기 요약. **차트보다 위에 둔다** — 여기서 "셋이 같은 말을 하는가" 를 먼저 보고,
        어긋나는 주기가 있으면 그것을 아래에서 연다.
      */}
      <ul className="space-y-1">
        {readouts.map((readout) => (
          <li key={readout.interval}>
            <button
              type="button"
              onClick={() => 고르기(readout.interval)}
              aria-pressed={readout.interval === 고른것}
              className={`flex w-full items-baseline justify-between gap-2 rounded px-2 py-1.5 text-xs ${
                readout.interval === 고른것
                  ? "bg-surface-2 ring-1 ring-line"
                  : "hover:bg-surface-2/60"
              }`}
            >
              <span className="font-medium text-ink">
                {LABEL[readout.interval] ?? readout.interval}
              </span>
              <span className="flex items-baseline gap-2">
                <span className={`font-medium ${TEXT_TONE[toneOf(readout.ichimoku.position)]}`}>
                  {POSITION[readout.ichimoku.position] ?? readout.ichimoku.position}
                </span>
                <span className={`font-medium ${TEXT_TONE[toneOf(readout.bollinger.position)]}`}>
                  {BAND[readout.bollinger.position] ?? readout.bollinger.position}
                </span>
                <span className="tabular-nums text-ink-4">
                  폭 {percent(readout.bollinger.bandWidthPercent)}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>

      {보는것 && (
        <>
          <div className="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1 text-[11px] text-ink-3">
            <span className="font-medium text-ink-2">
              {LABEL[보는것.interval] ?? 보는것.interval} 차트
            </span>
            <Distance label="지지" zone={보는것.support} />
            <Distance label="저항" zone={보는것.resistance} />
            {/* 매물대는 언제나 온다 — 서버 DTO 가 nullable 이 아니다. 없는 것은 대뿐이다. */}
            <span className="tabular-nums">매물대 중심 {price(보는것.volume.pointOfControl)}</span>
          </div>

          {/*
            **위치 딱지가 접어 버리는 것들.** 「구름 위」 는 아슬아슬하게 위인지 한참 위인지를
            같은 사실로 만들고, 「밴드 안」 은 하단에 붙어 있는 것과 상단 바로 아래인 것을 같은
            사실로 만든다. 근거는 `docs/spec/indicator-usage.md` § 4.

            **거리는 ATR 배수다.** 같은 300 도 조용한 장에서는 큰 값이고 급한 장에서는 아무것도
            아니다 — 대의 폭과 손절 버퍼가 이미 그 단위로 정해져 있다.
          */}
          <dl className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1 text-[11px] text-ink-3">
            <Fact 이름="구름 두께" 값={`${ratio(보는것.ichimoku.cloudThickness)} ATR`} />
            <Fact
              이름="구름"
              값={보는것.ichimoku.bullishCloud ? "선행1 이 위" : "선행2 가 위"}
            />
            <Fact 이름="전환−기준" 값={`${ratio(보는것.ichimoku.conversionGap)} ATR`} />
            <Fact 이름="기준선까지" 값={`${ratio(보는것.ichimoku.baseLineGap)} ATR`} />
            <Fact
              이름="밴드 안 어디"
              값={orNothing(보는것.bollinger.ratio, (안) => bandRatio(안))}
            />
          </dl>
          <PriceChart symbol={symbol} readout={보는것} />
        </>
      )}

      <details className="group mt-3">
        <summary className="cursor-pointer list-none text-[11px] text-ink-4 hover:text-ink-3">
          차트 읽는 법
          <span aria-hidden="true" className="group-open:hidden">{" ▸"}</span>
          <span aria-hidden="true" className="hidden group-open:inline">{" ▾"}</span>
        </summary>

        <p className="mt-2 text-[11px] leading-snug text-ink-4">
          가로선은 전부 <b>지금 봉 기준의 값</b>이다 — 구름 위·아래, 밴드 상·중·하단, 가장 가까운
          지지·저항, 매물대 중심. <b>실제로는 시간에 따라 움직이는 값인데 수평선으로 그려져
          있다</b>: 판독 응답이 지금 봉 하나의 값만 주기 때문이다. 자리는 정확하고 모양은 아직
          아니다.
        </p>

        <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
          <b>매물대는 대와 다른 것을 잰다</b> — 대가 「몇 번 되돌아섰나」라면 매물대는 「거기서
          얼마나 거래됐나」다. 둘이 같은 자리를 가리키면 그 자리에 대한 증거가 둘인 셈이다.
          <b>다만 매물대는 백테스트를 통과한 적이 없고</b>, 봉 안에서 거래량이 어느 가격에
          몰렸는지는 캔들만으로 알 수 없어 고르게 나눈 <b>근사</b>다.
          <b>골든 포켓에서 되돌아온다는 것도 이 도구가 재 본 적 없는 주장이다.</b>
          <b>비어 있는 선은 정상이다</b> — 그 방향에 사람이 반응한 적 있는 자리가 아직 없다는
          뜻이고, 0 으로 채우면 없는 선이 생긴다.
        </p>
      </details>
    </section>
  );
}

/** 대까지의 거리. **없으면 없다고 적는다** — 빈 자리로 두면 0% 로 읽힌다. */
function Distance({
  label,
  zone,
}: {
  label: string;
  zone: components["schemas"]["ZoneResponse"] | null | undefined;
}) {
  return (
    <span className="tabular-nums">
      {label} {zone ? `${percent(Math.abs(zone.distancePercent))}` : "없다"}
    </span>
  );
}

/**
 * 지금 이 주기의 사실 하나. <b>이름과 값을 함께 둔다</b> — 수만 늘어놓으면 `1.35` 가 무엇의
 * 배수인지가 사라지고, ATR 배수는 특히 그렇다.
 *
 * <b>「말할 수 없다」 를 빈칸으로 두지 않는다.</b> 밴드 폭이 0 이면 「밴드 안 어디」 가
 * 성립하지 않는데, 빈칸이면 그것이 0 으로 읽힌다 — `NOTHING` 이 그 자리를 채운다.
 */
function Fact({ 이름, 값 }: { 이름: string; 값: string }) {
  return (
    <div className="flex items-baseline gap-1">
      <dt className="text-ink-4">{이름}</dt>
      <dd className={`tabular-nums ${값 === NOTHING ? "text-ink-4" : ""}`}>{값}</dd>
    </div>
  );
}

/**
 * 위치를 색으로.
 *
 * **위/아래를 뜻하지 좋고 나쁨을 뜻하지 않는다.** `shared/tone` 이 정한 대로 `long` 은
 * "롱 쪽" 이고, 구름 위·밴드 위는 그 방향이라는 사실까지다. 가운데(구름 안·밴드 안)는 어느
 * 쪽도 아니므로 축 없는 값과 같은 색이다.
 */
function toneOf(position: Position): Tone {
  if (position === "ABOVE") {
    return "long";
  }
  return position === "BELOW" ? "short" : "none";
}

const LABEL: Record<string, string> = {
  "15m": "15분",
  "1h": "1시간",
  "4h": "4시간",
  "1d": "일봉",
  "1w": "주봉",
};

const POSITION: Record<string, string> = {
  ABOVE: "구름 위",
  INSIDE: "구름 안",
  BELOW: "구름 아래",
};

const BAND: Record<string, string> = {
  ABOVE: "상단 위",
  INSIDE: "밴드 안",
  BELOW: "하단 아래",
};
