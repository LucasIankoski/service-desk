import { apiFetch } from "./http";

export interface ActivityPhoto { id: string; url: string; thumbnailUrl: string; width: number; height: number }
export interface ActivitySummary {
  id: string; classId: string; title: string; activityDate: string; authorName: string;
  photoCount: number; cover: ActivityPhoto; version: number;
}
export interface ActivityRecord {
  id: string; classId: string; className: string; archived: boolean; title: string; activityDate: string;
  authorId: string; authorName: string; photos: ActivityPhoto[]; version: number; canEdit: boolean;
  createdAt: string; updatedAt: string; attachmentLimitMb: number;
}
export interface RecordPage { items: ActivitySummary[]; page: number; totalPages: number; totalElements: number; attachmentLimitMb: number }
export interface RecordInput { title: string; activityDate: string; retainedPhotoIds: string[]; version?: number }
const base = "/api/v1/pedagogical";
export const listRecords = (classId: string, year: string, month: string, page: number) => {
  const params = new URLSearchParams({ page: String(page) });
  if (year) params.set("year", year);
  if (year && month) params.set("month", month);
  return apiFetch<RecordPage>(`${base}/classes/${classId}/records?${params}`);
};
export const getRecord = (id: string) => apiFetch<ActivityRecord>(`${base}/records/${id}`);
export function saveRecord(classId: string, input: RecordInput, files: File[], id?: string) {
  const body = new FormData();
  body.append("metadata", new Blob([JSON.stringify(input)], { type: "application/json" }));
  files.forEach(file => body.append("files", file));
  return apiFetch<ActivityRecord>(id ? `${base}/records/${id}` : `${base}/classes/${classId}/records`, { method: id ? "PUT" : "POST", body });
}
export const deleteRecord = (id: string, version: number) => apiFetch<void>(`${base}/records/${id}?version=${version}`, { method: "DELETE" });
