import { useState } from "react";
import { Link, Navigate, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { Camera, Plus } from "lucide-react";
import { Button } from "../components/Button";
import { RecordEditor, Gallery, PhotoImage } from "../components/RecordPhotos";
import { dateLabel } from "../api/pedagogical";
import { listGeneralRecords, getGeneralRecord, saveGeneralRecord, deleteGeneralRecord } from "../api/records";
import { useSession } from "../hooks/useSession";
import { ApiError } from "../api/http";
import base from "./PedagogicalPage.module.css";
import styles from "./ActivityRecordsPage.module.css";

export default function GeneralRecordsPage() {
  const session = useSession();
  const { recordId } = useParams();
  if (!session.data) return null;
  if (!session.data.roles.some(role => ["ADMIN", "MANAGER", "REQUESTER"].includes(role))) return <Navigate to="/tickets" replace />;
  return recordId ? <Detail key={recordId} id={recordId} /> : <Records />;
}

function Records() {
  const [params, setParams] = useSearchParams();
  const year = params.get("ano") ?? "", month = params.get("mes") ?? "";
  const page = Math.max(0, Number(params.get("pagina")) || 0);
  const records = useQuery({ queryKey: ["general-records", "records", year, month, page], queryFn: () => listGeneralRecords(year, month, page) });
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const filter = (y: string, m: string) => setParams({ ...(y ? { ano: y } : {}), ...(y && m ? { mes: m } : {}) });
  return <section className={base.page}>
    <header className={base.heading}><div><h2>Registros</h2><p>Fotos e memórias dos eventos da instituição.</p></div><Camera size={36} /></header>
    <div className={base.toolbar}>
      <label>Ano<input type="number" min="1900" max="9998" placeholder="Todos os anos" value={year} onChange={e => filter(e.target.value, month)} /></label>
      <label>Mês<select disabled={!year} value={month} onChange={e => filter(year, e.target.value)}><option value="">Todos os meses</option>{Array.from({ length: 12 }, (_, i) => <option key={i} value={String(i + 1)}>{new Date(2026, i, 1).toLocaleDateString("pt-BR", { month: "long" })}</option>)}</select></label>
      {records.data?.canCreate && !creating && <Button variant="primary" icon={<Plus />} onClick={() => setCreating(true)}>Novo registro</Button>}
    </div>
    {creating && records.data?.canCreate && <RecordEditor namespace="general-records" subject="evento" load={getGeneralRecord} save={saveGeneralRecord}
      limit={records.data.attachmentLimitMb} maxPhotos={records.data.maxPhotos} onCancel={() => setCreating(false)} onSaved={r => navigate(`/registros/${r.id}`)} />}
    {records.isPending && <p role="status">Carregando registros…</p>}
    {records.error && <p role="alert">{records.error.message}</p>}
    <div className={styles.albums}>{records.data?.items.map(r => <Link className={styles.album} key={r.id} to={`/registros/${r.id}`}>
      <PhotoImage photo={r.cover} alt="" /><div><h3>{r.title}</h3><p>{dateLabel(r.activityDate)} · {r.photoCount} {r.photoCount === 1 ? "foto" : "fotos"}</p><small>{r.authorName}</small></div>
    </Link>)}</div>
    {records.data?.items.length === 0 && <div className={styles.empty}><Camera size={40} /><h3>Nenhum registro neste período</h3><p>Guarde aqui as fotos dos eventos da instituição.</p></div>}
    {records.data && records.data.totalPages > 1 && <nav className={base.toolbar} aria-label="Páginas dos registros">
      <Button disabled={page === 0} onClick={() => setParams({ ...Object.fromEntries(params), pagina: String(page - 1) })}>Anterior</Button>
      <span>Página {page + 1} de {records.data.totalPages}</span>
      <Button disabled={page + 1 >= records.data.totalPages} onClick={() => setParams({ ...Object.fromEntries(params), pagina: String(page + 1) })}>Próxima</Button>
    </nav>}
  </section>;
}

function Detail({ id }: { id: string }) {
  const record = useQuery({ queryKey: ["general-records", "record", id], queryFn: () => getGeneralRecord(id) });
  const [editing, setEditing] = useState(false), [deleting, setDeleting] = useState(false);
  const client = useQueryClient(), navigate = useNavigate();
  const remove = useMutation({ mutationFn: () => deleteGeneralRecord(id, record.data!.version), onSuccess: () => {
    client.removeQueries({ queryKey: ["general-records", "record", id] });
    void client.invalidateQueries({ queryKey: ["general-records", "records"] }); navigate("/registros");
  }, onError: error => { if (error instanceof ApiError && error.status === 409) void record.refetch(); } });
  if (record.error) return <p role="alert">{record.error.message}</p>;
  if (!record.data) return <p role="status">Carregando registro…</p>;
  const r = record.data, showEditor = editing && r.canEdit;
  return <section className={base.page}>
    <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/registros">Registros</Link><span>/</span><span>{r.title}</span></nav>
    <header className={base.heading}><div><h2 className={styles.title}>{r.title}</h2><p>{dateLabel(r.activityDate)} · {r.authorName} · {r.photos.length} fotos</p></div></header>
    {!showEditor && r.canEdit && <div className={base.toolbar}><Button onClick={() => setEditing(true)}>Editar registro</Button><Button variant="danger" onClick={() => setDeleting(true)}>Excluir registro</Button></div>}
    {showEditor && <RecordEditor initial={r} namespace="general-records" subject="evento" load={getGeneralRecord} save={saveGeneralRecord}
      limit={r.attachmentLimitMb} maxPhotos={r.maxPhotos} onCancel={() => setEditing(false)} onSaved={() => setEditing(false)} />}
    {!showEditor && <Gallery photos={r.photos} title={r.title} />}
    <Dialog.Root open={deleting && r.canEdit} onOpenChange={open => { if (!remove.isPending) setDeleting(open); }}><Dialog.Portal><Dialog.Overlay className={styles.overlay} /><Dialog.Content className={styles.confirm}>
      <Dialog.Title>Excluir registro?</Dialog.Title><Dialog.Description>O registro e suas fotos serão removidos. Esta ação não pode ser desfeita.</Dialog.Description>
      {remove.error && <p role="alert">{remove.error.message}</p>}
      <div className={base.toolbar}><Button disabled={remove.isPending} onClick={() => setDeleting(false)}>Cancelar</Button><Button variant="danger" disabled={remove.isPending} onClick={() => remove.mutate()}>Confirmar exclusão</Button></div>
    </Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}
