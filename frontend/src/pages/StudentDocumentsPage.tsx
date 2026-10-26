import { lazy, Suspense, useRef, useState } from "react";
import { Link } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { apiFetch } from "../api/http";
import type { DocumentPage, StudentDocument } from "../api/aee";
import { usePedagogicalScope } from "../hooks/usePedagogicalScope";
import { PedagogicalModules } from "../components/PedagogicalModules";
import { Button } from "../components/Button";
import base from "./PedagogicalPage.module.css";
import styles from "./ActivityRecordsPage.module.css";
const PdfViewer = lazy(() => import("../components/PdfViewer"));

export default function StudentDocumentsPage() {
  const scope = usePedagogicalScope();
  const client = useQueryClient();
  const [selected, setSelected] = useState<StudentDocument | null>(null);
  const [deleting, setDeleting] = useState<StudentDocument | null>(null);
  const [validation, setValidation] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const key = ["pedagogical", scope.key, "documents"];
  const classroom = useQuery({ queryKey: ["pedagogical", scope.key, "context"], queryFn: () => scope.getClass("") });
  const docs = useQuery({ queryKey: key, queryFn: () => apiFetch<DocumentPage>(`${scope.api}/documents`) });
  const upload = useMutation({ mutationFn: (file: File) => { const body = new FormData(); body.append("file", file); return apiFetch<StudentDocument>(`${scope.api}/documents`, { method: "POST", body }); },
    onSuccess: () => { void client.invalidateQueries({ queryKey: key }); if (input.current) input.current.value = ""; } });
  const remove = useMutation({ mutationFn: (d: StudentDocument) => apiFetch<void>(`${scope.api}/documents/${d.id}?version=${d.version}`, { method: "DELETE" }),
    onSuccess: (_, d) => { if (selected?.id === d.id) setSelected(null); setDeleting(null); void client.invalidateQueries({ queryKey: key }); },
    onError: () => { void client.invalidateQueries({ queryKey: key }); } });
  const fileUrl = (d: StudentDocument) => `${scope.api}/documents/${d.id}`;
  return <section className={base.page}>
    <nav aria-label="Diretórios"><Link to={scope.studentBase}>Conteúdos do aluno</Link> / Documentos</nav>
    <h2>Documentos</h2><p>{classroom.data?.name}</p><PedagogicalModules classId={classroom.data?.id ?? ""} />
    {(docs.error || classroom.error) && <p role="alert">{docs.error?.message ?? classroom.error?.message}</p>}
    {docs.isPending && <p role="status">Carregando documentos…</p>}
    {docs.data?.canUpload && <form className={base.panel} onSubmit={e => {
      e.preventDefault(); setValidation(""); const file = input.current?.files?.[0];
      if (!file || !file.name.toLowerCase().endsWith(".pdf")) { setValidation("Selecione um arquivo .pdf."); return; }
      if (file.size === 0 || file.size > docs.data.attachmentLimitMb * 1024 * 1024) { setValidation("Arquivo vazio ou acima do limite permitido."); return; }
      upload.mutate(file);
    }}><label>Anexar PDF (até {docs.data.attachmentLimitMb} MiB)<input ref={input} required type="file" accept=".pdf,application/pdf" /></label><Button type="submit" disabled={upload.isPending}>Anexar documento</Button>
      {(validation || upload.error) && <p role="alert">{validation || upload.error?.message}</p>}
    </form>}
    <div className={base.cards}>{docs.data?.items.map(d => <article className={base.card} key={d.id}>
      <h3>{d.filename}</h3><p>{d.authorName} · {new Date(d.createdAt).toLocaleString("pt-BR")}</p>
      <div className={base.toolbar}><Button onClick={() => setSelected(d)}>Visualizar {d.filename}</Button><a href={`${fileUrl(d)}?download=true`}>Baixar {d.filename}</a>{d.canDelete && <Button variant="danger" onClick={() => { remove.reset(); setDeleting(d); }}>Excluir {d.filename}</Button>}</div>
    </article>)}</div>
    {docs.data?.items.length === 0 && <p>Nenhum documento anexado.</p>}
    {selected && <div className={base.panel}><div className={base.toolbar}><h3>{selected.filename}</h3><Button onClick={() => setSelected(null)}>Fechar visualização</Button></div><Suspense fallback={<p role="status">Carregando visualizador…</p>}><PdfViewer key={selected.id} url={fileUrl(selected)} name={selected.filename} /></Suspense></div>}
    <Dialog.Root open={!!deleting} onOpenChange={open => { if (!open && !remove.isPending) setDeleting(null); }}><Dialog.Portal><Dialog.Overlay className={styles.overlay} /><Dialog.Content className={styles.confirm}>
      <Dialog.Title>Excluir documento?</Dialog.Title><Dialog.Description>O arquivo {deleting?.filename} será removido. Confirme a exclusão.</Dialog.Description>
      {remove.error && <p role="alert">{remove.error.message}</p>}
      <div className={base.toolbar}><Button disabled={remove.isPending} onClick={() => setDeleting(null)}>Cancelar</Button><Button variant="danger" disabled={remove.isPending} onClick={() => deleting && remove.mutate(deleting)}>Confirmar exclusão</Button></div>
    </Dialog.Content></Dialog.Portal></Dialog.Root>
  </section>;
}
