import { useEffect, useRef, useState } from "react";
import { useBlocker } from "react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { Button } from "./Button";
import { dateLabel } from "../api/pedagogical";
import type { PhotoRecord, ActivityPhoto, RecordInput } from "../api/records";
import { ApiError } from "../api/http";
import { usePublicSettings } from "../app/PublicSettingsContext";
import base from "../pages/PedagogicalPage.module.css";
import styles from "../pages/ActivityRecordsPage.module.css";

export function PhotoImage({ photo, alt }: { photo: ActivityPhoto; alt: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <span className={styles.unavailable}>Foto indisponível</span> : <img src={photo.thumbnailUrl} alt={alt} loading="lazy" width={photo.width} height={photo.height} onError={() => setFailed(true)} />;
}

export function Gallery({ photos, title }: { photos: ActivityPhoto[]; title: string }) {
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

export function RecordEditor<T extends PhotoRecord>({ initial, limit, maxPhotos = 50, subject = "proposta", namespace, scopeKey, save, load, onCancel, onSaved }: {
  initial?: T; limit: number; maxPhotos?: number; subject?: "proposta" | "evento"; namespace: string; scopeKey?: string;
  save: (input: RecordInput, files: File[], id?: string) => Promise<T>; load: (id: string) => Promise<T>;
  onCancel: () => void; onSaved: (record: T) => void;
}) {
  const settings = usePublicSettings();
  const [saved, setSaved] = useState(initial);
  const [title, setTitle] = useState(initial?.title ?? "");
  const [initialDate] = useState(() => initial?.activityDate ?? today(settings?.timezoneName ?? "America/Sao_Paulo"));
  const [date, setDate] = useState(initialDate);
  const [photos, setPhotos] = useState(initial?.photos ?? []);
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState("");
  const [remote, setRemote] = useState<T | null>(null);
  const leave = useRef(false);
  const client = useQueryClient();
  const dirty = title !== (saved?.title ?? "") || date !== (saved?.activityDate ?? initialDate) || files.length > 0 || photos.length !== (saved?.photos.length ?? 0);
  const blocker = useBlocker(() => dirty && !leave.current);
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", handler); return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);
  const mutation = useMutation({ mutationFn: () => save({ title, activityDate: date, retainedPhotoIds: photos.map(p => p.id), version: saved?.version }, files, saved?.id),
    onSuccess: r => {
      leave.current = true;
      const prefix = scopeKey ? [namespace, scopeKey] : [namespace];
      client.setQueryData([...prefix, "record", r.id], r);
      client.invalidateQueries({ queryKey: [...prefix, "records"] });
      onSaved(r);
    },
    onError: async e => {
      if (e instanceof ApiError && e.status === 409 && saved) {
        try { setRemote(await load(saved.id)); } catch { setError("Não foi possível consultar a versão atual. Suas alterações continuam aqui."); }
      }
    }
  });
  const add = (selected: File[]) => {
    setError("");
    if (photos.length + files.length + selected.length > maxPhotos) return setError(`Cada registro pode ter até ${maxPhotos} fotos.`);
    if (selected.some(f => !/\.(jpe?g|png|webp)$/i.test(f.name))) return setError("Use fotos JPG, PNG ou WebP.");
    if (selected.some(f => f.size === 0 || f.size > limit * 1024 * 1024)) return setError(`Cada foto deve ter entre 1 byte e ${limit} MiB.`);
    if ([...files, ...selected].reduce((sum, f) => sum + f.size, 0) > 100 * 1024 * 1024) return setError("Envie até 100 MiB de fotos por vez.");
    setFiles([...files, ...selected]);
  };
  return <form className={base.panel} onSubmit={e => { e.preventDefault(); setError(""); if (!title.trim()) return setError(`Informe o título ${subject === "evento" ? "do evento" : "da proposta"}.`); if (!photos.length && !files.length) return setError("Adicione ao menos uma foto."); mutation.mutate(); }}>
    <h3>{saved ? "Editar registro" : "Novo registro"}</h3>
    {blocker.state === "blocked" && <div role="alert"><p>Existem alterações não salvas.</p><Button type="button" onClick={() => blocker.reset()}>Continuar editando</Button><Button type="button" onClick={() => blocker.proceed()}>Descartar e sair</Button></div>}
    <fieldset disabled={mutation.isPending}><legend>Dados {subject === "evento" ? "do evento" : "da proposta"}</legend>
      <label>Título {subject === "evento" ? "do evento" : "da proposta"}<input required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Data {subject === "evento" ? "do evento" : "da proposta"}<input required type="date" min="1900-01-01" max="9998-12-31" value={date} onChange={e => setDate(e.target.value)} /></label>
      <label>Fotos {subject === "evento" ? "do evento" : "da proposta"}<input type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" multiple onChange={e => { add(Array.from(e.target.files ?? [])); e.target.value = ""; }} aria-describedby="photo-limits" /></label>
      <p id="photo-limits">JPG, PNG ou WebP · até {limit} MiB por foto · {maxPhotos} fotos por registro · 100 MiB por envio. A primeira foto será a capa.</p>
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
