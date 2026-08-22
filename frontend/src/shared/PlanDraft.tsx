import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../api/client";
import { ApiFailure } from "../api/problem";
import type { components } from "../api/schema";

type Draft = components["schemas"]["PlanDraftResponse"];

/**
 * 문장으로 계획 입력하기.
 *
 * **폼을 채울 뿐 제출하지 않는다.** 응답을 받으면 입력란에 값을 넣고 거기서 멈춘다 — 사용자가
 * 본 다음에 제출한다(§ 6.6). AI 가 읽어낸 값이 사람의 확인 없이 기록이 되면, 이 프로젝트가
 * AI 에게 맡기지 않기로 한 판단을 맡기는 것이 된다.
 *
 * **AI 가 꺼져 있는 것은 고장이 아니다.** 503 이면 이 자리만 그렇게 말하고 나머지 화면은
 * 전부 그대로 동작한다. `OPENAI_API_KEY` 없이 앱이 뜨는 것이 Phase 7 의 완료 조건이었다.
 *
 * `/plan` 과 `/journal` 두 화면이 쓰므로 `shared/` 에 있다.
 */
export function PlanDraft({ onDrafted }: { onDrafted: (draft: Draft) => void }) {
  const [text, setText] = useState("");
  const draft = useMutation<Draft, Error, string>({
    mutationFn: (sentence) => post("/api/ai/plan-draft", { text: sentence }),
    onSuccess: onDrafted,
  });

  const problem = draft.error instanceof ApiFailure ? draft.error.problem : null;

  return (
    <section className="space-y-2 rounded border border-slate-200 p-3" aria-label="문장으로 입력">
      <h3 className="text-xs text-slate-500">문장으로 입력 (AI 보조)</h3>

      <textarea
        aria-label="계획 문장"
        rows={2}
        value={text}
        onChange={(event) => setText(event.target.value)}
        className="block w-full rounded border border-slate-300 px-2 py-1 text-sm"
      />

      <button
        type="button"
        onClick={() => draft.mutate(text)}
        disabled={draft.isPending}
        className="rounded border border-slate-300 px-2 py-1 text-sm disabled:opacity-50"
      >
        {draft.isPending ? "읽는 중" : "칸 채우기"}
      </button>

      {/* 서버가 쓴 문장을 그대로 보여 준다. 422 는 무엇이 빠졌는지를 말해 준다. */}
      {problem && (
        <p role="status" className="text-sm text-slate-600">
          {problem.detail}
        </p>
      )}
      {draft.isSuccess && <p className="text-xs text-slate-500">칸을 채웠다. 확인하고 제출한다</p>}
    </section>
  );
}
