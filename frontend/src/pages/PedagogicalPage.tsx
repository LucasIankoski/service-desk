import { AeeDirectory } from "./AeePage";
import { usePedagogicalScope } from "../hooks/usePedagogicalScope";
import { useState } from "react";
import { Link, Navigate, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookOpen, Folder, Plus, Settings } from "lucide-react";
import { useSession } from "../hooks/useSession";
import { PedagogicalModules } from "../components/PedagogicalModules";
import { Button } from "../components/Button";
import { listClasses, listTeachers, saveClass, getClass, listPlans, createPlan, dateLabel, planPeriods } from "../api/pedagogical";
import type { ClassContentVisibility, SchoolClass, Teacher } from "../api/pedagogical";
import styles from "./PedagogicalPage.module.css";

export default function PedagogicalPage() {
  const session = useSession();
  const { classId } = useParams();
  if (!session.data) return null;
  const admin = session.data.roles.some(r => r === "ADMIN" || r === "MANAGER");
  if (!admin && !session.data.roles.includes("REQUESTER")) return <Navigate to="/tickets" replace />;
  return <section className={styles.page}>
    <header className={styles.heading}><div><span>ESCOLA · ORGANIZAÇÃO PEDAGÓGICA</span><h2>Pedagógico</h2><p>Planejar, compartilhar e acompanhar as experiências de cada turma.</p></div><BookOpen size={36} /></header>
    {classId ? <ClassFolder key={classId} id={classId} admin={admin} /> : <Classes admin={admin} />}
  </section>;
}

function Classes({ admin }: { admin: boolean }) {
  const classes = useQuery({ queryKey: ["pedagogical", "classes"], queryFn: listClasses });
  const [search, setSearch] = useState("");
  const [teacher, setTeacher] = useState("");
  const [archived, setArchived] = useState(false);
  const [editing, setEditing] = useState<SchoolClass | "new" | null>(null);
  const teachers = [...new Map(classes.data?.flatMap(c => c.teachers).map(t => [t.id, t])).values()];
  const rows = classes.data?.filter(c => c.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()) && (archived || !c.archived) && (!teacher || c.teachers.some(t => t.id === teacher)));
  return <>
    <div className={styles.toolbar}>
      <label>Buscar turma<input value={search} onChange={e => setSearch(e.target.value)} placeholder="Nome da turma" /></label>
      {admin && <label>Professora<select aria-label="Professora" value={teacher} onChange={e => setTeacher(e.target.value)}><option value="">Todas</option>{teachers.map(t => <option key={t.id} value={t.id}>{t.displayName}</option>)}</select></label>}
      <label className={styles.check}><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} /> Incluir arquivadas</label>
      {admin && <Button icon={<Plus />} onClick={() => setEditing("new")}>Nova turma</Button>}
    </div>
    {editing && <ClassEditor key={editing === "new" ? "new" : editing.id} row={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} />}
    {classes.isPending && <p role="status">Carregando turmas…</p>}
    {classes.error && <p role="alert">{classes.error.message}</p>}
    <div className={styles.cards}>{rows?.map(c => <article key={c.id} className={`${styles.card} ${styles.classCard}`}>
      <header className={styles.classCardHeading}><Folder aria-hidden /><h3><Link to={"/pedagogico/turmas/" + c.id}>{c.name}</Link></h3></header>
      <div className={styles.classCardBody}>
      <p>{c.teachers.map(t => t.displayName).join(", ") || "Nenhuma professora alocada"}</p>
      <span>{c.archived ? "Arquivada · consulta" : c.aee ? "Atendimento Educacional Especializado" : "Turma ativa"}</span>
      {c.contentVisibility === "PEDAGOGICAL_READ_ONLY" && <span>{c.canEdit ? "Compartilhada com o Pedagógico" : "Compartilhada · somente consulta"}</span>}
      {c.canManage && <Button icon={<Settings />} onClick={() => setEditing(c)}>Gerenciar turma</Button>}
      </div>
    </article>)}</div>
    {rows?.length === 0 && <p className={styles.empty}>{admin ? "Nenhuma turma encontrada. Cadastre uma turma ou ajuste os filtros." : "Nenhuma turma alocada ou compartilhada disponível. Ajuste os filtros ou consulte a administração."}</p>}
  </>;
}

function ClassEditor({ row, onClose }: { row?: SchoolClass; onClose: () => void }) {
  const client = useQueryClient();
  const teachers = useQuery({ queryKey: ["pedagogical", "teachers"], queryFn: listTeachers });
  const [name, setName] = useState(row?.name ?? "");
  const [aee, setAee] = useState(row?.aee ?? false);
  const [selected, setSelected] = useState(row?.teachers.map(t => t.id) ?? []);
  const [archived, setArchived] = useState(row?.archived ?? false);
  const [contentVisibility, setContentVisibility] = useState<ClassContentVisibility>(row?.contentVisibility ?? "ALLOCATED_ONLY");
  const options: Teacher[] = [...new Map([...(row?.teachers ?? []), ...(teachers.data ?? [])].map(t => [t.id, t])).values()];
  const mutation = useMutation({
    mutationFn: () => saveClass({ name, archived, teacherIds: selected, version: row?.version, contentVisibility, aee }, row?.id),
    onSuccess: () => { client.invalidateQueries({ queryKey: ["pedagogical"] }); onClose(); }
  });
  return <form className={styles.panel} onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
    <h3>{row ? "Gerenciar turma" : "Nova turma"}</h3>
    <label>Nome da turma<input required maxLength={120} value={name} onChange={e => setName(e.target.value)} disabled={!!row?.archived && archived} /></label>
    <fieldset disabled={!!row?.archived && archived}><legend>Professoras alocadas</legend>
      <p>As professoras selecionadas poderão produzir conteúdo e acessar todo o histórico desta turma, respeitando as permissões de cada documento.</p>
      {options.map(t => <label className={styles.check} key={t.id}><input type="checkbox" checked={selected.includes(t.id)} onChange={e => setSelected(e.target.checked ? [...selected, t.id] : selected.filter(id => id !== t.id))} />{t.displayName}</label>)}
      {teachers.isPending && <p>Carregando professoras…</p>}
      {teachers.error && <p role="alert">{teachers.error.message}</p>}
      {teachers.data?.length === 0 && <p>Cadastre usuários ativos com perfil Solicitante para alocá-los.</p>}
    </fieldset>
    <label className={styles.check}><input type="checkbox" checked={aee} disabled={!!row?.archived && archived} onChange={e => setAee(e.target.checked)} />Atendimento Educacional Especializado</label>
    <label>Visibilidade do conteúdo<select value={contentVisibility} disabled={!!row?.archived && archived} onChange={e => setContentVisibility(e.target.value as ClassContentVisibility)} aria-describedby="class-visibility-help">
      <option value="ALLOCATED_ONLY">Restrita às professoras alocadas</option>
      <option value="PEDAGOGICAL_READ_ONLY">Compartilhada com o Pedagógico</option>
    </select></label>
    <p id="class-visibility-help">A administração mantém seu acesso. Quando compartilhada, qualquer usuário ativo com acesso ao Pedagógico pode consultar todo o histórico, inclusive rascunhos e fotos. A escrita continua limitada às professoras alocadas e à administração.</p>
    {row && <label className={styles.check}><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} />Turma arquivada (somente consulta)</label>}
    <div className={styles.toolbar}><Button type="submit" variant="primary" disabled={mutation.isPending || teachers.isPending}>Salvar turma</Button><Button type="button" onClick={onClose}>Cancelar</Button></div>
    {mutation.error && <p role="alert" className={styles.error}>{mutation.error.message}</p>}
  </form>;
}

function ClassFolder({ id, admin }: { id: string; admin: boolean }) {
  const classroom = useQuery({ queryKey: ["pedagogical", "class", id], queryFn: () => getClass(id) });
  const [params] = useSearchParams();
  if (classroom.data?.aee && params.get("historico")!=="1") return <AeeDirectory id={id} classroom={classroom.data} />;
  if (classroom.isPending) return <p role="status">Carregando turma…</p>;
  if (classroom.error) return <p role="alert">{classroom.error.message}</p>;
  return <PlanningFolder id={id} admin={admin} />;
}
export function PlanningFolder({ id, admin }: { id: string; admin: boolean }) {
  const scope = usePedagogicalScope();
  const { getClass, listPlans, createPlan } = scope;
  const classroom = useQuery({ queryKey: ["pedagogical", scope.key, "class", id], queryFn: () => getClass(id) });
  const plans = useQuery({ queryKey: ["pedagogical", scope.key, "plans", id], queryFn: () => listPlans(id) });
  const [params, updateParams] = useSearchParams();
  const setParams = (value: Record<string, string>) => updateParams({ ...(classroom.data?.aee ? { historico: "1" } : {}), ...value });
  const year = params.get("ano") ?? "";
  const month = params.get("mes") ?? "";
  const [status, setStatus] = useState("");
  const [teacher, setTeacher] = useState("");
  const [monday, setMonday] = useState("");
  const navigate = useNavigate();
  const client = useQueryClient();
  const create = useMutation({
    mutationFn: () => createPlan(id, monday),
    onSuccess: p => { client.invalidateQueries({ queryKey: ["pedagogical"] }); navigate(scope.planUrl(p.id)); }
  });
  const periods = [...new Set(plans.data?.flatMap(planPeriods))].sort().reverse();
  const years = [...new Set(periods.map(p => p.slice(0, 4)))];
  const months = periods.filter(p => p.startsWith(year + "-")).map(p => p.slice(5));
  const teachers = [...new Map(plans.data?.flatMap(p => p.teachers).map(t => [t.id, t])).values()];
  const rows = plans.data?.filter(p => (!year || planPeriods(p).some(period => period.startsWith(year + (month ? "-" + month : "")))) && (!status || p.status === status) && (!teacher || p.teachers.some(t => t.id === teacher)));
  const monthName = (m: string) => new Date(2026, Number(m) - 1, 1).toLocaleDateString("pt-BR", { month: "long" });
  return <>
    <nav className={styles.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><button onClick={() => setParams({})}>{classroom.data?.name ?? "Turma"}</button>{year && <><span>/</span><button onClick={() => setParams({ ano: year })}>{year}</button></>}{month && <><span>/</span><span>{monthName(month)}</span></>}</nav>
    {classroom.error && <p role="alert">{classroom.error.message}</p>}
    {classroom.data && <>
      <h3>{classroom.data.name} {classroom.data.archived && "· Arquivada"}</h3>
      {!classroom.data.archived && !classroom.data.canEdit && <p>{classroom.data.aee ? "Documentos anteriores do AEE. Disponíveis somente para consulta e impressão." : "Turma compartilhada. Disponível somente para consulta e impressão."}</p>}
      <PedagogicalModules classId={id} legacyAee={classroom.data.aee} />
      <div className={styles.toolbar}>
        <label>Ano<select aria-label="Ano" value={year} onChange={e => setParams(e.target.value ? { ano: e.target.value } : {})}><option value="">Todos os anos</option>{years.map(y => <option key={y}>{y}</option>)}</select></label>
        <label>Mês<select aria-label="Mês" disabled={!year} value={month} onChange={e => setParams({ ano: year, ...(e.target.value ? { mes: e.target.value } : {}) })}><option value="">Todos os meses</option>{months.map(m => <option key={m} value={m}>{monthName(m)}</option>)}</select></label>
        <label>Status<select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)}><option value="">Todos</option><option value="DRAFT">Rascunho</option><option value="FINALIZED">Finalizado</option></select></label>
        {admin && <label>Responsável<select aria-label="Responsável" value={teacher} onChange={e => setTeacher(e.target.value)}><option value="">Todas</option>{teachers.map(t => <option key={t.id} value={t.id}>{t.displayName}</option>)}</select></label>}
      </div>
      {!year && <div className={styles.folders}>{years.map(y => <Button key={y} icon={<Folder />} onClick={() => setParams({ ano: y })}>{y}</Button>)}</div>}
      {year && !month && <div className={styles.folders}>{months.map(m => <Button key={m} icon={<Folder />} onClick={() => setParams({ ano: year, mes: m })}>{monthName(m)}</Button>)}</div>}
      {classroom.data.canCreate && <form className={styles.toolbar} onSubmit={e => { e.preventDefault(); create.mutate(); }}>
        <label>Nova semana (segunda-feira)<input type="date" required value={monday} min="1900-01-01" max="9998-12-31" onChange={e => setMonday(e.target.value)} /></label>
        <Button type="submit" icon={<Plus />} disabled={create.isPending}>Criar planejamento</Button>
      </form>}
    </>}
    {create.error && <p role="alert" className={styles.error}>{create.error.message}</p>}
    {plans.error && <p role="alert">{plans.error.message}</p>}
    {plans.isPending && <p role="status">Carregando planejamentos…</p>}
    <div className={styles.cards}>{rows?.map(p => <Link key={p.id} className={styles.card} to={scope.planUrl(p.id)}>
      <span className={styles.status}>{p.status === "DRAFT" ? "Rascunho" : "Finalizado"}</span>
      <h3>{dateLabel(p.weekStart)} a {dateLabel(p.weekEnd)}</h3><p>{p.theme || "Tema ainda não preenchido"}</p><small>{p.teachers.map(t => t.displayName).join(", ")}</small>
    </Link>)}</div>
    {rows?.length === 0 && <p className={styles.empty}>Nenhum planejamento neste período.</p>}
  </>;
}
