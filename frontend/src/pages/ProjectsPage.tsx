import { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { Button } from "../components/Button";
import { PedagogicalModules } from "../components/PedagogicalModules";
import { getClass, dateLabel } from "../api/pedagogical";
import { createProject, listProjects, overlapsPeriod, projectYears } from "../api/projects";
import styles from "./PedagogicalPage.module.css";

export default function ProjectsPage() {
  const { classId } = useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const year = params.get("ano") ?? "", month = params.get("mes") ?? "";
  const [status, setStatus] = useState("");
  const [teacher, setTeacher] = useState("");
  const [title, setTitle] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const classroom = useQuery({ queryKey: ["pedagogical", "class", classId], queryFn: () => getClass(classId!) });
  const projects = useQuery({ queryKey: ["pedagogical", "projects", classId], queryFn: () => listProjects(classId!) });
  const create = useMutation({ mutationFn: () => createProject(classId!, { title, startDate, endDate }),
    onSuccess: p => { client.invalidateQueries({ queryKey: ["pedagogical", "projects"] }); navigate(`/pedagogico/projetos/${p.id}`); } });
  const all = projects.data ?? [];
  const rows = all.filter(p => overlapsPeriod(p, year, month) && (!status || p.status === status) && (!teacher || p.teachers.some(t => t.id === teacher)));
  const teachers = [...new Map(all.flatMap(p => p.teachers).map(t => [t.id, t])).values()];
  const months = Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, "0")).filter(m => all.some(p => overlapsPeriod(p, year, m)));
  return <section className={styles.page}>
    <nav className={styles.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={`/pedagogico/turmas/${classId}`}>{classroom.data?.name ?? "Turma"}</Link><span>/ Projetos</span></nav>
    <header className={styles.heading}><div><h2>Projetos</h2><p>{classroom.data?.name} · Experiências e descobertas da turma</p></div></header>
    <PedagogicalModules classId={classId!} />
    {(classroom.error || projects.error) && <p role="alert">{classroom.error?.message ?? projects.error?.message}</p>}
    {classroom.data?.archived && <p>Turma arquivada. Projetos disponíveis somente para consulta e impressão.</p>}
    <div className={styles.toolbar}>
      <label>Ano<select value={year} onChange={e => setParams(e.target.value ? { ano: e.target.value } : {})}><option value="">Todos os anos</option>{projectYears(all).map(y => <option key={y}>{y}</option>)}</select></label>
      <label>Mês<select value={month} disabled={!year} onChange={e => setParams({ ano: year, ...(e.target.value ? { mes: e.target.value } : {}) })}><option value="">Todos os meses</option>{months.map(m => <option key={m} value={m}>{new Date(2026, Number(m) - 1, 1).toLocaleDateString("pt-BR", { month: "long" })}</option>)}</select></label>
      <label>Status<select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos</option><option value="DRAFT">Rascunho</option><option value="FINALIZED">Finalizado</option></select></label>
      <label>Responsável<select value={teacher} onChange={e => setTeacher(e.target.value)}><option value="">Todas</option>{teachers.map(t => <option key={t.id} value={t.id}>{t.displayName}</option>)}</select></label>
    </div>
    {classroom.data && !classroom.data.archived && <form className={styles.panel} onSubmit={e => { e.preventDefault(); create.mutate(); }}>
      <h3>Novo projeto</h3>
      <label>Título<input required maxLength={300} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <div className={styles.toolbar}>
        <label>Data inicial<input required type="date" min="1900-01-01" max="9998-12-31" value={startDate} onChange={e => setStartDate(e.target.value)} /></label>
        <label>Data final<input required type="date" min={startDate || "1900-01-01"} max="9998-12-31" value={endDate} onChange={e => setEndDate(e.target.value)} /></label>
        <Button type="submit" icon={<Plus />} disabled={create.isPending}>Criar projeto</Button>
      </div>
      {create.error && <p role="alert" className={styles.error}>{create.error.message}</p>}
    </form>}
    {projects.isPending && <p role="status">Carregando projetos…</p>}
    <div className={styles.cards}>{rows.map(p => <Link key={p.id} className={styles.card} to={`/pedagogico/projetos/${p.id}`}>
      <span className={styles.status}>{p.status === "DRAFT" ? "Rascunho" : "Finalizado"}</span><h3>{p.title}</h3>
      <p>{dateLabel(p.startDate)} a {dateLabel(p.endDate)}</p><small>{p.teachers.map(t => t.displayName).join(", ")}</small>
    </Link>)}</div>
    {projects.isSuccess && rows.length === 0 && <p className={styles.empty}>Nenhum projeto neste período.</p>}
  </section>;
}
