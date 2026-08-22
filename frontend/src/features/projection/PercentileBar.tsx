import { money } from "../../format";

interface Props {
  readonly worst: number;
  readonly p5: number;
  readonly median: number;
  readonly p95: number;
  readonly best: number;
}

/**
 * 백분위 다섯 점.
 *
 * 응답은 **백분위 다섯 개**이지 히스토그램이 아니다. **없는 분포 모양을 그리지 않는다**(§ 6.5) —
 * 막대 다섯 개를 세우면 그것이 도수분포로 읽히는데, 우리가 아는 것은 다섯 지점의 값뿐이다.
 * 그래서 최악에서 최고까지 이어진 구간 하나 위에 세 점을 찍는다.
 *
 * 점의 위치는 화면 좌표이지 도메인의 수가 아니다 — 차트가 값을 픽셀로 옮기는 것과 같은
 * 일이며, **표시되는 수는 언제나 응답에 있던 값 그대로다**(§ 3).
 */
export function PercentileBar({ worst, p5, median, p95, best }: Props) {
  const span = best - worst;
  const at = (value: number) => (span === 0 ? 50 : ((value - worst) / span) * 100);

  return (
    <figure className="space-y-2">
      <figcaption className="text-sm font-medium text-slate-700">최종 자산 분포</figcaption>

      <div className="relative h-10">
        <div className="absolute inset-x-0 top-4 h-1.5 rounded bg-slate-200" />
        <div
          className="absolute top-4 h-1.5 rounded bg-slate-400"
          style={{ left: `${at(p5)}%`, width: `${at(p95) - at(p5)}%` }}
        />
        {[
          { value: p5, label: "하위 5%" },
          { value: median, label: "중앙값" },
          { value: p95, label: "상위 5%" },
        ].map((mark) => (
          <div
            key={mark.label}
            className="absolute top-2 h-6 w-0.5 bg-slate-900"
            style={{ left: `${at(mark.value)}%` }}
            title={`${mark.label} ${money(mark.value)}`}
          />
        ))}
      </div>

      <dl className="grid grid-cols-5 gap-2 text-center text-xs tabular-nums">
        {[
          { label: "최악", value: worst },
          { label: "하위 5%", value: p5 },
          { label: "중앙값", value: median },
          { label: "상위 5%", value: p95 },
          { label: "최고", value: best },
        ].map((point) => (
          <div key={point.label}>
            <dt className="text-slate-500">{point.label}</dt>
            <dd>{money(point.value)}</dd>
          </div>
        ))}
      </dl>
    </figure>
  );
}
