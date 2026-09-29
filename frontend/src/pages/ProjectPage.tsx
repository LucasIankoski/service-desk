import { useEffect, useState } from "react";
import { Link, useBlocker, useNavigate, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Printer, Save } from "lucide-react";
import { DeletePedagogicalDocument } from "../components/DeletePedagogicalDocument";
import { deleteProject } from "../api/projects";
import { Button } from "../components/Button";
import { ProjectRichText } from "../components/ProjectRichText";
import { ProjectDocument } from "../components/ProjectDocument";
import { getClass } from "../api/pedagogical";
import type { SchoolClass } from "../api/pedagogical";
import { getProject, projectDevelopment, projectInput, saveProject, transitionProject } from "../api/projects";
import type { Project } from "../api/projects";
import { ApiError } from "../api/http";
import base from "./PedagogicalPage.module.css";
import styles from "./ProjectPage.module.css";

export default function ProjectPage() {
  const { projectId } = useParams();
  const project = useQuery({ queryKey: ["pedagogical", "project", projectId], queryFn: () => getProject(projectId!) });
  const classroom = useQuery({ queryKey: ["pedagogical", "class", project.data?.classId], queryFn: () => getClass(project.data!.classId), enabled: !!project.data });
  if (project.error || classroom.error) return <p role="alert">{project.error?.message ?? classroom.error?.message}</p>;
  if (!project.data || !classroom.data) return <p role="status">Carregando projeto…</p>;
  return <Editor key={project.data.id} initial={project.data} classroom={classroom.data} />;
}
function Editor({ initial, classroom }: { initial: Project; classroom: SchoolClass }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [deleted, setDeleted] = useState(false);
  const [saved, setSaved] = useState(initial);
  const [draft, setDraft] = useState(() => projectInput(initial));
  const [preview, setPreview] = useState(false);
  const [conflict, setConflict] = useState<Project | null>(null);
  const [message, setMessage] = useState("");
  const dirty = JSON.stringify(draft) !== JSON.stringify(projectInput(saved));
  const blocker = useBlocker(dirty && !deleted);
  useEffect(() => {
    if (!deleted) return;
    void client.invalidateQueries({ queryKey: ["pedagogical", "projects"] });
    void client.invalidateQueries({ queryKey: ["pedagogical", "project", saved.id], refetchType: "none" });
    void navigate(`/pedagogico/turmas/${saved.classId}/projetos`, { replace: true });
  }, [deleted, client, navigate, saved.classId, saved.id]);
  const locked = saved.status === "FINALIZED" || classroom.archived;
  useEffect(() => {
    if (!dirty || deleted) return;
    const leave = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", leave);
    return () => window.removeEventListener("beforeunload", leave);
  }, [dirty, deleted]);
  const accept = (p: Project) => {
    setSaved(p); setDraft(projectInput(p)); setConflict(null);
    client.setQueryData(["pedagogical", "project", p.id], p);
    client.invalidateQueries({ queryKey: ["pedagogical", "projects"] });
  };
  const mutation = useMutation({
    mutationFn: async (action: "save" | "finalize" | "reopen") => {
      setMessage("");
      let p = saved;
      if (action !== "reopen") { p = await saveProject(saved.id, draft); accept(p); }
      if (action !== "save") p = await transitionProject(saved.id, p.version, action);
      return p;
    },
    onSuccess: (p, action) => {
      if (action === "reopen" && dirty) {
        setSaved(p); setDraft(d => ({ ...d, version: p.version })); setConflict(null);
        client.setQueryData(["pedagogical", "project", p.id], p);
        client.invalidateQueries({ queryKey: ["pedagogical", "projects"] });
        setMessage("Projeto reaberto. Seu texto local foi preservado e ainda precisa ser salvo.");
      } else {
        accept(p); setMessage(p.status === "FINALIZED" ? "Projeto finalizado." : "Rascunho salvo.");
      }
    },
    onError: async error => {
      if (error instanceof ApiError && error.status === 409) {
        try { setConflict(await getProject(saved.id)); } catch { /* Keep the original error and local input. */ }
      }
    }
  });
  const options = [...new Map([...classroom.teachers, ...saved.teachers].map(t => [t.id, t])).values()];
  const displayProject: Project = { ...saved, ...draft, teachers: options.filter(t => draft.teacherIds.includes(t.id)) };
  return <section className={base.page}>
    <div className={styles.screenOnly}>
      <nav className={base.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={`/pedagogico/turmas/${saved.classId}`}>{classroom.name}</Link><span>/</span><Link to={`/pedagogico/turmas/${saved.classId}/projetos`}>Projetos</Link></nav>
      <header className={base.heading}><div><h2>Projeto pedagógico</h2><p>{classroom.name} · {saved.title}</p></div><span className={base.status}>{saved.status === "DRAFT" ? "Rascunho" : "Finalizado"}</span></header>
      {classroom.archived && <p>Turma arquivada. Disponível somente para consulta e impressão.</p>}
      {blocker.state === "blocked" && <div className={base.panel} role="alert"><p>Existem alterações não salvas. Deseja sair e descartá-las?</p><Button onClick={() => blocker.reset()}>Continuar editando</Button><Button variant="danger" onClick={() => blocker.proceed()}>Descartar e sair</Button></div>}
      <div className={base.toolbar}>
        {!locked && <><Button icon={<Save />} disabled={mutation.isPending || !!conflict} onClick={() => mutation.mutate("save")}>Salvar rascunho</Button><Button disabled={mutation.isPending || !!conflict} onClick={() => mutation.mutate("finalize")}>Finalizar</Button></>}
        {saved.status === "FINALIZED" && !classroom.archived && <Button disabled={mutation.isPending || !!conflict} onClick={() => mutation.mutate("reopen")}>Reabrir</Button>}
        <Button onClick={() => setPreview(!preview)}>{preview ? "Voltar ao formulário" : "Visualizar / Imprimir"}</Button>
        {preview && <Button icon={<Printer />} onClick={() => window.print()}>Imprimir / Salvar PDF</Button>}
        {!classroom.archived && <DeletePedagogicalDocument kind="projeto" label={saved.title} dirty={dirty}
          disabled={mutation.isPending || !!conflict} remove={() => deleteProject(saved.id, saved.version)}
          onDeleted={() => setDeleted(true)} onConflict={async () => {
            try { setConflict(await getProject(saved.id)); } catch { /* Keep local input and the deletion error visible. */ }
          }} />}
        {dirty && <span>Alterações não salvas</span>}
      </div>
      {message && <p role="status">{message}</p>}
      {mutation.error && <p role="alert" className={base.error}>{mutation.error.message}</p>}
      {conflict && <div className={base.panel}>
        <h3>Outra pessoa alterou este projeto</h3><p>Seu preenchimento continua no formulário. Compare a versão atual antes de decidir.</p>
        <details><summary>Consultar versão salva no sistema</summary><div className={styles.preview}><ProjectDocument project={conflict} /></div></details>
        <Button onClick={() => { setSaved(conflict); setDraft(d => ({ ...d, version: conflict.version })); setConflict(null); mutation.reset(); }}>Manter meu texto para revisão</Button>
        <Button onClick={() => { accept(conflict); mutation.reset(); }}>Carregar versão salva e descartar meu texto</Button>
      </div>}
      {preview && <p>Para salvar o PDF, selecione A4, escala 100% e desative os cabeçalhos e rodapés do navegador.{dirty && " Prévia com alterações locais ainda não salvas."}</p>}
      {!preview && <div className={base.panel}><fieldset disabled={locked || mutation.isPending}><legend>Preenchimento do projeto</legend>
        <label>Título<input maxLength={300} value={draft.title} onChange={e => setDraft({ ...draft, title: e.target.value })} /></label>
        <div className={base.dayFields}>
          <label>Data inicial<input type="date" min="1900-01-01" max="9998-12-31" value={draft.startDate} onChange={e => setDraft({ ...draft, startDate: e.target.value })} /></label>
          <label>Data final<input type="date" min={draft.startDate || "1900-01-01"} max="9998-12-31" value={draft.endDate} onChange={e => setDraft({ ...draft, endDate: e.target.value })} /></label>
        </div>
        <label>Faixa etária<input maxLength={200} placeholder="Crianças de 0 a 6 anos" value={draft.ageRange} onChange={e => setDraft({ ...draft, ageRange: e.target.value })} /></label>
        <fieldset><legend>Professoras responsáveis</legend>{options.map(t => <label key={t.id} className={base.check}><input type="checkbox" checked={draft.teacherIds.includes(t.id)} onChange={e => setDraft({ ...draft, teacherIds: e.target.checked ? [...draft.teacherIds, t.id] : draft.teacherIds.filter(id => id !== t.id) })} />{t.displayName}{!classroom.teachers.some(current => current.id === t.id) && " (registro histórico)"}</label>)}</fieldset>
        <ProjectRichText label="Objetivo geral" value={draft.generalObjective} onChange={generalObjective => setDraft({ ...draft, generalObjective })} />
        <label>Objetivos específicos<textarea aria-label="Objetivos específicos" rows={7} maxLength={200000}
          placeholder="Escreva um objetivo por linha."
          value={draft.specificObjectives.join("\n")} onChange={e => setDraft({ ...draft, specificObjectives: e.target.value.split("\n") })} /></label>
        <ProjectRichText label="Desenvolvimento" rows={14} value={projectDevelopment(draft)}
          onChange={description => setDraft({ ...draft, activities: [{ date: null, title: "", description }] })} />
        <ProjectRichText label="Conclusão" value={draft.conclusion} onChange={conclusion => setDraft({ ...draft, conclusion })} />
      </fieldset></div>}
    </div>
    <div className={preview ? styles.preview : styles.printOnly}><ProjectDocument project={displayProject} unsaved={dirty} /></div>
  </section>;
}
