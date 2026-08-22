/**
 * 일부러 어기는 코드. 규칙 1 — 반올림은 `src/format/` 안에서만.
 *
 * 이 파일은 `npm run lint` 에서 제외되고, `test/eslint-rules.test.ts` 가 여기에 ESLint 를
 * 돌려 **정확히 그 규칙으로** 실패하는지 본다. 임시 파괴 대신 픽스처를 상주시키는 이유는
 * Phase 0 과 같다 — 임시 파괴는 확인이 1회성이고 원복 실패 위험만 더한다.
 */
export function 서버가_정한_스케일을_다시_굴린다(maxLoss: number): string {
  return maxLoss.toFixed(1);
}

export function 반올림해서_보여준다(requiredMargin: number): number {
  return Math.round(requiredMargin);
}

export function 형식기를_직접_만든다(price: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(price);
}
