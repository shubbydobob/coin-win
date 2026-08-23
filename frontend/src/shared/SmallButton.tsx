/**
 * 화면의 흐름을 끊지 않는 작은 버튼.
 *
 * **네 번째 사본이 생기려던 자리에서 뽑았다.** 같은 클래스 문자열이 `ActiveTrades` 의 다음
 * 동작, 시장 지표의 다시 시도, 그리고 거래소 대조의 새로고침에 각각 있었다 —
 * `conventions.md` 의 "같은 로직이 두 번째 나타나면 즉시 추출한다" 가 겨냥한 것이 이것이다.
 * 값이 갈라지는 종류의 중복은 아니지만, 흩어져 있으면 버튼 하나만 다르게 생긴 화면이 된다.
 *
 * **이 버튼은 되돌릴 수 없는 일을 하지 않는다.** 청산·삭제 같은 것을 여기에 태우면 눈에 띄지
 * 않는 생김새가 그대로 위험이 된다.
 */
export function SmallButton({
  children,
  onClick,
}: {
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded border border-slate-300 px-2 py-0.5 text-xs"
    >
      {children}
    </button>
  );
}
