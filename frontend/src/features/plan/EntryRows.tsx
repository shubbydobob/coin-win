import { allocationSum } from "./planForm";
import type { EntryRow } from "./planForm";

interface Props {
  readonly entries: readonly EntryRow[];
  readonly onChange: (entries: readonly EntryRow[]) => void;
}

/**
 * 분할 진입 회차. 행을 더하고 뺀다.
 *
 * 아래에 비중 합계를 보여 주지만 **100 인지 아닌지는 말하지 않는다.** 합계는 사용자가 방금
 * 타이핑한 값의 산술이고, "유효한 계획입니다" 를 붙이는 순간 `EntryLadder` 의 규칙이 두 곳에
 * 생긴다(§ 3).
 */
export function EntryRows({ entries, onChange }: Props) {
  const replace = (index: number, row: EntryRow) =>
    onChange(entries.map((entry, at) => (at === index ? row : entry)));

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-slate-700">진입 회차</legend>

      {entries.map((entry, index) => (
        <div key={index} className="flex items-end gap-2">
          <label className="flex-1 text-xs text-slate-500">
            {index + 1}회차 진입가
            <input
              type="number"
              step="any"
              value={entry.price}
              onChange={(event) => replace(index, { ...entry, price: event.target.value })}
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
          </label>
          <label className="w-24 text-xs text-slate-500">
            {index + 1}회차 비중
            <input
              type="number"
              step="any"
              value={entry.allocation}
              onChange={(event) => replace(index, { ...entry, allocation: event.target.value })}
              className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
          </label>
          <button
            type="button"
            onClick={() => onChange(entries.filter((_, at) => at !== index))}
            disabled={entries.length === 1}
            className="rounded border border-slate-300 px-2 py-1 text-sm disabled:opacity-40"
          >
            {index + 1}회차 삭제
          </button>
        </div>
      ))}

      <div className="flex items-center justify-between text-sm">
        <button
          type="button"
          onClick={() => onChange([...entries, { price: "", allocation: "" }])}
          className="rounded border border-slate-300 px-2 py-1"
        >
          회차 추가
        </button>
        <span className="text-slate-500">비중 합계 {allocationSum(entries)}</span>
      </div>
    </fieldset>
  );
}
