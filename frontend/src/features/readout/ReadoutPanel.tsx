import { TimeframeTrack } from "./TimeframeTrack";
import { percent } from "../../format";
import { TEXT_TONE, type Tone } from "../../shared/tone";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];
type Position = Readout["ichimoku"];

/**
 * 지금 가격이 지표상 어디에 서 있나 — 15분 · 1시간 · 4시간.
 *
 * **표를 띠로 바꿨다.** 처음에는 열 일곱 개짜리 표였고 한 행에 값이 열 개가 넘었다. 값은 전부
 * 맞았지만 **무엇을 먼저 볼지를 화면이 정해 주지 않아** 매번 사람이 정해야 했다. 지지·저항·
 * 매물대·포켓은 전부 *가격 위의 자리*이므로 한 축에 그리는 것이 원래 모양이고, 그러면 "위에
 * 뭐가 있고 아래에 뭐가 있나" 가 읽는 것이 아니라 보이는 것이 된다.
 *
 * **세 줄을 세로로 쌓는 것은 그대로다.** 이 화면이 답하는 질문은 "15분은 어떤가" 가 아니라
 * **"세 주기가 같은 말을 하는가"** 이고, 세 띠의 지금 선이 세로로 정렬돼 있어야 그 비교가
 * 눈에서 일어난다.
 *
 * **글을 접었다.** 설명 세 문단이 표보다 길었다. 읽는 법은 한 번 읽으면 되는 것이라
 * 감시 화면의 「색과 눈금 읽는 법」과 같은 자리에 접어 두었다.
 *
 * **방향을 말하지 않는다.** 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 종류의 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`). 색도 위치를 뜻하지 좋고
 * 나쁨을 뜻하지 않는다 — `shared/tone` 의 규칙 그대로다.
 *
 * **수는 하나도 만들지 않는다.** 띠의 좌표는 그리기 좌표이고 사람이 읽는 수는 서버가 낸
 * 값이다(`docs/adr/020`, `shared/Meter` 가 같은 말을 적어 두었다).
 */
export function ReadoutPanel({ readouts }: { readouts: Readout[] }) {
  return (
    <section aria-label="지표 판독" className="rounded-lg border border-line bg-surface p-3">
      <ul className="space-y-3">
        {readouts.map((readout) => (
          <li key={readout.interval}>
            <div className="flex items-baseline justify-between gap-2 text-xs">
              <span className="font-medium text-ink">
                {LABEL[readout.interval] ?? readout.interval}
              </span>
              <span className="flex items-baseline gap-2">
                <span className={`font-medium ${TEXT_TONE[toneOf(readout.ichimoku)]}`}>
                  {POSITION[readout.ichimoku] ?? readout.ichimoku}
                </span>
                <span className={`font-medium ${TEXT_TONE[toneOf(readout.bollinger)]}`}>
                  {BAND[readout.bollinger] ?? readout.bollinger}
                </span>
                <span className="tabular-nums text-ink-4">
                  폭 {percent(readout.bandWidthPercent)}
                </span>
              </span>
            </div>
            <TimeframeTrack readout={readout} label={LABEL[readout.interval] ?? readout.interval} />
          </li>
        ))}
      </ul>

      {/*
        읽는 법은 한 번 읽으면 되는 것이다. 펼쳐 두면 화면에서 가장 긴 덩어리가 되고,
        그러면 매일 보는 값들이 한 번 읽을 글에 밀린다. 감시 화면과 같은 자리·같은 모양이다.
      */}
      <details className="group mt-3">
        <summary className="cursor-pointer list-none text-[11px] text-ink-4 hover:text-ink-3">
          띠 읽는 법
          <span aria-hidden="true" className="group-open:hidden">{" ▸"}</span>
          <span aria-hidden="true" className="hidden group-open:inline">{" ▾"}</span>
        </summary>

        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink-4">
          <span className="inline-flex items-center gap-1">
            <span className="inline-block h-2.5 w-4 rounded-sm bg-warn/20" aria-hidden="true" />
            매물대
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="inline-block h-2.5 w-px bg-ink-3" aria-hidden="true" />
            지지 · 저항
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="inline-block h-2.5 w-px bg-ink" aria-hidden="true" />
            지금
          </span>
        </div>

        <p className="mt-2 text-[11px] leading-snug text-ink-4">
          띠의 폭은 <b>지금 가격 위아래 2 ATR</b> 이다. 같은 화면 폭이 주기마다 다른 가격 범위를
          뜻하므로, 세 띠는 <b>거리가 아니라 「그 주기 기준으로 가까운가」를 견준다.</b>
          <b>창 밖은 지우지 않고 가장자리에 붙인다</b> — 화살표(‹ ›)가 어느 쪽인지 말한다.
          4시간 지지가 −16% 인 것은 사실이고, 지우면 "아래에 아무것도 없다" 가 된다.
          <b>비어 있는 것도 정상이다</b>: 그 방향에 사람이 반응한 적 있는 자리가 아직 없거나
          스윙 한쪽이 안 잡혔다는 뜻이고, 0 이나 창 끝으로 채우면 없는 선이 생긴다.
        </p>

        <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
          <b>매물대는 대와 다른 것을 잰다</b> — 대가 「몇 번 되돌아섰나」라면 매물대는 「거기서
          얼마나 거래됐나」다. 둘이 같은 자리를 가리키면 그 자리에 대한 증거가 둘인 셈이다.
          <b>다만 매물대는 백테스트를 통과한 적이 없고</b>, 봉 안에서 거래량이 어느 가격에
          몰렸는지는 캔들만으로 알 수 없어 고르게 나눈 <b>근사</b>다.
          <b>골든 포켓에서 되돌아온다는 것도 이 도구가 재 본 적 없는 주장이다.</b>
          <b>구름과 골든 포켓은 띠에 그리지 않는다</b> — 구름은 「구름 위/아래」가 이미 같은
          말을 하고, 골든 포켓은 폭이 창의 1~2% 라 그리면 부스러기로 보인다. 두 값은 띠에
          마우스를 올리면 나오는 문장에 그대로 있다.
        </p>
      </details>
    </section>
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
