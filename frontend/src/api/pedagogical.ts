import { apiFetch } from "./http";

export interface Teacher { id: string; displayName: string }
export interface SchoolClass { id: string; name: string; archived: boolean; teachers: Teacher[]; version: number }
export interface PlanningDay {
  date: string; noClass: boolean; reason: string | null; proposal: string | null;
  objectives: string | null; development: string | null; resources: string | null;
}
export interface PlanSummary {
  id: string; classId: string; weekStart: string; weekEnd: string; theme: string | null;
  status: "DRAFT" | "FINALIZED"; teachers: Teacher[]; version: number;
}
export interface Planning extends PlanSummary {
  className: string; archived: boolean; days: PlanningDay[]; createdAt: string; updatedAt: string;
}
export interface ClassInput { name: string; archived: boolean; teacherIds: string[]; version?: number }
export interface PlanningInput { version: number; theme: string; teacherIds: string[]; days: PlanningDay[] }
const base = "/api/v1/pedagogical";
export const listClasses = () => apiFetch<SchoolClass[]>(base + "/classes");
export const getClass = (id: string) => apiFetch<SchoolClass>(base + "/classes/" + id);
export const listTeachers = () => apiFetch<Teacher[]>(base + "/teachers");
export const saveClass = (input: ClassInput, id?: string) => apiFetch<SchoolClass>(base + "/classes" + (id ? "/" + id : ""), { method: id ? "PUT" : "POST", body: JSON.stringify(input) });
export const listPlans = (id: string) => apiFetch<PlanSummary[]>(base + "/classes/" + id + "/plans");
export const createPlan = (id: string, weekStart: string) => apiFetch<Planning>(base + "/classes/" + id + "/plans", { method: "POST", body: JSON.stringify({ weekStart }) });
export const getPlan = (id: string) => apiFetch<Planning>(base + "/plans/" + id);
export const savePlan = (id: string, input: PlanningInput) => apiFetch<Planning>(base + "/plans/" + id, { method: "PUT", body: JSON.stringify(input) });
export const transitionPlan = (id: string, version: number, action: "finalize" | "reopen") => apiFetch<Planning>(base + "/plans/" + id + "/" + action, { method: "POST", body: JSON.stringify({ version }) });
export const dateLabel = (date: string) => date.split("-").reverse().join("/");
export function planPeriods(plan: Pick<PlanSummary, "weekStart" | "weekEnd">) {
  return [...new Set([plan.weekStart.slice(0, 7), plan.weekEnd.slice(0, 7)])];
}
