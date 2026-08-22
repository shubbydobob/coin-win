import { decimal } from "../../form";
import { SPLIT_IN_HALF, toEntries } from "../../shared/entries";
import type { EntryRow } from "../../shared/entries";
import type { components } from "../../api/schema";

type Request = components["schemas"]["AnalyzePositionRequest"];

export interface PlanForm {
  readonly direction: Request["direction"];
  readonly entries: readonly EntryRow[];
  readonly stopLoss: string;
  readonly takeProfit: string;
  readonly leverage: string;
  readonly accountBalance: string;
  readonly riskPercent: string;
}

export const EMPTY_FORM: PlanForm = {
  direction: "LONG",
  entries: SPLIT_IN_HALF,
  stopLoss: "",
  takeProfit: "",
  leverage: "",
  accountBalance: "",
  riskPercent: "",
};

export function toRequest(form: PlanForm): Request {
  return {
    direction: form.direction,
    entries: toEntries(form.entries),
    stopLoss: decimal(form.stopLoss),
    takeProfit: decimal(form.takeProfit),
    leverage: decimal(form.leverage),
    accountBalance: decimal(form.accountBalance),
    riskPercent: decimal(form.riskPercent),
  };
}
