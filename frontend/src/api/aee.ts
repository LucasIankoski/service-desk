import { apiFetch } from "./http";
import type { Teacher } from "./pedagogical";
export interface AeeClassroom { id: string; name: string; archived: boolean; canView: boolean; canCreateStudents: boolean }
export interface Student { id: string; aeeClassId: string; classId: string; name: string; archived: boolean; version: number; canManage: boolean }
export interface AeeContext { aeeId: string; aeeName: string; classId: string; className: string; student: Student; archived: boolean; canEdit: boolean; canManage: boolean; teachers: Teacher[] }
export const aeeClasses = (id: string, archived: boolean) => apiFetch<AeeClassroom[]>(`/api/v1/pedagogical/aee/${id}/classes?archived=${archived}`);
export const studentsUrl = (aeeId: string, classId: string) => `/api/v1/pedagogical/aee/${aeeId}/classes/${classId}/students`;
export const listStudents = (aeeId: string, classId: string) => apiFetch<Student[]>(studentsUrl(aeeId, classId));
export const saveStudent = (aeeId: string, classId: string, name: string, archived: boolean, row?: Student) => apiFetch<Student>(studentsUrl(aeeId, classId) + (row ? `/${row.id}` : ""), { method: row ? "PUT" : "POST", body: JSON.stringify({ name, archived, version: row?.version }) });
export interface StudentDocument { id: string; filename: string; size: number; authorId: string; authorName: string; createdAt: string; version: number; canDelete: boolean }
export interface DocumentPage { items: StudentDocument[]; canUpload: boolean; attachmentLimitMb: number }
