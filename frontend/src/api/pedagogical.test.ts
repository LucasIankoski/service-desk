import { describe, expect, it } from "vitest";
import { planPeriods } from "./pedagogical";
describe("diretórios do planejamento", () => {
  it("exibe a mesma semana nos dois meses e anos abrangidos", () => {
    expect(planPeriods({ weekStart: "2026-09-28", weekEnd: "2026-10-02" })).toEqual(["2026-09", "2026-10"]);
    expect(planPeriods({ weekStart: "2026-12-28", weekEnd: "2027-01-01" })).toEqual(["2026-12", "2027-01"]);
  });
  it("não duplica semanas dentro do mesmo mês", () => {
    expect(planPeriods({ weekStart: "2026-09-21", weekEnd: "2026-09-25" })).toEqual(["2026-09"]);
  });
});
