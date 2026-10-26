import { usePedagogicalScope } from "../hooks/usePedagogicalScope";
import { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { Camera, Plus } from "lucide-react";
import { Button } from "../components/Button";
import { PedagogicalModules } from "../components/PedagogicalModules";
import { dateLabel } from "../api/pedagogical";

import { RecordEditor, Gallery, PhotoImage } from "../components/RecordPhotos";
import base from "./PedagogicalPage.module.css";
import styles from "./ActivityRecordsPage.module.css";

export default function ActivityRecordsPage() {
  const { classId, recordId } = useParams();
  return recordId ? <RecordDetail key={recordId} id={recordId} /> : <Records key={classId} classId={classId!} />;
}

function Records({ classId }: { classId: string }) {
  const scope = usePedagogicalScope();
  const { getClass, getRecord, listRecords, saveRecord } = scope;
  const classroom = useQuery({ queryKey: ["pedagogical", scope.key, "class", classId], queryFn: () => getClass(classId) });
  const [params, setParams] = useSearchParams();
  const year = params.get("ano") ?? "", month = params.get("mes") ?? "";
  const page = Math.max(0, Number(params.get("pagina")) || 0);
  const records = useQuery({ queryKey: ["pedagogical", scope.key, "records", classId, year, month, page], queryFn: () => listRecords(classId, year, month, page), enabled: !!classroom.data });
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const filter = (y: string, m: string) => setParams({ ...(y ? { ano: y } : {}), ...(y && m ? { mes: m } : {}) });
  if (classroom.error) return <p role="alert">{classroom.error.message}</p>;
  if (!classroom.data) return <p role="status">Carregando turma…</p>;
  return <section className={base.page}>
    <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={scope.classBase(classId)}>{classroom.data.name}</Link><span>/ Registros</span></nav>
    <header className={base.heading}><div><h2>Registros</h2><p>{classroom.data.name} · Memórias das nossas propostas</p></div><Camera size={36} /></header>
    <PedagogicalModules classId={classId} legacyAee={classroom.data.aee} />
    {classroom.data.archived && <p>Turma arquivada. Os registros estão disponíveis somente para consulta.</p>}
    {!classroom.data.archived && !classroom.data.canEdit && <p>Turma compartilhada. Os registros estão disponíveis somente para consulta.</p>}
    <div className={base.toolbar}>
      <label>Ano<input type="number" min="1900" max="9998" placeholder="Todos os anos" value={year} onChange={e => filter(e.target.value, month)} /></label>
      <label>Mês<select disabled={!year} value={month} onChange={e => filter(year, e.target.value)}><option value="">Todos os meses</option>{Array.from({ length: 12 }, (_, i) => <option key={i} value={String(i + 1)}>{new Date(2026, i, 1).toLocaleDateString("pt-BR", { month: "long" })}</option>)}</select></label>
      {classroom.data.canCreate && !creating && <Button variant="primary" icon={<Plus />} disabled={!records.data} onClick={() => setCreating(true)}>Novo registro</Button>}
    </div>
    {creating && classroom.data.canCreate && <RecordEditor namespace="pedagogical" scopeKey={scope.key} load={getRecord} save={(input, files, id) => saveRecord(classId, input, files, id)} limit={records.data?.attachmentLimitMb ?? 1} onCancel={() => setCreating(false)} onSaved={r => navigate(scope.recordUrl(r.id))} />}
    {records.isPending && <p role="status">Carregando registros…</p>}
    {records.error && <p role="alert">{records.error.message}</p>}
    <div className={styles.albums}>{records.data?.items.map(r => <Link className={styles.album} key={r.id} to={scope.recordUrl(r.id)}>
      <PhotoImage photo={r.cover} alt="" /><div><h3>{r.title}</h3><p>{dateLabel(r.activityDate)} · {r.photoCount} {r.photoCount === 1 ? "foto" : "fotos"}</p><small>{r.authorName}</small></div>
    </Link>)}</div>
    {records.data?.items.length === 0 && <div className={styles.empty}><Camera size={40} /><h3>Nenhum registro neste período</h3><p>Guarde aqui as fotos das propostas da turma.</p></div>}
    {records.data && records.data.totalPages > 1 && <nav className={base.toolbar} aria-label="Páginas dos registros">
      <Button disabled={page === 0} onClick={() => setParams({ ...Object.fromEntries(params), pagina: String(page - 1) })}>Anterior</Button>
      <span>Página {page + 1} de {records.data.totalPages}</span>
      <Button disabled={page + 1 >= records.data.totalPages} onClick={() => setParams({ ...Object.fromEntries(params), pagina: String(page + 1) })}>Próxima</Button>
    </nav>}
  </section>;
}

function RecordDetail({ id }: { id: string }) {
  const scope = usePedagogicalScope();
  const { deleteRecord, getRecord, saveRecord } = scope;
  const record = useQuery({ queryKey: ["pedagogical", scope.key, "record", id], queryFn: () => getRecord(id) });
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const client = useQueryClient(), navigate = useNavigate();
  const remove = useMutation({ mutationFn: () => deleteRecord(id, record.data!.version), onSuccess: () => {
    client.removeQueries({ queryKey: ["pedagogical", scope.key, "record", id] });
    client.invalidateQueries({ queryKey: ["pedagogical", scope.key, "records"] });
    navigate(scope.classBase(record.data!.classId) + "/registros");
  } });
  if (record.error) return <p role="alert">{record.error.message}</p>;
  if (!record.data) return <p role="status">Carregando registro…</p>;
  const r = record.data;
  return <section className={base.page}>
    <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={scope.classBase(r.classId)}>{r.className}</Link><span>/</span><Link to={scope.classBase(r.classId) + "/registros"}>Registros</Link></nav>
    <header className={base.heading}><div><h2 className={styles.title}>{r.title}</h2><p>{dateLabel(r.activityDate)} · {r.authorName} · {r.photos.length} fotos</p></div></header>
    {r.archived && <p>Turma arquivada. Disponível somente para consulta.</p>}
    {!editing && r.canEdit && <div className={base.toolbar}><Button onClick={() => setEditing(true)}>Editar registro</Button><Button variant="danger" onClick={() => setDeleting(true)}>Excluir registro</Button></div>}
    {editing && <RecordEditor initial={r} namespace="pedagogical" scopeKey={scope.key} load={getRecord} save={(input, files, id) => saveRecord(r.classId, input, files, id)} limit={r.attachmentLimitMb} onCancel={() => setEditing(false)} onSaved={() => setEditing(false)} />}
    {!editing && <Gallery photos={r.photos} title={r.title} />}
    <Dialog.Root open={deleting} onOpenChange={open => { if (!remove.isPending) setDeleting(open); }}><Dialog.Portal><Dialog.Overlay className={styles.overlay} /><Dialog.Content className={styles.confirm}>
      <Dialog.Title>Excluir registro?</Dialog.Title><Dialog.Description>O registro e suas fotos serão removidos. Esta ação não pode ser desfeita.</Dialog.Description>
      {remove.error && <p role="alert">{remove.error.message}</p>}
      <div className={base.toolbar}><Button disabled={remove.isPending} onClick={() => setDeleting(false)}>Cancelar</Button><Button variant="danger" disabled={remove.isPending} onClick={() => remove.mutate()}>Confirmar exclusão</Button></div>
    </Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}
