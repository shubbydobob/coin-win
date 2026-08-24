import { percent, price } from "../../format";
import { TEXT_TONE, type Tone } from "../../shared/tone";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];
type Zone = components["schemas"]["ZoneResponse"];
type Position = Readout["ichimoku"];

/**
 * 지금 가격이 지표상 어디에 서 있나 — 15분 · 1시간 · 4시간.
 *
 * **표로 놓는 이유는 세로로 읽기 위해서다.** 이 화면에서 답해야 하는 질문은 "15분은
 * 어떤가" 가 아니라 **"세 주기가 같은 말을 하는가"** 다. 주기별 카드로 흩어 놓으면 그
 * 비교가 눈이 아니라 머릿속에서 일어나고, 그것이 「내 자리」 카드를 만든 이유와 같은 문제다.
 *
 * **방향을 말하지 않는다.** 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 종류의 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`). 색도 위치를 뜻하지 좋고
 * 나쁨을 뜻하지 않는다 — `shared/tone` 의 규칙 그대로다.
 *
 * **수는 하나도 만들지 않는다.** 대까지의 거리도 서버가 낸다(`docs/adr/020`).
 */
export function ReadoutPanel({ readouts }: { readouts: Readout[] }) {
  return (
    <section aria-label="지표 판독" className="rounded-lg border border-line bg-surface p-3">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[34rem] border-collapse text-sm">
          <thead>
            <tr className="text-left text-[11px] text-ink-4">
              <th className="pb-1 font-normal">주기</th>
              <th className="pb-1 font-normal">일목</th>
              <th className="pb-1 font-normal">볼린저</th>
              <th className="pb-1 text-right font-normal">가까운 지지</th>
              <th className="pb-1 text-right font-normal">가까운 저항</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line-soft">
            {readouts.map((readout) => (
              <Row key={readout.interval} readout={readout} />
            ))}
          </tbody>
        </table>
      </div>

      <p className="mt-2 text-[11px] leading-snug text-ink-4">
        대는 백테스트가 7년으로 검증한 것과 <b>같은 규칙</b>으로 잡는다 — 피벗을 세고 ATR 안의
        것을 한 대로 묶는다. <b>저항이 비어 있는 것은 정상이다</b>: 위쪽에 사람이 반응한 적
        있는 자리가 아직 없다는 뜻이고, 그것을 0 이나 화면 끝으로 채우면 없는 대가 생긴다.
      </p>
    </section>
  );
}

function Row({ readout }: { readout: Readout }) {
  return (
    <tr className="align-top">
      <th scope="row" className="py-2 text-left font-medium text-ink">
        {LABEL[readout.interval] ?? readout.interval}
        <span className="mt-0.5 block text-[10px] font-normal text-ink-4">
          ATR {price(readout.atr)}
        </span>
      </th>

      <td className="py-2">
        <span className={`font-medium ${TEXT_TONE[toneOf(readout.ichimoku)]}`}>
          {POSITION[readout.ichimoku] ?? readout.ichimoku}
        </span>
        <span className="mt-0.5 block text-[10px] tabular-nums text-ink-4">
          {price(readout.cloudBottom)} ~ {price(readout.cloudTop)}
        </span>
      </td>

      <td className="py-2">
        <span className={`font-medium ${TEXT_TONE[toneOf(readout.bollinger)]}`}>
          {BAND[readout.bollinger] ?? readout.bollinger}
        </span>
        <span className="mt-0.5 block text-[10px] tabular-nums text-ink-4">
          폭 {percent(readout.bandWidthPercent)}
        </span>
      </td>

      <ZoneCell zone={readout.support} 아래 />
      <ZoneCell zone={readout.resistance} />
    </tr>
  );
}

/**
 * 대 한 칸.
 *
 * **거리에 부호를 화면이 붙인다.** 서버는 언제나 0 이상으로 낸다 — 위인지 아래인지는 이 값이
 * 지지에 붙었는지 저항에 붙었는지가 이미 말하기 때문이다. 여기서 `−` 를 앞에 놓는 것은
 * 계산이 아니라 **같은 사실을 읽기 쉽게 적는 것**이고, 두 칸이 나란히 있을 때 어느 쪽이
 * 가까운지가 부호 없이는 눈으로 비교되지 않는다.
 */
function ZoneCell({ zone, 아래 = false }: { zone: Zone | null; 아래?: boolean }) {
  if (!zone) {
    return (
      <td className="py-2 text-right text-ink-4">
        —<span className="mt-0.5 block text-[10px]">없다</span>
      </td>
    );
  }
  return (
    <td className="py-2 text-right tabular-nums">
      <span className="text-ink">{price(zone.near)}</span>
      <span className="mt-0.5 block text-[10px] text-ink-3">
        {아래 ? "−" : "+"}
        {percent(zone.distancePercent)} · 터치 {zone.touches}
      </span>
    </td>
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
