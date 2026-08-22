import { decimal } from "../../form";
import type { components } from "../../api/schema";

type Spec = components["schemas"]["ProjectionSpecRequest"];

export interface ProjectionForm {
  readonly initialCapital: string;
  readonly winRate: string;
  readonly riskRewardRatio: string;
  readonly riskPerTrade: string;
  readonly tradesPerWeek: string;
  readonly weeks: string;
  readonly runs: string;
  readonly seed: string;
}

/** 시작 입력값. `scope.md` 의 증거금 전제(800 USDT)와 리스크 2% 를 그대로 놓는다. */
export const STARTING_FORM: ProjectionForm = {
  initialCapital: "800",
  winRate: "45",
  riskRewardRatio: "2",
  riskPerTrade: "2",
  tradesPerWeek: "3",
  weeks: "52",
  runs: "1000",
  seed: "1",
};

export function toSpec(form: ProjectionForm): Spec {
  return {
    initialCapital: decimal(form.initialCapital),
    winRate: decimal(form.winRate),
    riskRewardRatio: decimal(form.riskRewardRatio),
    riskPerTrade: decimal(form.riskPerTrade),
    tradesPerWeek: decimal(form.tradesPerWeek),
    weeks: decimal(form.weeks),
  };
}
