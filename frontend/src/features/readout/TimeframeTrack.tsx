import { percent, price } from "../../format";
import { Decimal } from "../../shared/Decimal";
import type { components } from "../../api/schema";

type Readout = components["schemas"]["TimeframeReadoutResponse"];

/**
 * 한 주기의 자리들을 **가격 띠 하나에 그린다.**
 *
 * **숫자를 그림으로 바꾸는 자리이지 계산하는 자리가 아니다.** 좌표는 서버가 낸 가격에서
 * 나오고, 사람이 읽는 수는 언제나 띠 위에 놓인 `format/` 의 출력이다 — `shared/Meter` 가
 * 같은 이유로 같은 말을 적어 두었다. 그래서 여기 있는 산술은 `docs/adr/020` 이 금지한
 * 계산에 해당하지 않는다.
 *
 * **그리는 것은 셋뿐이다** — 매물대(면) · 지지·저항(선) · 지금(선). 처음에는 구름과 골든
 * 포켓도 그렸는데, 구름은 창 밖이면 아예 안 보이면서 「구름 위」 라는 글자가 이미 같은 말을
 * 하고 있었고, 골든 포켓은 폭이 창의 1~2% 라 **점선 부스러기처럼 보였다.** 그리는 것이
 * 늘수록 무엇을 보는 화면인지가 흐려진다. 둘 다 값은 `aria-label` 에 그대로 있다.
 *
 * **창은 ATR 로 정한다.** 지금 가격의 위아래 2 ATR 이 창이다. 지지·저항으로 창을 잡으면
 * 한쪽이 없을 때(자주 그렇다) 창 자체가 정해지지 않고, 고정 % 로 잡으면 변동성이 큰 날과
 * 작은 날이 같은 폭으로 그려져 **같은 거리가 다른 뜻인 것이 지워진다.**
 *
 * **창 밖은 지우지 않고 가장자리에 붙인다.** 4시간 지지가 −16% 인 것은 사실이고, 지우면
 * "아래에 아무것도 없다" 가 된다. 화살표가 **어느 쪽인지**를 말한다 — 처음에는 위아래 모두
 * `‹` 를 붙여, 창 위에 있는 저항을 아래를 가리키며 표시하고 있었다.
 *
 * **띠 아래 한 줄이 그 띠를 읽어 준다.** 그림만으로는 "가까운가" 가 눈대중이 되고, 눈대중은
 * 주기마다 창 폭이 달라 서로 견줄 수 없다. 거리는 서버가 낸 값이다.
 */
export function TimeframeTrack({ readout, label }: { readout: Readout; label: string }) {
  const 창 = 창범위(readout);

  return (
    <div className="mt-1.5">
      <div
        className="relative h-7 rounded bg-surface-2"
        role="img"
        aria-label={설명(readout, label)}
      >
        <VolumeBand 창={창} volume={readout.volume} />

        {/* 지금. 언제나 한가운데다 — 창이 지금을 중심으로 잡히기 때문이다. */}
        <div className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-ink" />
        <span className="absolute left-1/2 top-1 -translate-x-1/2 rounded bg-surface px-1 text-[10px] font-medium tabular-nums text-ink">
          <Decimal text={price(readout.close)} />
        </span>

        <Level 창={창} 값={readout.support?.near} label="지지" />
        <Level 창={창} 값={readout.resistance?.near} label="저항" />
      </div>

      {/*
        **글의 배치가 띠의 배치와 같다.** 아래 것은 왼쪽에, 위 것은 오른쪽에 둔다 — 눈이
        띠에서 글로 내려올 때 좌우가 뒤집히면 같은 사실을 두 번 해석하게 된다.
      */}
      <div className="mt-1 flex flex-wrap justify-between gap-x-3 text-[11px] tabular-nums text-ink-3">
        <span>아래 {아래읽기(readout)}</span>
        {readout.volume.here && (
          <span className="text-warn">
            지금 매물대 안 (두께 {percent(readout.volume.here.sharePercent)})
          </span>
        )}
        <span>위 {위읽기(readout)}</span>
      </div>
    </div>
  );
}

/** 창의 두 끝. 밖에 있는 것은 가장자리에 붙는다. */
type 창범위 = { 아래: number; 위: number };

/**
 * 지금 가격의 위아래 2 ATR.
 *
 * ATR 이 0 인 경우는 캔들이 전혀 움직이지 않았다는 뜻이라 실전에서 오지 않지만, 0 이면
 * 나눗셈이 무너지므로 최소 폭을 준다.
 */
function 창범위(readout: Readout): 창범위 {
  const 폭 = readout.atr > 0 ? readout.atr * 2 : 1;
  return { 아래: readout.close - 폭, 위: readout.close + 폭 };
}

/** 가격을 띠 위 백분율로. 창 밖은 가장자리에 붙는다. */
function 위치(창: 창범위, 값: number): number {
  const 비율 = ((값 - 창.아래) / (창.위 - 창.아래)) * 100;
  return Math.min(100, Math.max(0, 비율));
}

/**
 * 매물대.
 *
 * **위·아래·품은 것이 같은 색이다.** 셋은 위치가 다를 뿐 같은 것이고, 어느 쪽인지는 띠에서
 * 눈으로 보인다 — 색을 나누면 "지금 이 안" 이라는 사실을 다시 글로 적어야 한다.
 *
 * **아주 얇아도 보이게 둔다.** 창의 1% 밖에 안 되는 대도 있는데 그때 0픽셀로 사라지면
 * "매물대가 없다" 로 읽힌다.
 */
function VolumeBand({ 창, volume }: { 창: 창범위; volume: Readout["volume"] }) {
  const 대들 = [volume.here, volume.below, volume.above].filter((대) => 대 !== null);
  return (
    <>
      {대들.map((대) => {
        const 왼쪽 = 위치(창, Math.min(대.near, 대.far));
        const 오른쪽 = 위치(창, Math.max(대.near, 대.far));
        return (
          <div
            key={`${대.near}-${대.far}`}
            className="absolute inset-y-0 min-w-[2px] rounded-sm bg-warn/20"
            style={{ left: `${왼쪽}%`, width: `${오른쪽 - 왼쪽}%` }}
            aria-hidden="true"
          />
        );
      })}
    </>
  );
}

/**
 * 지지 또는 저항 한 줄.
 *
 * **없으면 아무것도 그리지 않는다.** 그 방향에 사람이 반응한 적 있는 자리가 아직 없다는
 * 뜻이고, 0 이나 창 끝에 붙이면 없는 선이 생긴다.
 */
function Level({ 창, 값, label }: { 창: 창범위; 값?: number; label: string }) {
  if (값 === undefined) {
    return null;
  }
  const 왼쪽 = 위치(창, 값);
  const 아래로밖 = 값 < 창.아래;
  const 위로밖 = 값 > 창.위;
  const 밖 = 아래로밖 || 위로밖;
  return (
    <>
      <div
        className="absolute inset-y-0 w-px bg-ink-3"
        style={{ left: `${왼쪽}%` }}
        aria-hidden="true"
      />
      <span
        className={`absolute bottom-0.5 -translate-x-1/2 whitespace-nowrap px-1 text-[10px] tabular-nums ${
          밖 ? "text-ink-4" : "text-ink-2"
        }`}
        style={{ left: `${Math.min(86, Math.max(14, 왼쪽))}%` }}
      >
        {아래로밖 && "‹ "}
        {label} <Decimal text={price(값)} />
        {위로밖 && " ›"}
      </span>
    </>
  );
}

/**
 * 띠를 거리로 읽어 준다. **아래 쪽과 위 쪽을 따로 낸다.**
 *
 * 처음에는 한 줄에 「지지 · 매물대 · 저항」을 이어 붙였고, 매물대는 아래·위 중 <b>하나만</b>
 * 골라 적었다. 그래서 띠에는 매물대가 오른쪽(위)에 칠해져 있는데 글은 "매물대 0.99% 아래"
 * 라고 말하는 화면이 나왔다 — <b>둘 다 사실인데 글이 눈에 안 보이는 쪽을 골랐다.</b>
 * 방향별로 나누면 그 어긋남이 생길 자리가 없다.
 *
 * **거리로 말한다.** 띠 위의 가격은 손절을 어디 둘지에 쓰는 값이고, "가까운가" 는 거리에서만
 * 나온다 — 주기마다 창 폭이 달라 그림의 길이는 서로 견줄 수 없다.
 *
 * **여기서도 방향은 말하지 않는다.** "지지 0.2% 아래" 는 사실이고 "그러니 반등" 은 예측이다.
 */
function 아래읽기(readout: Readout): string {
  const 조각 = [
    readout.support && `지지 ${percent(readout.support.distancePercent)}`,
    readout.volume.below && `매물대 ${percent(readout.volume.below.distancePercent)}`,
  ].filter(Boolean);
  return 조각.length === 0 ? "없음" : 조각.join(" · ");
}

function 위읽기(readout: Readout): string {
  const 조각 = [
    readout.resistance && `저항 ${percent(readout.resistance.distancePercent)}`,
    readout.volume.above && `매물대 ${percent(readout.volume.above.distancePercent)}`,
  ].filter(Boolean);
  return 조각.length === 0 ? "없음" : 조각.join(" · ");
}

/**
 * 띠가 말하는 것을 문장 하나로.
 *
 * **여기에 모든 수가 있다.** 화면에서 숫자를 덜어낸 대신 값이 사라지면 안 되므로, 읽어야 할
 * 사람과 스크린 리더가 같은 문장을 본다. 테스트도 이 문장을 본다 — 그림의 좌표가 아니라
 * 사실을 검사하기 위해서다.
 */
function 설명(readout: Readout, label: string): string {
  const 조각 = [label, `지금 ${price(readout.close)}`, `ATR ${price(readout.atr)}`];
  if (readout.support) {
    조각.push(`지지 ${price(readout.support.near)} ${percent(readout.support.distancePercent)} 아래`);
  }
  if (readout.resistance) {
    조각.push(`저항 ${price(readout.resistance.near)} ${percent(readout.resistance.distancePercent)} 위`);
  }
  조각.push(매물대문장(readout.volume));
  조각.push(`POC ${price(readout.volume.pointOfControl)}`);
  조각.push(`구름 ${price(readout.cloudBottom)}~${price(readout.cloudTop)}`);
  조각.push(포켓문장(readout.fibonacci));
  return 조각.join(" · ");
}

/**
 * 골든 포켓.
 *
 * **띠에는 그리지 않고 값만 여기 둔다.** 폭이 창의 1~2% 라 그리면 점선 부스러기가 되고,
 * 그럼에도 값이 아예 없으면 손절을 그 경계에 두려는 사람이 화면에서 읽을 수 없다.
 */
function 포켓문장(fibonacci: Readout["fibonacci"]): string {
  const 포켓 = 포켓구간(fibonacci);
  if (!fibonacci || !포켓) {
    return "골든 포켓 없음";
  }
  const 안 = fibonacci.inGoldenPocket ? " 지금 이 안" : "";
  return `골든 포켓 ${price(포켓.아래)}~${price(포켓.위)}${안}`;
}

function 매물대문장(volume: Readout["volume"]): string {
  if (volume.here) {
    return `매물대 ${price(volume.here.near)}~${price(volume.here.far)} 두께 ${percent(volume.here.sharePercent)} 지금 이 안`;
  }
  const 조각 = [
    volume.below && `아래 ${price(volume.below.near)} 두께 ${percent(volume.below.sharePercent)}`,
    volume.above && `위 ${price(volume.above.near)} 두께 ${percent(volume.above.sharePercent)}`,
  ].filter(Boolean);
  return 조각.length === 0 ? "매물대 없음" : `매물대 ${조각.join(", ")}`;
}

/**
 * 골든 포켓의 두 끝. **낮은 값부터 세운다.**
 *
 * 서버는 비율 순서(0.618 → 0.65)로 주는데, 오른 스윙의 되돌림은 고점에서 **아래로** 재므로
 * 0.65 가 더 낮은 가격이다. 순서를 세우지 않으면 오른 스윙과 내린 스윙이 서로 다른 규칙으로
 * 적힌다. **정렬은 수를 만드는 것이 아니다** — 서버가 준 두 값을 그대로 쓰고 순서만 세운다.
 */
function 포켓구간(fibonacci: Readout["fibonacci"]): { 아래: number; 위: number } | null {
  if (!fibonacci) {
    return null;
  }
  const [아래, 위] = fibonacci.levels
    .filter((level) => POCKET.includes(level.ratio))
    .map((level) => level.price)
    .sort((a, b) => a - b);
  return 아래 === undefined || 위 === undefined ? null : { 아래, 위 };
}

/** 골든 포켓의 두 비율. 서버가 내는 값 그대로이며 화면이 계산하지 않는다. */
const POCKET = [0.618, 0.65];
