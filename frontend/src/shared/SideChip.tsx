/**
 * 롱 / 숏 딱지.
 *
 * <b>색 하나에 기대지 않는다.</b> 앞판은 「롱 2」 를 초록 글씨로, 「숏 2」 를 빨간 글씨로만
 * 적었다. 그런데 이 화면에는 초록·빨강 글씨가 이미 많다 — 24시간 변동, 미실현 손익, 다른
 * 자산 열두 개. <b>그 사이에서 색만 다른 글자는 구분으로 읽히지 않는다.</b>
 *
 * 그래서 셋을 겹친다 — <b>모양</b>(테두리 있는 알약), <b>글자</b>(롱 / 숏), <b>색</b>.
 * 셋 중 하나만 눈에 들어와도 어느 쪽인지 알 수 있다.
 *
 * <b>이것은 방향을 말하는 것이지 판단이 아니다.</b> 롱 딱지가 초록인 것은 이 저장소가 상승을
 * 초록으로 쓰기 때문이고(`index.css`), 롱이 좋다는 뜻이 아니다.
 */
export type ChipSide = "LONG" | "SHORT" | "NEUTRAL";

const STYLE: Record<ChipSide, string> = {
  LONG: "bg-up/15 text-up ring-up/40",
  SHORT: "bg-down/15 text-down ring-down/40",
  NEUTRAL: "bg-ink-4/15 text-ink-3 ring-ink-4/40",
};

const WORD: Record<ChipSide, string> = {
  LONG: "롱",
  SHORT: "숏",
  NEUTRAL: "중립",
};

export function SideChip({
  side,
  children,
  word,
}: {
  side: ChipSide;
  /** 딱지 옆에 붙는 값. 없으면 딱지만 나온다. */
  children?: React.ReactNode;
  /** 「롱」 대신 쓸 말. 호가처럼 매수/매도로 부르는 자리가 있다. */
  word?: string;
}) {
  return (
    <span className="inline-flex items-baseline gap-1">
      <span
        className={`rounded px-1.5 py-px text-[11px] font-medium leading-tight ring-1 ${STYLE[side]}`}
      >
        {word ?? WORD[side]}
      </span>
      {children !== undefined && <span className="tabular-nums text-ink">{children}</span>}
    </span>
  );
}
