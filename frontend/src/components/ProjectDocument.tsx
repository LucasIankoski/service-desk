import { usePublicSettings } from "../app/PublicSettingsContext";
import { dateLabel } from "../api/pedagogical";
import type { Project } from "../api/projects";
import { RichTextView } from "./ProjectRichText";
import styles from "../pages/ProjectPage.module.css";

export function ProjectDocument({ project, unsaved = false }: { project: Project; unsaved?: boolean }) {
  const settings = usePublicSettings();
  const shortDate = (date: string) => dateLabel(date).slice(0, 5);
  const periodDate = project.startDate.slice(0, 4) === project.endDate.slice(0, 4) ? shortDate : dateLabel;
  return <article data-project-document className={styles.document}>
    <header className={styles.documentHeader}>
      {settings?.schoolLogoUrl && <img src={settings.schoolLogoUrl} alt="Logotipo da escola" />}
      <h1>{project.title}</h1>
    </header>
    {(project.status === "DRAFT" || unsaved) && <p className={styles.draftMark}>RASCUNHO{unsaved ? " · ALTERAÇÕES NÃO SALVAS" : ""}</p>}
    <div className={styles.metadata}>PERÍODO: {periodDate(project.startDate)} À {periodDate(project.endDate)}<br />FAIXA ETÁRIA: {project.ageRange.toLocaleUpperCase("pt-BR")}</div>
    <section className={styles.general}><h2>OBJETIVO GERAL:</h2><RichTextView value={project.generalObjective} /></section>
    <section><h2>OBJETIVOS ESPECÍFICOS:</h2><ul>{project.specificObjectives.map(s => s.trim()).filter(Boolean).map((objective, i) => <li key={i}>{objective}</li>)}</ul></section>
    <section><h2>DESENVOLVIMENTO:</h2>
      {[...project.activities].sort((a, b) => (a.date ?? "9999").localeCompare(b.date ?? "9999")).map((activity, i) => <section className={styles.activity} key={i}>
        {activity.title && <h3>{activity.date ? `${periodDate(activity.date)}: ` : ""}{activity.title.toLocaleUpperCase("pt-BR")}</h3>}<RichTextView value={activity.description} />
      </section>)}
    </section>
    <section><h2>CONCLUSÃO:</h2><RichTextView value={project.conclusion} /></section>
  </article>;
}
