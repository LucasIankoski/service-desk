import { describe, expect, it } from "vitest";
import { overlapsPeriod, projectDevelopment, projectYears, plainRichText, type ProjectSummary } from "./projects";

const project: ProjectSummary = { id: "p", classId: "c", title: "Projeto", startDate: "2026-11-20", endDate: "2027-02-10", status: "DRAFT", teachers: [], version: 0 };
describe("períodos de projetos", () => {
  it("inclui meses intermediários e a virada de ano", () => {
    expect(projectYears([project])).toEqual(["2027", "2026"]);
    for (const [year, month] of [["2026", "11"], ["2026", "12"], ["2027", "01"], ["2027", "02"]]) expect(overlapsPeriod(project, year, month)).toBe(true);
    expect(overlapsPeriod(project, "2026", "10")).toBe(false);
    expect(overlapsPeriod(project, "2027", "03")).toBe(false);
  });
  it("apresenta o desenvolvimento histórico sem perder subtítulos ou exigir datas novas", () => {
    const old = projectDevelopment({ startDate: "2026-10-01", endDate: "2026-10-09", activities: [
      { date: "2026-10-01", title: "A rainha mandou", description: plainRichText("Vamos brincar.") }
    ] });
    expect(old.blocks[0]).toEqual({ type: "HEADING", runs: [{ text: "01/10: A RAINHA MANDOU", bold: true }] });
    expect(old.blocks[1].runs[0].text).toBe("Vamos brincar.");
    const free = projectDevelopment({ ...project, activities: [{ date: null, title: "", description: plainRichText("Desenvolvimento livre.") }] });
    expect(free).toEqual(plainRichText("Desenvolvimento livre."));
  });
});
