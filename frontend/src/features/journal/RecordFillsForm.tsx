import { useState } from "react";

import { decimal, instantAt } from "../../form";
import { BAND_POSITION } from "../../shared/labels";
import { Field } from "../../shared/Field";
import type { components } from "../../api/schema";

type Request = components["schemas"]["RecordFillsRequest"];

type BandPosition = components["schemas"]["MarketContextRequest"]["ichimokuPosition"];

interface FillDraft {
  readonly price: string;
  readonly quantity: string;
  readonly at: string;
}

const EMPTY_FILL: FillDraft = { price: "", quantity: "", at: "" };

/**
 * 체결 기록. 체결 내역과 **진입 시점의 시장 상태**를 함께 받는다 — 맥락은 이 순간에만
 * 존재하고, 나중에 되살릴 수 없다.
 *
 * 근거를 비워 둘 수 없는 것은 서버의 규칙이다. 화면은 그 규칙을 복창하지 않고 422 를 그대로
 * 보여 준다(§ 7).
 */
export function RecordFillsForm({ onSubmit, pending }: { onSubmit: (request: Request) => void; pending: boolean }) {
  const [fills, setFills] = useState<readonly FillDraft[]>([EMPTY_FILL]);
  const [priceAtEntry, setPriceAtEntry] = useState("");
  const [ichimoku, setIchimoku] = useState<BandPosition>("INSIDE");
  const [bollinger, setBollinger] = useState<BandPosition>("INSIDE");
  const [rationale, setRationale] = useState("");

  const replace = (index: number, fill: FillDraft) =>
    setFills(fills.map((each, at) => (at === index ? fill : each)));

  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({
          fills: fills.map((fill) => ({
            price: decimal(fill.price),
            quantity: decimal(fill.quantity),
            at: instantAt(fill.at),
          })),
          context: {
            priceAtEntry: decimal(priceAtEntry),
            ichimokuPosition: ichimoku,
            bollingerPosition: bollinger,
            rationale,
          },
        });
      }}
    >
      <h3 className="text-sm font-medium text-slate-700">체결 기록</h3>

      {fills.map((fill, index) => (
        <div key={index} className="grid grid-cols-3 gap-2">
          <Field label={`${index + 1}차 체결가`} value={fill.price} onChange={(v) => replace(index, { ...fill, price: v })} />
          <Field label={`${index + 1}차 체결수량`} value={fill.quantity} onChange={(v) => replace(index, { ...fill, quantity: v })} />
          <Field label={`${index + 1}차 체결시각 (UTC)`} type="datetime-local" value={fill.at} onChange={(v) => replace(index, { ...fill, at: v })} />
        </div>
      ))}

      <button type="button" onClick={() => setFills([...fills, EMPTY_FILL])} className="rounded border border-slate-300 px-2 py-1 text-sm">
        체결 추가
      </button>

      <div className="grid grid-cols-3 gap-2">
        <Field label="판단 시점 가격" value={priceAtEntry} onChange={setPriceAtEntry} />
        <BandField label="일목 구름 대비" value={ichimoku} onChange={setIchimoku} />
        <BandField label="볼린저 밴드 대비" value={bollinger} onChange={setBollinger} />
      </div>

      <label className="block text-xs text-slate-500">
        진입 근거
        <textarea
          value={rationale}
          onChange={(event) => setRationale(event.target.value)}
          rows={2}
          className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
        />
      </label>

      <button type="submit" disabled={pending} className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50">
        체결 저장
      </button>
    </form>
  );
}

function BandField({ label, value, onChange }: { label: string; value: BandPosition; onChange: (value: BandPosition) => void }) {
  return (
    <label className="block text-xs text-slate-500">
      {label}
      <select
        value={value}
        onChange={(event) => onChange(event.target.value as BandPosition)}
        className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm"
      >
        {Object.entries(BAND_POSITION).map(([option, text]) => (
          <option key={option} value={option}>{text}</option>
        ))}
      </select>
    </label>
  );
}
