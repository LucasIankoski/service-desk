import { useParams } from "react-router";
import { apiFetch } from "../api/http";
import * as ped from "../api/pedagogical";
import * as projects from "../api/projects";
import * as records from "../api/records";
import type { AeeContext } from "../api/aee";

/** Explicit route scope: never infer a student's permissions or context from a document ID. */
export function usePedagogicalScope() {
  const { aeeId, classId, studentId } = useParams();
  const scoped = !!(aeeId && classId && studentId);
  const api = `/api/v1/pedagogical/aee/${aeeId}/classes/${classId}/students/${studentId}`;
  const studentBase = `/pedagogico/turmas/${aeeId}/turmas/${classId}/alunos/${studentId}`;
  const classBase = (id: string) => scoped ? studentBase : `/pedagogico/turmas/${id}`;
  const planUrl = (id: string) => scoped ? `${studentBase}/planejamentos/${id}` : `/pedagogico/planejamentos/${id}`;
  const projectUrl = (id: string) => scoped ? `${studentBase}/projetos/${id}` : `/pedagogico/projetos/${id}`;
  const recordUrl = (id: string) => scoped ? `${studentBase}/registros/${id}` : `/pedagogico/registros/${id}`;
  const json = (method: string, body: unknown) => ({ method, body: JSON.stringify(body) });
  return {
    key: scoped ? `${aeeId}:${classId}:${studentId}` : "class", scoped, api, studentBase, classBase, planUrl, projectUrl, recordUrl,
    getClass: async (id: string): Promise<ped.SchoolClass> => {
      if (!scoped) return ped.getClass(id);
      const c = await apiFetch<AeeContext>(api);
      return { id: c.classId, name: `AEE: ${c.aeeName} · Turma: ${c.className} · Aluno: ${c.student.name}`, archived: c.archived,
        version: c.student.version, teachers: c.teachers, contentVisibility: "ALLOCATED_ONLY", canCreate: c.canEdit, canEdit: c.canEdit, canManage: c.canManage };
    },
    listPlans: (id: string) => scoped ? apiFetch<ped.PlanSummary[]>(`${api}/plans`) : ped.listPlans(id),
    createPlan: (id: string, weekStart: string) => scoped ? apiFetch<ped.Planning>(`${api}/plans`, json("POST", { weekStart })) : ped.createPlan(id, weekStart),
    getPlan: (id: string) => scoped ? apiFetch<ped.Planning>(`${api}/plans/${id}`) : ped.getPlan(id),
    savePlan: (id: string, input: ped.PlanningInput) => scoped ? apiFetch<ped.Planning>(`${api}/plans/${id}`, json("PUT", input)) : ped.savePlan(id, input),
    deletePlan: (id: string, version: number) => scoped ? apiFetch<void>(`${api}/plans/${id}?version=${version}`, { method: "DELETE" }) : ped.deletePlan(id, version),
    transitionPlan: (id: string, version: number, action: "finalize" | "reopen") => scoped ? apiFetch<ped.Planning>(`${api}/plans/${id}/${action}`, json("POST", { version })) : ped.transitionPlan(id, version, action),
    listProjects: (id: string) => scoped ? apiFetch<projects.ProjectSummary[]>(`${api}/projects`) : projects.listProjects(id),
    createProject: (id: string, input: projects.ProjectCreate) => scoped ? apiFetch<projects.Project>(`${api}/projects`, json("POST", input)) : projects.createProject(id, input),
    getProject: (id: string) => scoped ? apiFetch<projects.Project>(`${api}/projects/${id}`) : projects.getProject(id),
    saveProject: (id: string, input: projects.ProjectInput) => scoped ? apiFetch<projects.Project>(`${api}/projects/${id}`, json("PUT", input)) : projects.saveProject(id, input),
    deleteProject: (id: string, version: number) => scoped ? apiFetch<void>(`${api}/projects/${id}?version=${version}`, { method: "DELETE" }) : projects.deleteProject(id, version),
    transitionProject: (id: string, version: number, action: "finalize" | "reopen") => scoped ? apiFetch<projects.Project>(`${api}/projects/${id}/${action}`, json("POST", { version })) : projects.transitionProject(id, version, action),
    listRecords: (id: string, year: string, month: string, page: number) => scoped ? apiFetch<records.RecordPage>(`${api}/records?${new URLSearchParams({ ...(year ? { year } : {}), ...(year && month ? { month } : {}), page: String(page) })}`) : records.listRecords(id, year, month, page),
    getRecord: (id: string) => scoped ? apiFetch<records.ActivityRecord>(`${api}/records/${id}`) : records.getRecord(id),
    saveRecord: (idClass: string, input: records.RecordInput, files: File[], id?: string) => {
      if (!scoped) return records.saveRecord(idClass, input, files, id);
      const body = new FormData(); body.append("metadata", new Blob([JSON.stringify(input)], { type: "application/json" })); files.forEach(f => body.append("files", f));
      return apiFetch<records.ActivityRecord>(`${api}/records${id ? `/${id}` : ""}`, { method: id ? "PUT" : "POST", body });
    },
    deleteRecord: (id: string, version: number) => scoped ? apiFetch<void>(`${api}/records/${id}?version=${version}`, { method: "DELETE" }) : records.deleteRecord(id, version)
  };
}
