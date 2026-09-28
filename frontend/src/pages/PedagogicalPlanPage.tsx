import { useEffect, useState } from "react";
import { Link, useBlocker, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Printer, Save } from "lucide-react";
import { Button } from "../components/Button";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { getPlan, getClass, savePlan, transitionPlan, dateLabel } from "../api/pedagogical";
import type { Planning, PlanningInput, PlanningDay, SchoolClass } from "../api/pedagogical";
import { ApiError } from "../api/http";
import styles from "./PedagogicalPage.module.css";

export default function PedagogicalPlanPage() {
  const { planId } = useParams();
  const plan = useQuery({ queryKey: ["pedagogical", "plan", planId], queryFn: () => getPlan(planId!) });
  const classroom = useQuery({ queryKey: ["pedagogical", "class", plan.data?.classId], queryFn: () => getClass(plan.data!.classId), enabled: !!plan.data });
  if (plan.error) return <p role="alert">{plan.error.message}</p>;
  if (classroom.error) return <p role="alert">{classroom.error.message}</p>;
  if (!plan.data || !classroom.data) return <p role="status">Carregando planejamento…</p>;
  return <Editor key={plan.data.id} initial={plan.data} classroom={classroom.data} />;
}

function inputOf(p: Planning): PlanningInput {
  return { version: p.version, theme: p.theme ?? "", teacherIds: p.teachers.map(t => t.id), days: p.days.map(d => ({ ...d })) };
}

function Editor({ initial, classroom }: { initial: Planning; classroom: SchoolClass }) {
  const settings = usePublicSettings();
  const client = useQueryClient();
  const [saved, setSaved] = useState(initial);
  const [draft, setDraft] = useState(() => inputOf(initial));
  const [preview, setPreview] = useState(false);
  const [conflict, setConflict] = useState<Planning | null>(null);
  const [message, setMessage] = useState("");
  const dirty = JSON.stringify(draft) !== JSON.stringify(inputOf(saved));
  const blocker = useBlocker(dirty);
  const locked = saved.status === "FINALIZED" || classroom.archived;
  useEffect(() => {
    if (!dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [dirty]);
  const accept = (p: Planning) => {
    setSaved(p); setDraft(inputOf(p)); setConflict(null);
    client.setQueryData(["pedagogical", "plan", p.id], p);
    client.invalidateQueries({ queryKey: ["pedagogical", "plans"] });
  };
  const mutation = useMutation({
    mutationFn: async (action: "save" | "finalize" | "reopen") => {
      setMessage("");
      let p = saved;
      if (action !== "reopen") {
        p = await savePlan(saved.id, draft);
        accept(p);
      }
      if (action !== "save") p = await transitionPlan(saved.id, p.version, action);
      return p;
    },
    onSuccess: p => { accept(p); setMessage(p.status === "FINALIZED" ? "Planejamento finalizado." : "Rascunho salvo."); },
    onError: async error => {
      if (error instanceof ApiError && error.status === 409) {
        try { setConflict(await getPlan(saved.id)); } catch { /* Original error remains visible. */ }
      }
    }
  });
  const options = [...new Map([...classroom.teachers, ...saved.teachers].map(t => [t.id, t])).values()];
  const updateDay = (index: number, changes: Partial<PlanningDay>) => setDraft({ ...draft, days: draft.days.map((day, i) => i === index ? { ...day, ...changes } : day) });
  const displayPlan: Planning = { ...saved, theme: draft.theme, days: draft.days, teachers: options.filter(t => draft.teacherIds.includes(t.id)) };
  return <section className={styles.page}>
    <div className={styles.screenOnly}>
      <nav className={styles.breadcrumb} aria-label="Diretórios"><Link to="/pedagogico">Turmas</Link><span>/</span><Link to={"/pedagogico/turmas/" + saved.classId}>{classroom.name}</Link><span>/</span><span>{dateLabel(saved.weekStart)} a {dateLabel(saved.weekEnd)}</span></nav>
      <header className={styles.heading}><div><h2>Planejamento Pedagógico</h2><p>{classroom.name} · {dateLabel(saved.weekStart)} a {dateLabel(saved.weekEnd)}</p></div><span className={styles.status}>{saved.status === "DRAFT" ? "Rascunho" : "Finalizado"}</span></header>
      {classroom.archived && <p>Turma arquivada. Disponível somente para consulta e impressão.</p>}
      {blocker.state === "blocked" && <div className={styles.panel} role="alert"><p>Existem alterações não salvas. Deseja sair e descartá-las?</p><Button onClick={() => blocker.reset()}>Continuar editando</Button><Button variant="danger" onClick={() => blocker.proceed()}>Descartar e sair</Button></div>}
      <div className={styles.toolbar}>
        {!locked && <><Button icon={<Save />} variant="primary" disabled={mutation.isPending || !!conflict} onClick={() => mutation.mutate("save")}>Salvar rascunho</Button><Button disabled={mutation.isPending || !!conflict} onClick={() => mutation.mutate("finalize")}>Finalizar</Button></>}
        {saved.status === "FINALIZED" && !classroom.archived && <Button disabled={mutation.isPending} onClick={() => mutation.mutate("reopen")}>Reabrir</Button>}
        <Button onClick={() => setPreview(!preview)}>{preview ? "Voltar ao formulário" : "Visualizar / Imprimir"}</Button>
        {preview && <Button icon={<Printer />} onClick={() => window.print()}>Imprimir / Salvar PDF</Button>}
        {dirty && <span>Alterações não salvas</span>}
      </div>
      {message && <p role="status">{message}</p>}
      {mutation.error && <p className={styles.error} role="alert">{mutation.error.message}</p>}
      {conflict && <div className={styles.panel}>
        <h3>Outra pessoa alterou este planejamento</h3><p>Seu preenchimento continua no formulário. Compare a versão atual antes de decidir.</p>
        <details><summary>Consultar versão salva no sistema</summary><PlanDocument plan={conflict} /></details>
        <Button onClick={() => { setSaved(conflict); setDraft(d => ({ ...d, version: conflict.version })); setConflict(null); }}>Manter meu texto para revisão</Button>
        <Button onClick={() => accept(conflict)}>Carregar versão salva e descartar meu texto</Button>
      </div>}
      {!preview && <div className={styles.panel}>
        <fieldset disabled={locked || mutation.isPending}><legend>Dados da semana</legend>
          <label>Tema / Projeto / Experiência da semana<textarea aria-label="Tema / Projeto / Experiência da semana" maxLength={1000} value={draft.theme} onChange={e => setDraft({ ...draft, theme: e.target.value })} /></label>
          <fieldset><legend>Professoras responsáveis</legend>{options.map(t => <label className={styles.check} key={t.id}><input type="checkbox" checked={draft.teacherIds.includes(t.id)} onChange={e => setDraft({ ...draft, teacherIds: e.target.checked ? [...draft.teacherIds, t.id] : draft.teacherIds.filter(id => id !== t.id) })} />{t.displayName}{!classroom.teachers.some(current => current.id === t.id) && " (registro histórico)"}</label>)}</fieldset>
          {draft.days.map((day, i) => <fieldset key={day.date} className={styles.day}><legend>{new Date(day.date + "T12:00:00").toLocaleDateString("pt-BR", { weekday: "long" })} · {dateLabel(day.date)}</legend>
            <label className={styles.check}><input type="checkbox" checked={day.noClass} onChange={e => updateDay(i, { noClass: e.target.checked })} />Sem aula</label>
            {day.noClass ? <label>Motivo<textarea aria-label="Motivo" maxLength={4000} value={day.reason ?? ""} onChange={e => updateDay(i, { reason: e.target.value })} /></label> :
              <div className={styles.dayFields}>{(["proposal", "objectives", "development", "resources"] as const).map((key, n) => <label key={key}>{["Proposta", "Objetivos", "Desenvolvimento", "Recursos"][n]}<textarea aria-label={["Proposta", "Objetivos", "Desenvolvimento", "Recursos"][n]} rows={5} maxLength={10000} value={day[key] ?? ""} onChange={e => updateDay(i, { [key]: e.target.value })} /></label>)}</div>}
          </fieldset>)}
        </fieldset>
      </div>}
    </div>
    <div className={preview ? styles.preview : styles.printOnly}>
      {preview && dirty && <p className={styles.screenOnly}>Prévia com alterações locais ainda não salvas.</p>}
      <PlanDocument plan={displayPlan} unsaved={dirty} institution={settings?.institutionName} logo={settings?.schoolLogoUrl} />
    </div>
  </section>;
}

function PlanDocument({ plan, unsaved = false, institution, logo }: { plan: Planning; unsaved?: boolean; institution?: string; logo?: string | null }) {
  const settings = usePublicSettings();
  const source = logo ?? settings?.schoolLogoUrl;
  return <article data-pedagogical-document className={styles.document}>
    <header className={styles.documentHeader}>
      <div><strong>TURMA: {plan.className}</strong><p>PROFESSORAS: {plan.teachers.map(t => t.displayName).join(", ") || "Não informadas"}</p></div>
      <div><h2>{institution ?? settings?.institutionName ?? "Escola"}</h2><h3>PLANEJAMENTO PEDAGÓGICO</h3><p>{dateLabel(plan.weekStart)} a {dateLabel(plan.weekEnd)}</p></div>
      {source && <img src={source} alt="Logotipo da escola" />}
    </header>
    {(plan.status === "DRAFT" || unsaved) && <p className={styles.draftMark}>RASCUNHO{unsaved ? " · ALTERAÇÕES NÃO SALVAS" : ""}</p>}
    <h3 className={styles.theme}>TEMA / PROJETO / EXPERIÊNCIA DA SEMANA: {plan.theme}</h3>
    <table><colgroup><col style={{ width: "7%" }} /><col style={{ width: "15%" }} /><col style={{ width: "20%" }} /><col style={{ width: "38%" }} /><col style={{ width: "20%" }} /></colgroup>
      <thead><tr>{["DATA", "PROPOSTA", "OBJETIVOS", "DESENVOLVIMENTO", "RECURSOS"].map(label => <th key={label}>{label}</th>)}</tr></thead>
      <tbody>{plan.days.map(day => <tr key={day.date}><th scope="row">{dateLabel(day.date).slice(0, 5)}</th>{day.noClass ? <td colSpan={4}><strong>Sem aula</strong> — {day.reason}</td> : <><td>{day.proposal}</td><td>{day.objectives}</td><td>{day.development}</td><td>{day.resources}</td></>}</tr>)}</tbody>
    </table>
  </article>;
}
