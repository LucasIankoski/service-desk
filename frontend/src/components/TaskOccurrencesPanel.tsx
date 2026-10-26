import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, NotebookPen, Pencil, Plus, Trash2, X } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Temporal } from "temporal-polyfill";
import { createAgendaOccurrence, deleteAgendaOccurrence, listAgendaOccurrences, updateAgendaOccurrence, createAgendaCommitment, deleteAgendaCommitment, listAgendaCommitments, updateAgendaCommitment, listCommitmentCollaborators } from "../api/agenda";
import { ApiError } from "../api/http";
import type { AgendaOccurrence } from "../api/types";
import { Button } from "./Button";
import { Field, SelectInput, TextArea, TextInput } from "./FormField";
import dialogStyles from "./AgendaItemDialogs.module.css";
import styles from "./TaskOccurrencesPanel.module.css";

type NoteKind = "occurrences" | "commitments";
const noteTypes = {
  occurrences: { title: "Ocorrências do dia", dateLabel: "Data das ocorrências", plural: "ocorrências", singular: "ocorrência", itemLabel: "Ocorrência", create: "Nova ocorrência", save: "Salvar ocorrência", empty: "Nenhuma ocorrência neste dia.", intro: "Registre os acontecimentos do dia, mesmo quando não houver tarefas.", placeholder: "O que aconteceu neste dia?", confirmation: "Excluir esta ocorrência? Esta ação não pode ser desfeita.", list: listAgendaOccurrences, add: createAgendaOccurrence, update: updateAgendaOccurrence, remove: deleteAgendaOccurrence },
  commitments: { title: "Compromissos", dateLabel: "Data dos compromissos", plural: "compromissos", singular: "compromisso", itemLabel: "Compromisso", create: "Novo compromisso", save: "Salvar compromisso", empty: "Nenhum compromisso neste dia.", intro: "Registre compromissos e ausências dos colaboradores.", placeholder: "Ex.: vai ao médico e estará ausente.", confirmation: "Excluir este compromisso? Esta ação não pode ser desfeita.", list: listAgendaCommitments, add: createAgendaCommitment, update: updateAgendaCommitment, remove: deleteAgendaCommitment }
};

export function TaskDailyNotesPanel({ month, timeZone, kind }: { month: string; timeZone: string; kind: NoteKind }) {
  const config = noteTypes[kind];
  const queryKey = ["agenda-" + kind];
  const contentId = "daily-" + kind + "-content";
  const client = useQueryClient();
  const start = Temporal.PlainYearMonth.from(month).toPlainDate({ day: 1 });
  const end = start.add({ months: 1 });
  const today = Temporal.Now.zonedDateTimeISO(timeZone).toPlainDate().toString();
  const [date, setDate] = useState(today.startsWith(month) ? today : start.toString());
  const [expanded, setExpanded] = useState(() => window.matchMedia("(min-width: 1200px)").matches);
  const [editor, setEditor] = useState<{ item?: AgendaOccurrence; date: string }>();
  const occurrences = useQuery({ queryKey: [...queryKey, month], queryFn: () => config.list(start.toString(), end.toString()) });
  const rows = occurrences.data ?? [];
  const daily = rows.filter((row) => row.date === date);
  const days = [...new Set(rows.map((row) => row.date))].sort();
  const refresh = () => client.invalidateQueries({ queryKey: queryKey });
  const deletion = useMutation({
    mutationFn: (item: AgendaOccurrence) => config.remove(item.id, item.version),
    onSuccess: refresh,
    onError: (error) => { if (error instanceof ApiError && error.status === 409) void refresh(); }
  });
  return <aside className={styles.panel} aria-label={config.title}>
    <button className={styles.heading} aria-label={config.title} aria-expanded={expanded} aria-controls={contentId} onClick={() => setExpanded(!expanded)}>
      <NotebookPen aria-hidden /><span>{config.title}<small>{rows.length} anotação(ões) no mês</small></span><ChevronDown aria-hidden />
    </button>
    <div id={contentId} hidden={!expanded}>
      <div className={styles.content}>
        <p className={styles.intro}>{config.intro}</p>
        <Field label={config.dateLabel}><TextInput type="date" value={date} min={start.toString()} max={end.subtract({ days: 1 }).toString()} onChange={(event) => {
          const value = event.target.value;
          if (value >= start.toString() && value < end.toString()) { setDate(value); deletion.reset(); }
        }} /></Field>
        {days.length ? <nav className={styles.days} aria-label={`Dias com ${config.plural}`}>
          {days.map((day) => <button key={day} aria-label={`Ver ${config.plural} de ${day.split("-").reverse().join("/")}`} aria-pressed={day === date} onClick={() => { setDate(day); deletion.reset(); }}>{day.slice(8)}/{day.slice(5, 7)}</button>)}
        </nav> : null}
        <Button icon={<Plus />} onClick={() => setEditor({ date })}>{config.create}</Button>
        {occurrences.isLoading ? <p>Carregando {config.plural}…</p> : null}
        {occurrences.error ? <div role="alert" className={styles.error}>{occurrences.error.message}<Button onClick={() => occurrences.refetch()}>Tentar novamente</Button></div> : null}
        {deletion.error ? <p role="alert" className={styles.error}>{deletion.error.message}</p> : null}
        {!occurrences.isLoading && !occurrences.error && !daily.length ? <p className={styles.empty}>{config.empty}</p> : null}
        <div className={styles.notes} aria-busy={occurrences.isFetching}>
          {daily.map((item, index) => <article key={item.id} className={styles.note} aria-label={`${config.itemLabel} ${index + 1}`}>
            {kind === "commitments" && <strong>{item.collaboratorName ?? "Colaborador não informado"}</strong>}
            <p>{item.body}</p>
            <footer><small>{item.createdByName}<br />{new Intl.DateTimeFormat("pt-BR", { timeZone, dateStyle: "short", timeStyle: "short" }).format(new Date(item.createdAt))}</small>
              <div><Button variant="ghost" icon={<Pencil />} aria-label={`Editar ${config.singular} ${index + 1}`} onClick={() => setEditor({ item, date: item.date })} />
                <Button variant="ghost" icon={<Trash2 />} aria-label={`Excluir ${config.singular} ${index + 1}`} disabled={deletion.isPending} onClick={() => { if (window.confirm(config.confirmation)) deletion.mutate(item); }} /></div>
            </footer>
          </article>)}
        </div>
      </div>
    </div>
    {editor ? <OccurrenceEditor kind={kind} item={editor.item} date={editor.date} onClose={() => setEditor(undefined)} onSaved={() => { void refresh(); setEditor(undefined); }} /> : null}
  </aside>;
}

function OccurrenceEditor({ kind, item, date, onClose, onSaved }: { kind: NoteKind; item?: AgendaOccurrence; date: string; onClose: () => void; onSaved: () => void }) {
  const client = useQueryClient();
  const config = noteTypes[kind];
  const queryKey = ["agenda-" + kind];
  const [body, setBody] = useState(item?.body ?? "");
  const [collaboratorId, setCollaboratorId] = useState(item?.collaboratorId ?? "");
  const collaborators = useQuery({ queryKey: ["commitment-collaborators"], queryFn: listCommitmentCollaborators, enabled: kind === "commitments" });
  const options = collaborators.data ?? [];
  const mutation = useMutation({
    mutationFn: () => item ? config.update(item.id, { date, body: body.trim(), version: item.version, ...(kind === "commitments" ? { collaboratorId } : {}) }) : config.add({ date, body: body.trim(), ...(kind === "commitments" ? { collaboratorId } : {}) }),
    onSuccess: onSaved,
    onError: (error) => { if (error instanceof ApiError && error.status === 409) void client.invalidateQueries({ queryKey: queryKey }); }
  });
  function submit(event: FormEvent) { event.preventDefault(); if (body.trim() && (kind !== "commitments" || collaboratorId)) mutation.mutate(); }
  return <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}><Dialog.Portal>
    <Dialog.Overlay className={dialogStyles.overlay} />
    <Dialog.Content className={dialogStyles.dialog}>
      <div className={dialogStyles.dialogHeader}><Dialog.Title>{item ? `Editar ${config.singular}` : config.create}</Dialog.Title><Dialog.Close className={dialogStyles.closeButton} aria-label="Fechar"><X /></Dialog.Close></div>
      <Dialog.Description>Anotação de {date.split("-").reverse().join("/")}, compartilhada com os Administrativos.</Dialog.Description>
      <form onSubmit={submit}>
        {kind === "commitments" && <>
          <Field label="Colaborador"><SelectInput aria-label="Colaborador" required value={collaboratorId} onChange={event => setCollaboratorId(event.target.value)} disabled={collaborators.isPending || !!collaborators.error || mutation.isPending}>
            <option value="">Selecione um usuário</option>
            {item?.collaboratorId && !options.some(u => u.id === item.collaboratorId) && <option value={item.collaboratorId}>{item.collaboratorName ?? "Colaborador indisponível"}</option>}
            {options.map(u => <option key={u.id} value={u.id}>{u.displayName}{!u.active ? " (inativo)" : ""}</option>)}
          </SelectInput></Field>
          {collaborators.isPending && <p role="status">Carregando colaboradores…</p>}
          {collaborators.error && <div role="alert">{collaborators.error.message}<Button type="button" onClick={() => collaborators.refetch()}>Tentar novamente</Button></div>}
        </>}
        <Field label="Anotação"><TextArea autoFocus value={body} onChange={(event) => setBody(event.target.value)} maxLength={4000} required rows={6} placeholder={config.placeholder} /></Field>
        <small>{body.length}/4000 caracteres</small>
        {mutation.error ? <p className={styles.error} role="alert">{mutation.error.message}</p> : null}
        <div className={dialogStyles.dialogActions}><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" disabled={mutation.isPending || !body.trim() || (kind === "commitments" && (!collaboratorId || collaborators.isPending || !!collaborators.error))}>{config.save}</Button></div>
      </form>
    </Dialog.Content>
  </Dialog.Portal></Dialog.Root>;
}
