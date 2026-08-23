import { TEXT_TONE, type Tone } from "./tone";

/**
 * 표본 시계열을 작은 선으로.
 *
 * **위치와 변화율만으로는 "서서히인가 급격한가" 가 사라진다.** 같은 −3% 라도 계단처럼
 * 한 번에 떨어진 것과 천천히 흘러내린 것은 다른 일이고, 그 구분은 모양에만 있다.
 *
 * **숫자를 만들지 않는다.** 좌표는 서버가 준 표본에서 나오고 사람이 읽는 수는 언제나 옆에
 * 있다 — 이 선만 보고 값을 읽어 내지 않는다(`docs/adr/020`).
 *
 * **색은 오르내림이 아니라 뜻을 담는다.** 처음에는 오르면 초록·내리면 빨강이었는데 그것은
 * 아무것도 말하지 않았다 — 선의 모양이 이미 오른 것을 보여 주기 때문이다. 게다가 그 두 색은
 * 매매에서 "좋다/나쁘다" 로 읽히는데 **미결제약정이 오르는 것이 좋은 일인지는 아무도 모른다.**
 * 지금은 `tone` 이 진영을 담는다(`shared/tone`).
 */
export function Sparkline({
  samples,
  tone = "none",
  label,
}: {
  samples: readonly number[];
  tone?: Tone;
  label: string;
}) {
  if (samples.length < 2) {
    return null;
  }

  const 최소 = Math.min(...samples);
  const 최대 = Math.max(...samples);
  const 폭 = 최대 - 최소;
  const 점 = samples.map((value, index) => {
    const x = (index / (samples.length - 1)) * 100;
    // 평평한 계열은 가운데 선으로 그린다. 0 으로 나누면 좌표가 사라진다.
    const y = 폭 === 0 ? 50 : 100 - ((value - 최소) / 폭) * 100;
    return `${x},${y}`;
  });

  return (
    <svg
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      className={`h-6 w-full ${TEXT_TONE[tone]}`}
      role="img"
      aria-label={label}
    >
      <polyline
        points={점.join(" ")}
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
        vectorEffect="non-scaling-stroke"
        strokeLinejoin="round"
      />
    </svg>
  );
}
