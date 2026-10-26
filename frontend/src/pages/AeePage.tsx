import { useState } from "react";
import { Link, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "../components/Button";
import { aeeClasses, listStudents, saveStudent } from "../api/aee";
import type { Student } from "../api/aee";
import { getClass } from "../api/pedagogical";
import type { SchoolClass } from "../api/pedagogical";
import { useSession } from "../hooks/useSession";
import { PlanningFolder } from "./PedagogicalPage";
import styles from "./PedagogicalPage.module.css";

export function AeeDirectory({ id, classroom }: { id: string; classroom: SchoolClass }) {
  const [archived, setArchived] = useState(false);
  const session = useSession();
  const rows = useQuery({ queryKey: ["pedagogical", "aee", id, archived], queryFn: () => aeeClasses(id, archived) });
  const legacy = classroom.contentVisibility === "PEDAGOGICAL_READ_ONLY" || classroom.canManage || classroom.teachers.some(t => t.id === session.data?.id);
  return <>
    <nav aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link> / {classroom.name}</nav>
    <h3>{classroom.name} · Atendimento Educacional Especial</h3>
    <p>Selecione a turma regular para consultar os alunos atendidos neste AEE.</p>
    {classroom.archived && <p>AEE arquivado. Conteúdos disponíveis somente para consulta.</p>}
    <label className={styles.check}><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} />Incluir turmas arquivadas</label>
    {rows.isPending && <p role="status">Carregando turmas regulares…</p>}
    {rows.error && <p role="alert">{rows.error.message}</p>}
    <div className={styles.cards}>{rows.data?.map(c => <article className={styles.card} key={c.id}>
      <h3>{c.canView ? <Link to={`/pedagogico/turmas/${id}/turmas/${c.id}/alunos`}>{c.name}</Link> : c.name}</h3>
      <p>{c.canView ? c.archived ? "Arquivada · consulta" : "" : "Acesso restrito"}</p>
    </article>)}</div>
  </>;
}

export default function AeePage() {
  const { aeeId, classId, studentId } = useParams();
  const session = useSession();
  if (studentId) return <section className={styles.page}>
    <nav aria-label="Navegação do aluno"><Link to={`/pedagogico/turmas/${aeeId}`}>Atendimento Educacional Especial</Link> / <Link to={`/pedagogico/turmas/${aeeId}/turmas/${classId}/alunos`}>Alunos</Link></nav>
    <PlanningFolder key={studentId} id={classId!} admin={!!session.data?.roles.some(r => r === "ADMIN" || r === "MANAGER")} />
  </section>;
  return <Students aeeId={aeeId!} classId={classId!} />;
}

function Students({ aeeId, classId }: { aeeId: string; classId: string }) {
  const client = useQueryClient();
  const [archived, setArchived] = useState(false);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Student | "new" | null>(null);
  const rows = useQuery({ queryKey: ["pedagogical", "students", aeeId, classId], queryFn: () => listStudents(aeeId, classId) });
  const parent = useQuery({ queryKey: ["pedagogical", "class", aeeId], queryFn: () => getClass(aeeId) });
  const directory = useQuery({ queryKey: ["pedagogical", "aee", aeeId, true], queryFn: () => aeeClasses(aeeId, true) });
  const classroom = directory.data?.find(c => c.id === classId);
  return <section className={styles.page}>
    <nav className={styles.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link> / <Link to={`/pedagogico/turmas/${aeeId}`}>{parent.data?.name ?? "AEE"}</Link> / <span>{classroom?.name ?? "Alunos"}</span></nav>
    <h2>Alunos · {classroom?.name}</h2>
    <p>Os alunos e seus conteúdos pertencem exclusivamente a este AEE e a esta turma regular.</p>
    <div className={styles.toolbar}>
      <label>Buscar aluno<input value={search} onChange={e => setSearch(e.target.value)} /></label>
      <label className={styles.check}><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} />Incluir alunos arquivados</label>
      {classroom?.canCreateStudents && <Button onClick={() => setEditing("new")}>Cadastrar aluno</Button>}
    </div>
    {editing && <StudentEditor key={editing === "new" ? "new" : editing.id} aeeId={aeeId} classId={classId} row={editing === "new" ? undefined : editing} close={() => setEditing(null)} saved={() => { void client.invalidateQueries({ queryKey: ["pedagogical"] }); setEditing(null); }} />}
    {rows.isPending && <p role="status">Carregando alunos…</p>}
    {(rows.error || directory.error || parent.error) && <p role="alert">{rows.error?.message ?? directory.error?.message ?? parent.error?.message}</p>}
    <div className={styles.cards}>{rows.data?.filter(s => (archived || !s.archived) && s.name.toLocaleLowerCase("pt-BR").includes(search.toLocaleLowerCase("pt-BR"))).map(s => <article className={styles.card} key={s.id}>
      <h3><Link to={`/pedagogico/turmas/${aeeId}/turmas/${classId}/alunos/${s.id}`}>{s.name}</Link></h3>
      <p>{s.archived ? "Arquivado · consulta" : "Aluno ativo"}</p>
      {s.canManage && <Button onClick={() => setEditing(s)}>Gerenciar aluno</Button>}
    </article>)}</div>
    {rows.data?.length === 0 && <p>Nenhum aluno cadastrado neste contexto.</p>}
  </section>;
}

function StudentEditor({ aeeId, classId, row, close, saved }: { aeeId: string; classId: string; row?: Student; close: () => void; saved: () => void }) {
  const [name, setName] = useState(row?.name ?? "");
  const [archived, setArchived] = useState(row?.archived ?? false);
  const mutation = useMutation({ mutationFn: () => saveStudent(aeeId, classId, name, archived, row), onSuccess: saved });
  return <form className={styles.panel} onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
    <h3>{row ? "Gerenciar aluno" : "Cadastrar aluno"}</h3>
    <label>Nome completo<input required maxLength={160} value={name} disabled={!!row?.archived && archived} onChange={e => setName(e.target.value)} /></label>
    {row && <label className={styles.check}><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} />Aluno arquivado (somente consulta)</label>}
    <div className={styles.toolbar}><Button type="submit" disabled={mutation.isPending}>Salvar aluno</Button><Button type="button" disabled={mutation.isPending} onClick={close}>Cancelar</Button></div>
    {mutation.error && <p role="alert">{mutation.error.message}</p>}
  </form>;
}
