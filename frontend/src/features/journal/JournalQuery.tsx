import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { post } from "../../api/client";
import { ApiFailure } from "../../api/problem";
import type { components } from "../../api/schema";

type Answer = components["schemas"]["JournalAnswerResponse"];

/**
 * 매매 기록 질의(RAG).
 *
 * **`/ai` 라우트를 만들지 않는다**(§ 6.6). 독립 화면을 만들면 "AI 에게 물어보는 곳" 이 생기고,
 * 그것은 이 프로젝트가 하지 않기로 한 것(판단·추천)을 사용자가 기대하게 만드는 배치다.
 * 그래서 기록 화면 안의 패널이다.
 *
 * **답변에는 근거 거래가 함께 온다.** `citedTradeIds` 를 목록의 해당 거래로 가는 링크로
 * 만든다 — 대조할 수 없는 답은 이 프로젝트에서 근거가 아니다.
 */
export function JournalQuery() {
  const [question, setQuestion] = useState("");
  const ask = useMutation<Answer, Error, string>({
    mutationFn: (asked) => post("/api/ai/journal-query", { question: asked }),
  });

  const problem = ask.error instanceof ApiFailure ? ask.error.problem : null;

  return (
    <section className="space-y-2 rounded border border-slate-200 p-3" aria-label="기록 질의">
      <h3 className="text-xs text-slate-500">지난 매매에 묻기 (AI 보조)</h3>

      <input
        aria-label="질문"
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        className="block w-full rounded border border-slate-300 px-2 py-1 text-sm"
      />

      <button
        type="button"
        onClick={() => ask.mutate(question)}
        disabled={ask.isPending}
        className="rounded border border-slate-300 px-2 py-1 text-sm disabled:opacity-50"
      >
        {ask.isPending ? "찾는 중" : "묻기"}
      </button>

      {problem && (
        <p role="status" className="text-sm text-slate-600">
          {problem.detail}
        </p>
      )}

      {ask.data && (
        <div className="space-y-2 text-sm">
          <p>{ask.data.answer}</p>

          {ask.data.citedTradeIds.length > 0 && (
            <p className="text-xs text-slate-500">
              근거:{" "}
              {ask.data.citedTradeIds.map((id) => (
                <a key={id} href={`#trade-${id}`} className="mr-2 underline">
                  {id}
                </a>
              ))}
            </p>
          )}

          <ul className="space-y-1 text-xs text-slate-500">
            {ask.data.retrieved.map((trade) => (
              <li key={trade.tradeId}>{trade.summary}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
