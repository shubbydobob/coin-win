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
 * **창은 ATR 로 정한다.** 지금 가격의 위아래 2 ATR 이 창이다. 지지·저항으로 창을 잡으면
 * 한쪽이 없을 때(자주 그렇다) 창 자체가 정해지지 않고, 고정 % 로 잡으면 변동성이 큰 날과
 * 작은 날이 같은 폭으로 그려져 **같은 거리가 다른 뜻인 것이 지워진다.** ATR 은 서버가 낸
 * 값이고 그 주기의 하루치 흔들림이다.
 *
 * **창 밖은 지우지 않고 가장자리에 붙인다.** 4시간 지지가 −16% 인 것은 사실이고, 지우면
 * "아래에 아무것도 없다" 가 된다. 화살표로 그 방향에 있다는 것만 말한다.
 *
 * **읽는 수는 전부 `aria-label` 에도 있다.** 띠는 눈으로 보는 것이고, 값이 필요한 사람과
 * 스크린 리더는 같은 문장을 읽는다.
 */
export function TimeframeTrack({ readout, label }: { readout: Readout; label: string }) {
  const 창 = 창범위(readout);

  return (
    <div className="mt-1.5">
      <div
        className="relative h-9 rounded bg-surface-2"
        role="img"
        aria-label={설명(readout, label)}
      >
        <Band 창={창} 아래={readout.cloudBottom} 위={readout.cloudTop} className="bg-ink-4/15" />
        <VolumeBand 창={창} volume={readout.volume} />
        <PocketBand 창={창} fibonacci={readout.fibonacci} />

        {/* 지금. 언제나 한가운데다 — 창이 지금을 중심으로 잡히기 때문이다. */}
        <div className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-ink" />
        <span className="absolute left-1/2 top-0.5 -translate-x-1/2 rounded bg-surface px-1 text-[10px] font-medium tabular-nums text-ink">
          <Decimal text={price(readout.close)} />
        </span>

        <Level 창={창} 값={readout.support?.near} label="지지" />
        <Level 창={창} 값={readout.resistance?.near} label="저항" />
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
 * 나눗셈이 무너지므로 최소 폭을 준다. 그 폭이 무엇이든 띠에는 아무것도 안 보이게 된다.
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

function 창밖(창: 창범위, 값: number): boolean {
  return 값 < 창.아래 || 값 > 창.위;
}

function Band({
  창,
  아래,
  위,
  className,
}: {
  창: 창범위;
  아래: number;
  위: number;
  className: string;
}) {
  const 왼쪽 = 위치(창, 아래);
  const 오른쪽 = 위치(창, 위);
  if (오른쪽 <= 왼쪽) {
    return null;
  }
  return (
    <div
      className={`absolute inset-y-0 ${className}`}
      style={{ left: `${왼쪽}%`, width: `${오른쪽 - 왼쪽}%` }}
      aria-hidden="true"
    />
  );
}

/**
 * 매물대.
 *
 * **위·아래·품은 것이 같은 색이다.** 셋은 위치가 다를 뿐 같은 것이고, 어느 쪽인지는 띠에서
 * 눈으로 보인다 — 색을 나누면 "지금 이 안" 이라는 사실을 다시 글로 적어야 한다.
 */
function VolumeBand({ 창, volume }: { 창: 창범위; volume: Readout["volume"] }) {
  const 대들 = [volume.here, volume.below, volume.above].filter((대) => 대 !== null);
  return (
    <>
      {대들.map((대) => (
        <Band
          key={`${대.near}-${대.far}`}
          창={창}
          아래={Math.min(대.near, 대.far)}
          위={Math.max(대.near, 대.far)}
          className="bg-warn/20"
        />
      ))}
    </>
  );
}

/** 골든 포켓. 서버가 준 두 값을 쓰고 순서만 세운다. */
function PocketBand({ 창, fibonacci }: { 창: 창범위; fibonacci: Readout["fibonacci"] }) {
  const 포켓 = 포켓구간(fibonacci);
  if (!포켓) {
    return null;
  }
  return (
    <Band
      창={창}
      아래={포켓.아래}
      위={포켓.위}
      className="border-y border-dashed border-ink-3/60"
    />
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
  const 밖 = 창밖(창, 값);
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
        style={{ left: `${Math.min(88, Math.max(12, 왼쪽))}%` }}
      >
        {밖 && "‹ "}
        {label} <Decimal text={price(값)} />
      </span>
    </>
  );
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
  조각.push(포켓문장(readout.fibonacci));
  return 조각.join(" · ");
}

/**
 * 골든 포켓.
 *
 * **띠에는 점선으로만 그리고 값은 여기에 둔다.** 화면에서 숫자를 덜어낸 것이지 사실을 덜어낸
 * 것이 아니다 — 값이 아예 없으면 손절을 그 경계에 두려는 사람이 화면에서 읽을 수 없다.
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
function 포켓구간(
  fibonacci: Readout["fibonacci"],
): { 아래: number; 위: number } | null {
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
