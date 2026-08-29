import type { ReactNode } from "react";

/**
 * 한 탭 안의 구획.
 *
 * 여섯 탭을 셋으로 합치면서 생겼다. 합치기만 하면 <b>무엇이 무엇인지 경계가 사라지므로</b>
 * 제목과 한 줄 설명을 함께 둔다 — 탭 이름이 하던 일을 이 제목이 대신 한다.
 *
 * `tone="warn"` 은 <b>그 구획의 수를 신뢰하는 방식에 조건이 붙을 때</b>만 쓴다. 지금은
 * 백테스트 하나뿐이고, 남발하면 색이 뜻을 잃는다.
 */
export function Section({
  title,
  hint,
  tone = "plain",
  children,
}: {
  title: string;
  hint: string;
  tone?: "plain" | "warn";
  children: ReactNode;
}) {
  return (
    <section aria-label={title}>
      <h2 className="border-l-2 border-accent pl-2 text-base font-medium text-ink">{title}</h2>
      <p
        className={`mt-1 mb-3 pl-2.5 text-xs leading-snug ${
          tone === "warn" ? "text-warn" : "text-ink-3"
        }`}
      >
        {hint}
      </p>
      {children}
    </section>
  );
}
