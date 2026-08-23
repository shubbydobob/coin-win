/**
 * 값 하나를 눈금 위에 찍는다.
 *
 * **숫자를 그림으로 바꾸는 자리이지 계산하는 자리가 아니다.** 좌표는 서버가 낸 값에서 나오고,
 * 사람이 읽는 수는 언제나 옆에 있는 `format/` 의 출력이다 — 이 막대만 보고 값을 읽어 내지
 * 않는다. 그래서 여기 있는 산술은 `docs/adr/020` 이 금지한 계산에 해당하지 않는다.
 * 접근성 도구에도 그 뜻을 그대로 전한다(`role="img"` + `aria-label`).
 */

/**
 * 0~1 사이의 위치를 눈금 위에 점으로 찍는다. 이상치면 색이 바뀐다.
 *
 * 눈금 양 끝의 5% 를 옅게 칠해 **경계가 어디인지를 그림으로** 보인다 — "상위 5% 안" 이라는
 * 문장을 읽고 머릿속에서 5% 를 그리지 않아도 되게 하는 것이 이 띠의 전부다.
 */
export function PositionMeter({
  ratio,
  outlier,
  label,
}: {
  ratio: number;
  outlier: boolean;
  label: string;
}) {
  const 위치 = Math.min(100, Math.max(0, ratio * 100));

  return (
    <div className="relative h-1.5 w-full rounded-full bg-surface-2" role="img" aria-label={label}>
      <div className="absolute inset-y-0 left-0 w-[5%] rounded-l-full bg-warn/25" />
      <div className="absolute inset-y-0 right-0 w-[5%] rounded-r-full bg-warn/25" />
      <div
        className={`absolute top-1/2 size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-surface ${
          outlier ? "bg-warn" : "bg-ink-2"
        }`}
        style={{ left: `${위치}%` }}
      />
    </div>
  );
}

/**
 * 두 양의 비율을 좌우로 나눈 막대. 호가 불균형이 이 모양이다.
 *
 * `0.0910` 이라는 수를 읽고 "매수가 9% 두껍다" 로 옮기는 일을 사람이 하지 않게 한다.
 */
export function SplitMeter({
  left,
  right,
  label,
}: {
  left: number;
  right: number;
  label: string;
}) {
  const 합 = left + right;
  const 왼쪽 = 합 === 0 ? 50 : (left / 합) * 100;

  return (
    <div
      className="flex h-1.5 w-full overflow-hidden rounded-full bg-surface-2"
      role="img"
      aria-label={label}
    >
      <div className="bg-up" style={{ width: `${왼쪽}%` }} />
      <div className="flex-1 bg-down" />
    </div>
  );
}

/**
 * 구간 안에서 지금이 어디인가. 24시간 고저와 현재가가 이 모양이다.
 *
 * 최고·최저를 숫자로만 두면 <b>지금이 그 사이 어디인지</b>가 안 보인다. 하루의 꼭대기에
 * 붙어 있는 것과 바닥에 붙어 있는 것은 같은 숫자 셋으로 표현되지만 전혀 다른 상황이다.
 */
export function RangeMeter({
  low,
  high,
  current,
  label,
}: {
  low: number;
  high: number;
  current: number;
  label: string;
}) {
  const 폭 = high - low;
  const 위치 = 폭 === 0 ? 50 : Math.min(100, Math.max(0, ((current - low) / 폭) * 100));

  return (
    <div className="relative h-1 w-full rounded-full bg-surface-2" role="img" aria-label={label}>
      <div
        className="absolute inset-y-0 left-0 rounded-full bg-ink-4"
        style={{ width: `${위치}%` }}
      />
      <div
        className="absolute top-1/2 h-3 w-0.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-ink"
        style={{ left: `${위치}%` }}
      />
    </div>
  );
}
