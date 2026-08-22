// 일부러 어기는 코드. 규칙 3 — feature 끼리 직접 부르지 않는다.
//
// 백엔드가 애써 나눈 경계가 화면에서 다시 붙는 자리다. 이 import 가 통과하면
// `features/backtest` 가 `features/journal` 의 사정을 알게 되고, 그 다음은 반대 방향이다.
//
// 경로가 실제로 존재하지 않아도 된다 — `no-restricted-imports` 는 경로 문자열만 본다.
import { TradeRow } from "../journal/TradeRow";

export function 남의_모듈_컴포넌트를_쓴다() {
  return TradeRow;
}
