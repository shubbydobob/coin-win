/**
 * 신호등 — **평소인가, 치우쳤나, 평소와 다른가.** 세 단계뿐이다.
 *
 * 화면이 그림을 여럿 쓰다가 읽히지 않게 된 자리를 이것 하나로 바꿨다. 그림은 "얼마나
 * 그런가" 를 보여 주는 데는 좋지만, 이 화면들이 실제로 묻는 것은 **"지금 이게 신경 쓸 일인가"**
 * 하나였고 거기에는 세 단계면 충분하다.
 *
 * <b>색은 등락과 아무 관계가 없다.</b> 초록·빨강은 이 저장소에서 이미 롱 쪽 / 숏 쪽이고
 * (`shared/tone.ts`), 신호등에까지 쓰면 같은 초록이 한 화면에서 두 가지를 가리킨다. 그래서
 * 회색 → 노랑 → 주황이다.
 *
 * <b>색만으로 뜻을 지지 않는다.</b> 세 색은 어두운 배경에서 서로 가까워 보이고 이 화면들은
 * 빠르게 훑는 자리다. 언제나 옆에 글자가 붙고, 접근성 이름도 글자다.
 */
export type Level = "usual" | "leaning" | "unusual";

export const LEVEL_TEXT: Record<Level, string> = {
  usual: "평소",
  leaning: "치우침",
  unusual: "평소와 다름",
};

const LEVEL_DOT: Record<Level, string> = {
  usual: "bg-ink-4",
  leaning: "bg-warn",
  unusual: "bg-alert",
};

export function Light({ level, label }: { level: Level; label?: string }) {
  return (
    <span
      className={`inline-block size-2.5 shrink-0 rounded-full ${LEVEL_DOT[level]}`}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  );
}

/**
 * 범례 한 줄. **펼친 채로 둔다** — 접힌 설명은 뜻을 모르는 사람에게 없는 것과 같다.
 * 세 단계뿐이라 한 줄에 들어가므로 접을 이유가 없다.
 */
export function LightLegend({ note }: { note: string }) {
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink-4">
      {(["usual", "leaning", "unusual"] as const).map((level) => (
        <span key={level} className="flex items-center gap-1">
          <Light level={level} />
          {LEVEL_TEXT[level]}
        </span>
      ))}
      <span>— {note}</span>
    </div>
  );
}
