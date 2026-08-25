import { percent, price } from "../../format";
import { Decimal } from "../../shared/Decimal";
import { TEXT_TONE, type Tone } from "../../shared/tone";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];
type Zone = components["schemas"]["ZoneResponse"];
type Fibonacci = components["schemas"]["FibonacciResponse"];
type Volume = components["schemas"]["VolumeProfileResponse"];
type Shelf = components["schemas"]["VolumeShelfResponse"];
type Position = Readout["ichimoku"];

/**
 * 지금 가격이 지표상 어디에 서 있나 — 15분 · 1시간 · 4시간.
 *
 * **표로 놓는 이유는 세로로 읽기 위해서다.** 이 화면에서 답해야 하는 질문은 "15분은
 * 어떤가" 가 아니라 **"세 주기가 같은 말을 하는가"** 다. 주기별 카드로 흩어 놓으면 그
 * 비교가 눈이 아니라 머릿속에서 일어난다.
 *
 * **열을 두 묶음으로 가른다.** 왼쪽 둘(일목·볼린저)은 **지금 어디에 서 있나**이고 오른쪽
 * 넷은 **주변에 무엇이 있나**다. 읽는 방식이 다른데 한 줄로 이어 놓으면 눈이 그 경계를
 * 매번 스스로 그어야 하고, 이 표는 한 행에 값이 열 개가 넘어 그 부담이 크다.
 *
 * **먼 것은 눌러 둔다.** 4시간 지지가 −16% 인데 15분 지지 −0.2% 와 같은 굵기로 놓여 있으면
 * 둘이 같은 무게로 읽힌다. 거리는 서버가 낸 값이고 화면은 그 값으로 **색만** 정한다.
 *
 * **방향을 말하지 않는다.** 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 종류의 전제를 7년 15,110봉에서 반증했다(`docs/adr/021`). 색도 위치를 뜻하지 좋고
 * 나쁨을 뜻하지 않는다 — `shared/tone` 의 규칙 그대로다.
 *
 * **수는 하나도 만들지 않는다.** 대까지의 거리도 서버가 낸다(`docs/adr/020`). 자릿수도
 * 줄이지 않는다 — 뒷자리는 `Decimal` 이 색만 낮춘다.
 */
export function ReadoutPanel({ readouts }: { readouts: Readout[] }) {
  return (
    <section aria-label="지표 판독" className="rounded-lg border border-line bg-surface p-3">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[40rem] border-collapse text-sm">
          <thead>
            {/*
              두 단계 헤더. 위 줄이 열 묶음의 이름이고 아래 줄이 열 이름이다. 위 줄이 없으면
              "볼린저" 와 "가까운 지지" 가 같은 종류로 보인다 — 하나는 상태이고 하나는 자리다.
            */}
            <tr className="text-left text-[10px] text-ink-4">
              <th className="pb-0.5 font-normal" />
              <th className="pb-0.5 font-normal" colSpan={2}>
                지금 어디에 서 있나
              </th>
              {/*
                묶음 이름은 묶음이 **시작하는 쪽**에 붙인다. 오른쪽 끝에 두었더니 네 열을
                가리키는 이름이 맨 끝 열(골든 포켓) 하나만 가리키는 것처럼 보였다.
              */}
              <th
                className="border-l border-line-soft pb-0.5 pl-3 text-left font-normal"
                colSpan={4}
              >
                주변에 무엇이 있나
              </th>
            </tr>
            <tr className="text-left text-[11px] text-ink-3">
              <th className="pb-1.5 font-normal">주기</th>
              <th className="pb-1.5 font-normal">일목</th>
              <th className="pb-1.5 font-normal">볼린저</th>
              <th className="border-l border-line-soft pb-1.5 pl-3 text-right font-normal">
                가까운 지지
              </th>
              <th className="pb-1.5 text-right font-normal">가까운 저항</th>
              <th className="pb-1.5 text-right font-normal">매물대</th>
              <th className="pb-1.5 text-right font-normal">골든 포켓</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line-soft">
            {readouts.map((readout) => (
              <Row key={readout.interval} readout={readout} />
            ))}
          </tbody>
        </table>
      </div>

      <p className="mt-3 text-[11px] leading-snug text-ink-4">
        <b>왼쪽 둘은 상태, 오른쪽 넷은 자리다.</b> 대와 되돌림은 <b>같은 피벗 위에 그려진다</b> —
        대는 백테스트가 7년으로 검증한 규칙 그대로이고, 골든 포켓은 그 마지막 스윙의 0.618~0.65
        다. <b>비어 있는 칸은 정상이다</b>: 위쪽에 사람이 반응한 적 있는 자리가 아직 없거나 스윙
        한쪽이 안 잡혔다는 뜻이고, 그것을 0 이나 화면 끝으로 채우면 없는 선이 생긴다.
        <b>골든 포켓에서 되돌아온다는 것은 이 도구가 재 본 적 없는 주장이다</b> — 여기 적는 것은
        지금 그 안인가까지다.
      </p>

      <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
        <b>매물대는 다른 것을 잰다</b> — 대가 「몇 번 되돌아섰나」라면 매물대는 「거기서 얼마나
        거래됐나」다. <b>「두께」로 적은 %가 그것</b>이고 그 옆의 %는 거리다. 봉 300개의
        고가~저가를 24칸으로 나눠 평균의 1.5배가 넘는 칸을 이어 붙였다. 대와 매물대가 비슷한
        값을 가리키면 그 자리에 대한 증거가 둘인 셈이다.
        <b>다만 이 수치는 백테스트를 통과한 적이 없다</b> — 일목·볼린저·대와 같은 무게로 읽으면
        안 된다. 봉 안에서 거래량이 어느 가격에 몰렸는지는 캔들만으로 알 수 없어 고가~저가에
        고르게 나눈 <b>근사</b>이기도 하다.
      </p>

      <p className="mt-1.5 text-[11px] leading-snug text-ink-4">
        <b>흐린 뒷자리도 값이다.</b> 서버가 낸 자릿수를 하나도 줄이지 않고 색만 낮췄다 — 줄이면
        화면이 반올림을 하게 되고, 그 순간 화면의 수와 서버의 수가 갈라진다.
      </p>
    </section>
  );
}

function Row({ readout }: { readout: Readout }) {
  return (
    <tr className="align-top">
      <th scope="row" className="py-2.5 text-left font-medium text-ink">
        {LABEL[readout.interval] ?? readout.interval}
        <span className="mt-0.5 block text-[10px] font-normal text-ink-4">
          ATR <Decimal text={price(readout.atr)} />
        </span>
      </th>

      <td className="py-2.5">
        <span className={`font-medium ${TEXT_TONE[toneOf(readout.ichimoku)]}`}>
          {POSITION[readout.ichimoku] ?? readout.ichimoku}
        </span>
        <span className="mt-0.5 block text-[10px] tabular-nums text-ink-4">
          {price(readout.cloudBottom)} ~ {price(readout.cloudTop)}
        </span>
      </td>

      <td className="py-2.5">
        <span className={`font-medium ${TEXT_TONE[toneOf(readout.bollinger)]}`}>
          {BAND[readout.bollinger] ?? readout.bollinger}
        </span>
        <span className="mt-0.5 block text-[10px] tabular-nums text-ink-4">
          폭 <Decimal text={percent(readout.bandWidthPercent)} keep={2} />
        </span>
      </td>

      <ZoneCell zone={readout.support} 아래 세로선 />
      <ZoneCell zone={readout.resistance} />
      <VolumeCell volume={readout.volume} />
      <PocketCell fibonacci={readout.fibonacci} />
    </tr>
  );
}

/**
 * 이보다 멀면 눌러 둔다.
 *
 * **거리는 서버가 낸 값이고 여기서는 그 값으로 색만 정한다.** 화면이 만드는 수가 아니므로
 * `docs/adr/020` 에 걸리지 않는다 — 표시되는 것은 서버가 준 그 %이고 달라지는 것은 굵기뿐이다.
 *
 * 5% 는 근거 있는 수가 아니라 자리표시자다. 다만 이 경계가 있어야 4시간의 −16% 와 15분의
 * −0.2% 가 다른 무게로 읽힌다.
 */
const 먼거리 = 5;

function 거리톤(distancePercent: number): string {
  return distancePercent >= 먼거리 ? "text-ink-4" : "text-ink";
}

/**
 * 대 한 칸.
 *
 * **거리에 부호를 화면이 붙인다.** 서버는 언제나 0 이상으로 낸다 — 위인지 아래인지는 이 값이
 * 지지에 붙었는지 저항에 붙었는지가 이미 말하기 때문이다. 여기서 `−` 를 앞에 놓는 것은
 * 계산이 아니라 **같은 사실을 읽기 쉽게 적는 것**이고, 두 칸이 나란히 있을 때 어느 쪽이
 * 가까운지가 부호 없이는 눈으로 비교되지 않는다.
 */
function ZoneCell({
  zone,
  아래 = false,
  세로선 = false,
}: {
  zone: Zone | null;
  아래?: boolean;
  세로선?: boolean;
}) {
  const 테두리 = 세로선 ? "border-l border-line-soft pl-3" : "";
  if (!zone) {
    return (
      <td className={`py-2.5 text-right text-ink-4 ${테두리}`}>
        —<span className="mt-0.5 block text-[10px]">없다</span>
      </td>
    );
  }
  return (
    <td className={`py-2.5 text-right tabular-nums ${테두리}`}>
      <span className={거리톤(zone.distancePercent)}>
        <Decimal text={price(zone.near)} />
      </span>
      <span className="mt-0.5 block text-[10px] text-ink-3">
        {아래 ? "−" : "+"}
        <Decimal text={percent(zone.distancePercent)} keep={2} /> · 터치 {zone.touches}
      </span>
    </td>
  );
}

/**
 * 매물대 한 칸.
 *
 * **대와 다른 것을 잰다.** 왼쪽 두 칸(지지·저항)은 가격이 **몇 번 되돌아섰나**를 세고 이 칸은
 * 거기서 **얼마나 거래됐나**를 센다. 두 칸이 비슷한 값을 가리키면 그 자리에 대한 증거가 둘인
 * 것이고, 그것이 이 칸을 옆에 놓은 이유다.
 *
 * **두 %가 다른 뜻이라 라벨을 붙인다.** 처음에는 `−1.46% · 9.24%` 처럼 가운뎃점으로만 갈라
 * 놓았는데 앞은 거리이고 뒤는 두께다. 같은 모양의 두 수를 붙여 놓으면 사람이 둘을 같은 종류로
 * 읽는다.
 *
 * **위아래를 함께 적는다.** 매물대의 쓸모는 "이쪽으로 가려면 무엇을 지나야 하나" 이므로 한쪽만
 * 적으면 반쪽이 된다.
 *
 * **품고 있는 경우는 다르게 적는다.** 위아래가 둘 다 비어 보이는 것은 "매물대가 없다" 로
 * 읽히는데 실제로는 정반대 — **지금 물린 물량 한가운데에 있고 어느 쪽으로 움직이든 그것을
 * 지나야 한다**는 뜻이다.
 *
 * **품고 있는 구간은 낮은 값부터 적는다.** 서버가 내는 `near`/`far` 는 "먼저 닿는 쪽" 이지
 * 위아래가 아니고, 가격을 품은 구간에서는 위 모서리가 언제나 가격보다 위라 `near` 가 곧 아래
 * 모서리다. 그것을 모르고 `far ~ near` 로 적었더니 화면에 **79,139 ~ 78,666** 처럼 큰 값이
 * 앞에 오는 구간이 떴다 — 값은 맞는데 사람이 오타로 읽는다. 브라우저에 처음 띄웠을 때 드러났다.
 *
 * **POC 가 여기 있는 이유**는 그것이 매물대의 일부이기 때문이다. 주기 이름 밑에 두었더니
 * ATR 과 나란히 놓여 "이 주기의 설정값" 처럼 읽혔다.
 */
function VolumeCell({ volume }: { volume: Volume }) {
  return (
    <td className="py-2.5 text-right tabular-nums">
      <VolumeBody volume={volume} />
      <span className="mt-1 block text-[10px] text-ink-4">
        POC <Decimal text={price(volume.pointOfControl)} />
      </span>
    </td>
  );
}

function VolumeBody({ volume }: { volume: Volume }) {
  if (volume.here) {
    return (
      <>
        <span className="font-medium text-warn">지금 이 안</span>
        <span className="mt-0.5 block text-[10px] text-warn">
          {price(volume.here.near)} ~ {price(volume.here.far)}
        </span>
        <span className="block text-[10px] text-ink-3">
          두께 <Decimal text={percent(volume.here.sharePercent)} keep={2} />
        </span>
      </>
    );
  }
  if (!volume.below && !volume.above) {
    return (
      <>
        <span className="text-ink-4">—</span>
        <span className="mt-0.5 block text-[10px] text-ink-4">고르게 퍼짐</span>
      </>
    );
  }
  return (
    <>
      <ShelfLine shelf={volume.above} 위 />
      <ShelfLine shelf={volume.below} />
    </>
  );
}

/** 한 줄. 부호는 화면이 붙인다 — 서버는 거리를 언제나 0 이상으로 낸다(`ZoneCell` 과 같다). */
function ShelfLine({ shelf, 위 = false }: { shelf: Shelf | null; 위?: boolean }) {
  if (!shelf) {
    return <span className="block text-[11px] text-ink-4">{위 ? "↑" : "↓"} 없다</span>;
  }
  return (
    <span className="block text-[11px] text-ink-3">
      {위 ? "↑" : "↓"}{" "}
      <span className={거리톤(shelf.distancePercent)}>
        <Decimal text={price(shelf.near)} />
      </span>{" "}
      {위 ? "+" : "−"}
      <Decimal text={percent(shelf.distancePercent)} keep={2} />
      <span className="ml-1 text-ink-4">
        두께 <Decimal text={percent(shelf.sharePercent)} keep={2} />
      </span>
    </span>
  );
}

/**
 * 골든 포켓 한 칸.
 *
 * **여섯 레벨을 다 적지 않는다.** 표에 여섯 줄을 더하면 이 화면이 답해야 하는 질문("세 주기가
 * 같은 말을 하는가")이 숫자 열여덟 개에 묻힌다. 여기 적는 것은 되돌림에서 사람들이 실제로
 * 보는 한 곳 — 0.618~0.65 띠 — 과 지금 가격이 그 안인가까지다.
 *
 * **안에 있다는 것만 말하고 그 다음은 말하지 않는다.** 그 자리에서 되돌아온다는 것은 이
 * 도구가 근거를 갖지 못한 주장이다.
 */
function PocketCell({ fibonacci }: { fibonacci: Fibonacci | null }) {
  if (!fibonacci) {
    return (
      <td className="py-2.5 text-right text-ink-4">
        —<span className="mt-0.5 block text-[10px]">스윙 없음</span>
      </td>
    );
  }
  /*
    **낮은 값부터 적는다.** 서버는 비율 순서(0.618 → 0.65)로 주는데, 오른 스윙의 되돌림은
    고점에서 **아래로** 재므로 0.65 가 더 낮은 가격이다. 그대로 이어 붙였더니 오른 스윙에서는
    `79,069.30 ~ 79,049.03` 로 큰 값이 앞에 오고 내린 스윙에서는 반대로 나왔다 — 같은 표의
    세 줄이 서로 다른 규칙으로 적힌 셈이다. 매물대에서 고친 것과 같은 종류다.

    **정렬은 수를 만드는 것이 아니다.** 서버가 준 두 값을 그대로 쓰고 순서만 세운다.
  */
  const 포켓 = fibonacci.levels
    .filter((level) => POCKET.includes(level.ratio))
    .map((level) => level.price)
    .sort((a, b) => a - b);
  const 안 = fibonacci.inGoldenPocket;

  return (
    <td className="py-2.5 text-right tabular-nums">
      <span className={안 ? "font-medium text-warn" : "text-ink-3"}>
        {포켓.map((값) => price(값)).join(" ~ ")}
      </span>
      <span className={`mt-0.5 block text-[10px] ${안 ? "text-warn" : "text-ink-4"}`}>
        {안 ? "지금 이 안" : fibonacci.upward ? "오른 스윙" : "내린 스윙"}
      </span>
    </td>
  );
}

/** 골든 포켓의 두 끝. 서버가 내는 비율 그대로이며 화면이 계산하지 않는다. */
const POCKET = [0.618, 0.65];

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
