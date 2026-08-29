/**
 * 자릿수를 **줄이지 않고 덜 튀게만** 한다.
 *
 * **`format/` 이 "스케일을 줄여서 표시하지 않는다" 고 못 박아 두었다.** 그 모듈이 정책의 두
 * 번째 사본이면서도 안전한 이유가 *계산이 없다*는 데 있고, `−0.2008%` 를 `−0.20%` 로 줄이면
 * 그 순간 브라우저가 반올림을 하게 되어 근거가 무너진다.
 *
 * **그런데 판독 표에는 숫자가 마흔 개가 넘고 전부 같은 굵기다.** `−0.2008%` 에서 사람이 실제로
 * 읽는 것은 앞의 두 자리이며, 뒤의 두 자리는 자리를 차지하면서 읽히지 않는다. 그래서 값은
 * 그대로 두고 **뒷자리의 색만 낮춘다** — 지워지지 않으므로 위 규칙에 걸리지 않고, 눈은
 * 앞자리부터 읽는다.
 *
 * 이미 만들어진 문자열만 다룬다. 여기서 수를 만들거나 자르지 않는다.
 */
export function Decimal({ text, keep = 0 }: { text: string; keep?: number }) {
  const parsed = /^([^\d]*[\d,]+)(?:\.(\d+))?(\D*)$/.exec(text);
  if (!parsed) {
    return <>{text}</>;
  }
  const [, whole, fraction, suffix] = parsed;
  if (!fraction) {
    return <>{text}</>;
  }
  const 읽는자리 = fraction.slice(0, keep);
  const 남는자리 = fraction.slice(keep);
  return (
    <>
      {whole}
      {읽는자리 && `.${읽는자리}`}
      {남는자리 && (
        <span className="text-ink-4">
          {읽는자리 ? "" : "."}
          {남는자리}
        </span>
      )}
      {suffix}
    </>
  );
}
