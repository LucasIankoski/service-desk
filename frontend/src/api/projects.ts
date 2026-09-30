import { apiFetch } from "./http";
import type { Teacher } from "./pedagogical";

export interface TextRun { text: string; bold: boolean }
export interface TextBlock { type: "PARAGRAPH" | "BULLET" | "HEADING"; runs: TextRun[] }
export interface RichText { blocks: TextBlock[] }
export interface ProjectActivity { date: string | null; title: string; description: RichText }
export interface ProjectSummary {
  id: string; classId: string; title: string; startDate: string; endDate: string;
  status: "DRAFT" | "FINALIZED"; teachers: Teacher[]; version: number;
}
export interface Project extends ProjectSummary {
  className: string; archived: boolean; ageRange: string; generalObjective: RichText;
  specificObjectives: string[]; activities: ProjectActivity[]; conclusion: RichText; createdAt: string; updatedAt: string;
}
export interface ProjectCreate { title: string; startDate: string; endDate: string }
export interface ProjectInput extends ProjectCreate {
  version: number; ageRange: string; teacherIds: string[]; generalObjective: RichText;
  specificObjectives: string[]; activities: ProjectActivity[]; conclusion: RichText;
}
export const emptyRichText = (): RichText => ({ blocks: [] });
export const plainRichText = (text: string): RichText => ({ blocks: [{ type: "PARAGRAPH", runs: [{ text, bold: false }] }] });
const base = "/api/v1/pedagogical";
export const deleteProject = (id: string, version: number) => apiFetch<void>(`${base}/projects/${id}?version=${version}`, { method: "DELETE" });
export const listProjects = (id: string) => apiFetch<ProjectSummary[]>(`${base}/classes/${id}/projects`);
export const createProject = (id: string, input: ProjectCreate) => apiFetch<Project>(`${base}/classes/${id}/projects`, { method: "POST", body: JSON.stringify(input) });
export const getProject = (id: string) => apiFetch<Project>(`${base}/projects/${id}`);
export const saveProject = (id: string, input: ProjectInput) => apiFetch<Project>(`${base}/projects/${id}`, { method: "PUT", body: JSON.stringify(input) });
export const transitionProject = (id: string, version: number, action: "finalize" | "reopen") => apiFetch<Project>(`${base}/projects/${id}/${action}`, { method: "POST", body: JSON.stringify({ version }) });
export function projectYears(projects: ProjectSummary[]) {
  const years = new Set<number>();
  for (const p of projects) for (let y = Number(p.startDate.slice(0, 4)); y <= Number(p.endDate.slice(0, 4)); y++) years.add(y);
  return [...years].sort((a, b) => b - a).map(String);
}
export function overlapsPeriod(p: ProjectSummary, year: string, month = "") {
  if (!year) return true;
  const start = `${year}-${month || "01"}-01`;
  const end = month ? `${year}-${month}-31` : `${year}-12-31`;
  return p.startDate <= end && p.endDate >= start;
}
export function projectInput(p: Project): ProjectInput {
  return { version: p.version, title: p.title, startDate: p.startDate, endDate: p.endDate,
    ageRange: p.ageRange, teacherIds: p.teachers.map(t => t.id), generalObjective: p.generalObjective,
    specificObjectives: p.specificObjectives, activities: p.activities, conclusion: p.conclusion };
}
export function projectDevelopment(p: Pick<ProjectInput, "activities" | "startDate" | "endDate">): RichText {
  const sameYear = p.startDate.slice(0, 4) === p.endDate.slice(0, 4);
  return { blocks: p.activities.flatMap(a => {
    const heading: TextBlock[] = a.title ? [{ type: "HEADING", runs: [{
      text: (a.date ? a.date.split("-").reverse().join("/").slice(0, sameYear ? 5 : 10) + ": " : "") + a.title.toLocaleUpperCase("pt-BR"), bold: true
    }] }] : [];
    return [...heading, ...a.description.blocks];
  }) };
}
