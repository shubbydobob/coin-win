/**
 * 화면의 색이 뜻하는 것. **네 가지뿐이고 예외를 두지 않는다.**
 *
 * 처음에는 색이 두 가지 일을 겹쳐서 했다 — 막대는 진영(롱 쪽/숏 쪽)을 뜻하는데 스파크라인과
 * 화살표는 오르내림을 뜻했다. 그래서 같은 초록이 한 줄 안에서 두 가지를 가리켰고,
 * **"오르면 초록" 은 아무것도 말하지 않았다** — 선의 모양이 이미 오른 것을 보여 주기 때문이다.
 *
 * 더 나쁜 것은 그 두 색이 매매에서 <b>좋다/나쁘다</b>로 읽힌다는 점이다. 미결제약정이 오르는
 * 것이 좋은 일인지는 아무도 모르고, 이 화면은 그것을 말하지 않기로 한 자리다.
 *
 * 지금은 색 하나가 뜻 하나를 갖는다.
 *
 * - `long` — 롱 쪽이거나 롱 쪽으로 가는 중
 * - `short` — 숏 쪽이거나 숏 쪽으로 가는 중
 * - `outlier` — 평소와 다르다(양 끝 5%). **진영보다 앞선다** — 드문 쪽이 흔한 쪽에 덮이면
 *   경고가 사라진다
 * - `none` — 어느 쪽도 아니다. 미결제약정처럼 축이 없는 값이 여기다
 */
export type Tone = "long" | "short" | "outlier" | "none";

export const TEXT_TONE: Record<Tone, string> = {
  long: "text-up",
  short: "text-down",
  outlier: "text-warn",
  none: "text-ink-4",
};

export const BG_TONE: Record<Tone, string> = {
  long: "bg-up",
  short: "bg-down",
  outlier: "bg-warn",
  none: "bg-ink-4",
};
