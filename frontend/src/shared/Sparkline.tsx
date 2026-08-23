/**
 * 표본 시계열을 작은 선으로.
 *
 * **위치와 변화율만으로는 "서서히인가 급격한가" 가 사라진다.** 같은 −3% 라도 계단처럼
 * 한 번에 떨어진 것과 천천히 흘러내린 것은 다른 일이고, 그 구분은 모양에만 있다.
 *
 * **숫자를 만들지 않는다.** 좌표는 서버가 준 표본에서 나오고 사람이 읽는 수는 언제나 옆에
 * 있다 — 이 선만 보고 값을 읽어 내지 않는다(`docs/adr/020`).
 */
export function Sparkline({
  samples,
  rising,
  label,
}: {
  samples: readonly number[];
  rising: boolean | null;
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

  const 색 = rising === null ? "text-ink-4" : rising ? "text-up" : "text-down";

  return (
    <svg
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      className={`h-6 w-full ${색}`}
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
