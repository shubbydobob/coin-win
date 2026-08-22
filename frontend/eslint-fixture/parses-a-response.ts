/**
 * 일부러 어기는 코드. 규칙 2 — 응답 값을 수로 되돌리지 않는다.
 *
 * 응답은 이미 수다. 다시 파싱한다는 것은 **계산에 쓰겠다는 뜻**이고, 그 순간 손익비 규칙이
 * 자바와 타입스크립트 양쪽에 존재하게 된다.
 */
interface Analysis {
  readonly requiredMargin: number;
  readonly riskRewardRatio: string;
}

export function 응답을_부동소수로_되돌린다(analysis: Analysis): number {
  return Number(analysis.requiredMargin) * 2;
}

export function 문자열_응답을_계산에_쓴다(analysis: Analysis): number {
  return parseFloat(analysis.riskRewardRatio) + 1;
}
