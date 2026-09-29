import { useEffect, useRef, useState } from "react";
import { Link, useBlocker, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { Camera, ChevronLeft, ChevronRight, Plus, X } from "lucide-react";
import { Button } from "../components/Button";
import { PedagogicalModules } from "../components/PedagogicalModules";
import { getClass, dateLabel } from "../api/pedagogical";
import { getRecord, listRecords, saveRecord, deleteRecord } from "../api/records";
import type { ActivityRecord, ActivityPhoto } from "../api/records";
import { ApiError } from "../api/http";
import { usePublicSettings } from "../app/PublicSettingsContext";
import base from "./PedagogicalPage.module.css";
import styles from "./ActivityRecordsPage.module.css";

export default function ActivityRecordsPage() {
  const { classId, recordId } = useParams();
  return recordId ? <RecordDetail key={recordId} id={recordId} /> : <Records key={classId} classId={classId!} />;
}

function Records({ classId }: { classId: string }) {
  const classroom = useQuery({ queryKey: ["pedagogical", "class", classId], queryFn: () => getClass(classId) });
  const [params, setParams] = useSearchParams();
  const year = params.get("ano") ?? "", month = params.get("mes") ?? "";
  const page = Math.max(0, Number(params.get("pagina")) || 0);
  const records = useQuery({ queryKey: ["pedagogical", "records", classId, year, month, page], queryFn: () => listRecords(classId, year, month, page), enabled: !!classroom.data });
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const filter = (y: string, m: string) => setParams({ ...(y ? { ano: y } : {}), ...(y && m ? { mes: m } : {}) });
  if (classroom.error) return <p role="alert">{classroom.error.message}</p>;
  if (!classroom.data) return <p role="status">Carregando turma…</p>;
  return <section className={base.page}>
    <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={`/pedagogico/turmas/${classId}`}>{classroom.data.name}</Link><span>/ Registros</span></nav>
    <header className={base.heading}><div><h2>Registros</h2><p>{classroom.data.name} · Memórias das nossas propostas</p></div><Camera size={36} /></header>
    <PedagogicalModules classId={classId} />
    {classroom.data.archived && <p>Turma arquivada. Os registros estão disponíveis somente para consulta.</p>}
    <div className={base.toolbar}>
      <label>Ano<input type="number" min="1900" max="9998" placeholder="Todos os anos" value={year} onChange={e => filter(e.target.value, month)} /></label>
      <label>Mês<select disabled={!year} value={month} onChange={e => filter(year, e.target.value)}><option value="">Todos os meses</option>{Array.from({ length: 12 }, (_, i) => <option key={i} value={String(i + 1)}>{new Date(2026, i, 1).toLocaleDateString("pt-BR", { month: "long" })}</option>)}</select></label>
      {!classroom.data.archived && !creating && <Button variant="primary" icon={<Plus />} disabled={!records.data} onClick={() => setCreating(true)}>Novo registro</Button>}
    </div>
    {creating && !classroom.data.archived && <RecordEditor classId={classId} limit={records.data?.attachmentLimitMb ?? 1} onCancel={() => setCreating(false)} onSaved={r => navigate(`/pedagogico/registros/${r.id}`)} />}
    {records.isPending && <p role="status">Carregando registros…</p>}
    {records.error && <p role="alert">{records.error.message}</p>}
    <div className={styles.albums}>{records.data?.items.map(r => <Link className={styles.album} key={r.id} to={`/pedagogico/registros/${r.id}`}>
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
  const record = useQuery({ queryKey: ["pedagogical", "record", id], queryFn: () => getRecord(id) });
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const client = useQueryClient(), navigate = useNavigate();
  const remove = useMutation({ mutationFn: () => deleteRecord(id, record.data!.version), onSuccess: () => {
    client.removeQueries({ queryKey: ["pedagogical", "record", id] });
    client.invalidateQueries({ queryKey: ["pedagogical", "records"] });
    navigate(`/pedagogico/turmas/${record.data!.classId}/registros`);
  } });
  if (record.error) return <p role="alert">{record.error.message}</p>;
  if (!record.data) return <p role="status">Carregando registro…</p>;
  const r = record.data;
  return <section className={base.page}>
    <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={`/pedagogico/turmas/${r.classId}`}>{r.className}</Link><span>/</span><Link to={`/pedagogico/turmas/${r.classId}/registros`}>Registros</Link></nav>
    <header className={base.heading}><div><h2 className={styles.title}>{r.title}</h2><p>{dateLabel(r.activityDate)} · {r.authorName} · {r.photos.length} fotos</p></div></header>
    {r.archived && <p>Turma arquivada. Disponível somente para consulta.</p>}
    {!editing && r.canEdit && <div className={base.toolbar}><Button onClick={() => setEditing(true)}>Editar registro</Button><Button variant="danger" onClick={() => setDeleting(true)}>Excluir registro</Button></div>}
    {editing && <RecordEditor initial={r} classId={r.classId} limit={r.attachmentLimitMb} onCancel={() => setEditing(false)} onSaved={() => setEditing(false)} />}
    {!editing && <Gallery photos={r.photos} title={r.title} />}
    <Dialog.Root open={deleting} onOpenChange={open => { if (!remove.isPending) setDeleting(open); }}><Dialog.Portal><Dialog.Overlay className={styles.overlay} /><Dialog.Content className={styles.confirm}>
      <Dialog.Title>Excluir registro?</Dialog.Title><Dialog.Description>O registro e suas fotos serão removidos. Esta ação não pode ser desfeita.</Dialog.Description>
      {remove.error && <p role="alert">{remove.error.message}</p>}
      <div className={base.toolbar}><Button disabled={remove.isPending} onClick={() => setDeleting(false)}>Cancelar</Button><Button variant="danger" disabled={remove.isPending} onClick={() => remove.mutate()}>Confirmar exclusão</Button></div>
    </Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}

function PhotoImage({ photo, alt }: { photo: ActivityPhoto; alt: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <span className={styles.unavailable}>Foto indisponível</span> : <img src={photo.thumbnailUrl} alt={alt} loading="lazy" width={photo.width} height={photo.height} onError={() => setFailed(true)} />;
}

function Gallery({ photos, title }: { photos: ActivityPhoto[]; title: string }) {
  const opener = useRef<HTMLButtonElement | null>(null);
  const [index, setIndex] = useState<number | null>(null);
  const [failed, setFailed] = useState(false);
  const move = (step: number) => { setFailed(false); setIndex(i => i === null ? null : (i + step + photos.length) % photos.length); };
  return <><div className={styles.gallery}>{photos.map((photo, i) => <button key={photo.id} onClick={event => { opener.current = event.currentTarget; setFailed(false); setIndex(i); }} aria-label={`Ampliar foto ${i + 1} de ${title}`}><PhotoImage photo={photo} alt={`Foto ${i + 1} da proposta ${title}`} /></button>)}</div>
    <Dialog.Root open={index !== null} onOpenChange={open => { if (!open) setIndex(null); }}><Dialog.Portal><Dialog.Overlay className={styles.overlay} /><Dialog.Content className={styles.lightbox} onCloseAutoFocus={event => { event.preventDefault(); opener.current?.focus(); }} onKeyDown={e => {
      if (e.key === "ArrowLeft") { e.preventDefault(); move(-1); } if (e.key === "ArrowRight") { e.preventDefault(); move(1); }
    }}>
      <div className={styles.lightboxHeader}><div><Dialog.Title>{title}</Dialog.Title><Dialog.Description>Foto {(index ?? 0) + 1} de {photos.length}</Dialog.Description></div><Dialog.Close asChild><button aria-label="Fechar foto"><X /></button></Dialog.Close></div>
      {index !== null && (failed ? <p role="alert">Não foi possível carregar a foto. Feche e tente novamente.</p> : <img src={photos[index].url} alt={`Foto ${index + 1} da proposta ${title}`} onError={() => setFailed(true)} />)}
      {photos.length > 1 && <div className={styles.lightboxControls}><button aria-label="Foto anterior" onClick={() => move(-1)}><ChevronLeft />Anterior</button><button aria-label="Próxima foto" onClick={() => move(1)}>Próxima<ChevronRight /></button></div>}
    </Dialog.Content></Dialog.Portal></Dialog.Root>
  </>;
}

function today(timeZone: string) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date());
  return ["year", "month", "day"].map(type => parts.find(p => p.type === type)!.value).join("-");
}

function RecordEditor({ initial, classId, limit, onCancel, onSaved }: {
  initial?: ActivityRecord; classId: string; limit: number; onCancel: () => void; onSaved: (record: ActivityRecord) => void;
}) {
  const settings = usePublicSettings();
  const [saved, setSaved] = useState(initial);
  const [title, setTitle] = useState(initial?.title ?? "");
  const [initialDate] = useState(() => initial?.activityDate ?? today(settings?.timezoneName ?? "America/Sao_Paulo"));
  const [date, setDate] = useState(initialDate);
  const [photos, setPhotos] = useState(initial?.photos ?? []);
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState("");
  const [remote, setRemote] = useState<ActivityRecord | null>(null);
  const leave = useRef(false);
  const client = useQueryClient();
  const dirty = title !== (saved?.title ?? "") || date !== (saved?.activityDate ?? initialDate) || files.length > 0 || photos.length !== (saved?.photos.length ?? 0);
  const blocker = useBlocker(() => dirty && !leave.current);
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", handler); return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);
  const mutation = useMutation({ mutationFn: () => saveRecord(classId, { title, activityDate: date, retainedPhotoIds: photos.map(p => p.id), version: saved?.version }, files, saved?.id),
    onSuccess: r => {
      leave.current = true;
      client.setQueryData(["pedagogical", "record", r.id], r);
      client.invalidateQueries({ queryKey: ["pedagogical", "records"] });
      onSaved(r);
    },
    onError: async e => {
      if (e instanceof ApiError && e.status === 409 && saved) {
        try { setRemote(await getRecord(saved.id)); } catch { setError("Não foi possível consultar a versão atual. Suas alterações continuam aqui."); }
      }
    }
  });
  const add = (selected: File[]) => {
    setError("");
    if (photos.length + files.length + selected.length > 50) return setError("Cada registro pode ter até 50 fotos.");
    if (selected.some(f => !/\.(jpe?g|png|webp)$/i.test(f.name))) return setError("Use fotos JPG, PNG ou WebP.");
    if (selected.some(f => f.size === 0 || f.size > limit * 1024 * 1024)) return setError(`Cada foto deve ter entre 1 byte e ${limit} MiB.`);
    if ([...files, ...selected].reduce((sum, f) => sum + f.size, 0) > 100 * 1024 * 1024) return setError("Envie até 100 MiB de fotos por vez.");
    setFiles([...files, ...selected]);
  };
  return <form className={base.panel} onSubmit={e => { e.preventDefault(); setError(""); if (!title.trim()) return setError("Informe o título da proposta."); if (!photos.length && !files.length) return setError("Adicione ao menos uma foto."); mutation.mutate(); }}>
    <h3>{saved ? "Editar registro" : "Novo registro"}</h3>
    {blocker.state === "blocked" && <div role="alert"><p>Existem alterações não salvas.</p><Button type="button" onClick={() => blocker.reset()}>Continuar editando</Button><Button type="button" onClick={() => blocker.proceed()}>Descartar e sair</Button></div>}
    <fieldset disabled={mutation.isPending}><legend>Dados da proposta</legend>
      <label>Título da proposta<input required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Data da proposta<input required type="date" min="1900-01-01" max="9998-12-31" value={date} onChange={e => setDate(e.target.value)} /></label>
      <label>Fotos da proposta<input type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" multiple onChange={e => { add(Array.from(e.target.files ?? [])); e.target.value = ""; }} aria-describedby="photo-limits" /></label>
      <p id="photo-limits">JPG, PNG ou WebP · até {limit} MiB por foto · 50 fotos por registro · 100 MiB por envio. A primeira foto será a capa.</p>
      <div className={styles.previews}>{photos.map((p, i) => <div key={p.id}><PhotoImage photo={p} alt={`Foto ${i + 1}`} /><Button type="button" onClick={() => setPhotos(photos.filter(photo => photo.id !== p.id))}>Remover foto {i + 1}</Button></div>)}{files.map((file, i) => <div key={i}><LocalPhoto file={file} /><Button type="button" onClick={() => setFiles(files.filter((_, j) => i !== j))}>Remover nova foto {i + 1}</Button></div>)}</div>
    </fieldset>
    {(error || mutation.error) && <p role="alert" className={base.error}>{error || mutation.error?.message}</p>}
    {remote && <div role="alert"><h3>Este registro foi alterado</h3><p>Versão atual: {remote.title} · {dateLabel(remote.activityDate)} · {remote.photos.length} fotos. Seu título, data e arquivos selecionados foram preservados.</p>
      <Button type="button" onClick={() => {
        const removed = new Set(saved?.photos.filter(p => !photos.some(selected => selected.id === p.id)).map(p => p.id));
        setPhotos(remote.photos.filter(p => !removed.has(p.id))); setSaved(remote); setRemote(null); mutation.reset();
      }}>Revisar minhas alterações sobre a versão atual</Button>
      <Button type="button" onClick={() => { if (window.confirm("Descartar suas alterações e carregar a versão salva?")) { setSaved(remote); setTitle(remote.title); setDate(remote.activityDate); setPhotos(remote.photos); setFiles([]); setRemote(null); mutation.reset(); } }}>Carregar versão salva</Button>
    </div>}
    <div className={base.toolbar}><Button type="submit" variant="primary" disabled={mutation.isPending || !!remote}>{mutation.isPending ? "Salvando fotos…" : "Salvar registro"}</Button><Button type="button" disabled={mutation.isPending} onClick={() => { if (!dirty || window.confirm("Descartar as alterações não salvas?")) onCancel(); }}>Cancelar</Button></div>
  </form>;
}

function LocalPhoto({ file }: { file: File }) {
  const [url, setUrl] = useState("");
  useEffect(() => { const objectUrl = URL.createObjectURL(file); setUrl(objectUrl); return () => URL.revokeObjectURL(objectUrl); }, [file]);
  return <img src={url || undefined} alt={`Prévia de ${file.name}`} />;
}
