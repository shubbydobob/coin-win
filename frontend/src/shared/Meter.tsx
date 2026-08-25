import type { Tone } from "./tone";

/**
 * 값 하나를 눈금 위에 찍는다.
 *
 * **숫자를 그림으로 바꾸는 자리이지 계산하는 자리가 아니다.** 좌표는 서버가 낸 값에서 나오고,
 * 사람이 읽는 수는 언제나 옆에 있는 `format/` 의 출력이다 — 이 막대만 보고 값을 읽어 내지
 * 않는다. 그래서 여기 있는 산술은 `docs/adr/020` 이 금지한 계산에 해당하지 않는다.
 * 접근성 도구에도 그 뜻을 그대로 전한다(`role="img"` + `aria-label`).
 *
 * **막대가 하나만 남았다.** 한때 셋이었다(값 위치 · 좌우 분할 · 범위). 셋 다 바로 옆 숫자가
 * 이미 말하는 것을 한 번 더 그리고 있었고, 그렇게 겹친 그림이 열 개가 되자 **어느 것도 읽히지
 * 않았다.** 지금은 그 자리를 신호등(`shared/Light`)과 딱지(`shared/SideChip`)가 대신한다 —
 * 둘 다 "얼마나 그런가" 가 아니라 **"신경 쓸 일인가"** 에 답하는 것들이다.
 *
 * 남은 하나는 24시간 범위다. 저·고가와 지금 값 셋을 한눈에 놓는 자리이고, 그 셋의 관계는
 * 숫자만으로는 머릿속에서 그려야 한다.
 */

/** 진영. 서버가 정하고 화면은 색과 방향만 고른다. */
export type Side = "LONG" | "SHORT" | "BALANCED" | "NONE";

/** 진영을 색의 뜻으로 옮긴다. 색이 뜻하는 것은 `shared/tone` 한 곳에만 적혀 있다. */
export function toneOf(side: Side, outlier = false): Tone {
  if (outlier) {
    return "outlier";
  }
  if (side === "LONG") {
    return "long";
  }
  return side === "SHORT" ? "short" : "none";
}

/**
 * 저점에서 고점까지 중 지금이 어디인가.
 *
 * 세 수(저가·현재가·고가)의 관계를 그린다. 숫자 셋을 나란히 적으면 사람이 머릿속에서 뺄셈을
 * 두 번 해야 하고, 이 자리는 그 뺄셈 말고는 볼 것이 없다.
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
