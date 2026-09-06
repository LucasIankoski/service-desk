import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { Navigate } from "react-router";
import { Temporal } from "temporal-polyfill";
import { agendaAssignees, changeAgendaItemStatus, listAgendaItems, listManagers } from "../api/agenda";
import { ApiError } from "../api/http";
import type { AgendaItem } from "../api/types";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { ItemDetails, ItemEditor, priorityLabels, type EditorState } from "../components/AgendaItemDialogs";
import { Button } from "../components/Button";
import { Field, SelectInput, TextInput } from "../components/FormField";
import { defaultAgendaPeriod, periodFieldsFromInstants } from "../components/agendaDateTime";
import { useSession } from "../hooks/useSession";
import { TaskOccurrencesPanel } from "../components/TaskOccurrencesPanel";
import styles from "./TasksPage.module.css";

export default function TasksPage() {
  const session = useSession();
  const settings = usePublicSettings();
  const timeZone = settings?.timezoneName ?? "America/Sao_Paulo";
  const canAccess = !!session.data?.roles.includes("MANAGER");
  const today = Temporal.Now.zonedDateTimeISO(timeZone).toPlainDate();
  const currentMonth = today.toPlainYearMonth().toString();
  const [month, setMonth] = useState(currentMonth);
  const [search, setSearch] = useState("");
  const [priority, setPriority] = useState("");
  const [status, setStatus] = useState("");
  const [assignee, setAssignee] = useState("");
  const [selectedId, setSelectedId] = useState<string>();
  const [editor, setEditor] = useState<EditorState>();
  const queryClient = useQueryClient();
  const monthDate = Temporal.PlainYearMonth.from(month).toPlainDate({ day: 1 });
  const start = monthDate.toZonedDateTime(timeZone).toInstant().toString();
  const end = monthDate.add({ months: 1 }).toZonedDateTime(timeZone).toInstant().toString();
  const agenda = useQuery({ queryKey: ["agenda-items", start, end], queryFn: () => listAgendaItems(start, end), enabled: canAccess });
  const managers = useQuery({ queryKey: ["agenda-managers"], queryFn: listManagers, enabled: canAccess });
  const items = (agenda.data ?? []).filter((item) => item.kind === "INTERNAL_DEMAND");
  const completed = items.filter((item) => item.status === "COMPLETED").length;
  const visible = items.filter((item) => {
    const text = `${item.title} ${item.description ?? ""}`.toLocaleLowerCase("pt-BR");
    return text.includes(search.trim().toLocaleLowerCase("pt-BR"))
      && (!priority || (item.priority ?? "MEDIUM") === priority)
      && (!status || item.status === status)
      && (!assignee || (assignee === "unassigned" ? !agendaAssignees(item).length : agendaAssignees(item).some((person) => person.id === assignee)));
  }).sort((a, b) => Temporal.Instant.compare(a.startAt, b.startAt) || a.title.localeCompare(b.title, "pt-BR"));
  const assignees = new Map((managers.data ?? []).map((person) => [person.id, person.displayName]));
  items.forEach((item) => agendaAssignees(item).forEach((person) => assignees.set(person.id, person.displayName)));
  function refresh() { return queryClient.invalidateQueries({ queryKey: ["agenda-items"] }); }
  const changeStatus = useMutation({
    mutationFn: (item: AgendaItem) => changeAgendaItemStatus(item.id, item.status === "COMPLETED" ? "PENDING" : "COMPLETED", item.version),
    onSuccess: refresh,
    onError: (error) => { if (error instanceof ApiError && error.status === 409) refresh(); }
  });
  function edit(item: AgendaItem) {
    setSelectedId(undefined);
    setEditor({ item, period: periodFieldsFromInstants(item.startAt, item.endAt, item.allDay, timeZone) });
  }
  function create() {
    const date = month === currentMonth ? today.toString() : monthDate.toString();
    setEditor({ period: { ...defaultAgendaPeriod(timeZone), startDate: date, endDate: date } });
  }
  function moveMonth(amount: number) { setMonth(monthDate.add({ months: amount }).toPlainYearMonth().toString()); }
  if (!canAccess) return <Navigate to="/tickets" replace />;
  const count = (value: number) => agenda.data ? value : "—";
  return <section className={styles.page}>
    <header className={styles.heading}>
      <div><span>Planejamento institucional</span><h2>Tarefas</h2><p>Organize as demandas internas e acompanhe o andamento do mês.</p></div>
      <Button variant="primary" icon={<Plus />} onClick={create}>Nova tarefa</Button>
    </header>
    <div className={styles.monthBar} aria-label="Período das tarefas">
      <Button icon={<ChevronLeft />} aria-label="Mês anterior" onClick={() => moveMonth(-1)} />
      <Field label="Mês e ano"><TextInput type="month" value={month} onChange={(event) => {
        if (/^\d{4}-\d{2}$/.test(event.target.value)) setMonth(event.target.value);
      }} /></Field>
      <Button icon={<ChevronRight />} aria-label="Próximo mês" onClick={() => moveMonth(1)} />
      <Button onClick={() => setMonth(currentMonth)}>Mês atual</Button>
    </div>
    <section aria-label="Totais do mês" className={styles.summary}>
      <article><span>Tarefas registradas</span><strong>{count(items.length)}</strong><small>Total do mês</small></article>
      <article className={styles.completed}><span>Concluídas</span><strong>{count(completed)}</strong><small>Total do mês</small></article>
      <article className={styles.pending}><span>Pendentes</span><strong>{count(items.length - completed)}</strong><small>Total do mês</small></article>
    </section>
    <div className={styles.filters} aria-label="Filtros das tarefas">
      <Field label="Buscar tarefa"><TextInput type="search" placeholder="Título ou observações" value={search} onChange={(event) => setSearch(event.target.value)} /></Field>
      <Field label="Prioridade"><SelectInput value={priority} onChange={(event) => setPriority(event.target.value)}><option value="">Todas</option>{Object.entries(priorityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</SelectInput></Field>
      <Field label="Status"><SelectInput value={status} onChange={(event) => setStatus(event.target.value)}><option value="">Todos</option><option value="PENDING">Pendente</option><option value="COMPLETED">Concluída</option></SelectInput></Field>
      <Field label="Responsável"><SelectInput value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="">Todos</option><option value="unassigned">Sem responsável</option>{[...assignees].sort((a, b) => a[1].localeCompare(b[1], "pt-BR")).map(([id, name]) => <option key={id} value={id}>{name}</option>)}</SelectInput></Field>
      <Button onClick={() => { setSearch(""); setPriority(""); setStatus(""); setAssignee(""); }}>Limpar filtros</Button>
    </div>
    {changeStatus.error ? <p role="alert" className={styles.error}>{changeStatus.error.message}</p> : null}
    {agenda.error ? <div role="alert" className={styles.error}>{agenda.error.message} <Button onClick={() => agenda.refetch()}>Tentar novamente</Button></div> : null}
    <div className={styles.dailyWorkspace}>
    <div className={styles.tablePanel}>
      <div className={styles.tableHeading}><strong>Demandas do mês</strong><span role="status">{agenda.isFetching ? "Carregando tarefas…" : `${visible.length} de ${items.length} tarefas`}</span></div>
      <div className={styles.tableScroll} role="region" aria-label="Tabela de tarefas" tabIndex={0} aria-busy={agenda.isFetching}>
        <table>
          <thead><tr>{["Data", "Dia da semana", "Tarefa", "Prioridade", "Status", "Horário", "Responsáveis", "Observações", "Ações"].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
          <tbody>{visible.map((item) => {
            const startDate = new Date(item.startAt);
            const fields = periodFieldsFromInstants(item.startAt, item.endAt, item.allDay, timeZone);
            const multiDay = fields.startDate !== fields.endDate;
            const date = new Intl.DateTimeFormat("pt-BR", { timeZone, dateStyle: "short" }).format(startDate);
            const time = item.allDay ? "Dia inteiro" : new Intl.DateTimeFormat("pt-BR", { timeZone, hour: "2-digit", minute: "2-digit" });
            return <tr key={item.id}>
              <td>{date}{multiDay ? <> a {fields.endDate.split("-").reverse().join("/")}</> : null}</td>
              <td>{new Intl.DateTimeFormat("pt-BR", { timeZone, weekday: "long" }).format(startDate)}</td>
              <td><button className={styles.titleButton} onClick={() => setSelectedId(item.id)}>{item.title}</button></td>
              <td><span className={`${styles.badge} ${styles[item.priority ?? "MEDIUM"]}`}>{priorityLabels[item.priority ?? "MEDIUM"]}</span></td>
              <td><button className={`${styles.badge} ${item.status === "COMPLETED" ? styles.completed : styles.pending}`} disabled={changeStatus.isPending} aria-label={`${item.status === "COMPLETED" ? "Reabrir" : "Concluir"} ${item.title}`} onClick={() => changeStatus.mutate(item)}>{item.status === "COMPLETED" ? "Concluída" : "Pendente"}</button></td>
              <td>{typeof time === "string" ? time : `${time.format(startDate)} – ${time.format(new Date(item.endAt))}`}</td>
              <td><div className={styles.assigneeNames}>{agendaAssignees(item).length ? agendaAssignees(item).map((person) => <span key={person.id}>{person.displayName}</span>) : "Sem responsável"}</div></td>
              <td className={styles.observations}>{item.description || "—"}</td>
              <td><Button icon={<Pencil />} aria-label={`Editar ${item.title}`} onClick={() => edit(item)} /></td>
            </tr>;
          })}</tbody>
        </table>
        {!agenda.isLoading && !agenda.error && !visible.length ? <p className={styles.empty}>{items.length ? "Nenhuma tarefa corresponde aos filtros." : "Nenhuma tarefa neste mês. Crie uma tarefa para começar."}</p> : null}
      </div>
    </div>
    <TaskOccurrencesPanel key={month} month={month} timeZone={timeZone} />
    </div>
    <ItemDetails key={selectedId ?? "closed"} item={items.find((item) => item.id === selectedId)} isManager timeZone={timeZone} onClose={() => setSelectedId(undefined)} onEdit={edit} onChanged={refresh} onDeleted={() => { refresh(); setSelectedId(undefined); }} />
    {editor ? <ItemEditor taskMode state={editor} managers={managers.data ?? []} timeZone={timeZone} onClose={() => setEditor(undefined)} onSaved={() => { refresh(); setEditor(undefined); }} /> : null}
  </section>;
}
