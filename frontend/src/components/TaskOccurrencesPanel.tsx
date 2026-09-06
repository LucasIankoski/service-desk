import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, NotebookPen, Pencil, Plus, Trash2, X } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Temporal } from "temporal-polyfill";
import { createAgendaOccurrence, deleteAgendaOccurrence, listAgendaOccurrences, updateAgendaOccurrence } from "../api/agenda";
import { ApiError } from "../api/http";
import type { AgendaOccurrence } from "../api/types";
import { Button } from "./Button";
import { Field, TextArea, TextInput } from "./FormField";
import dialogStyles from "./AgendaItemDialogs.module.css";
import styles from "./TaskOccurrencesPanel.module.css";

export function TaskOccurrencesPanel({ month, timeZone }: { month: string; timeZone: string }) {
  const client = useQueryClient();
  const start = Temporal.PlainYearMonth.from(month).toPlainDate({ day: 1 });
  const end = start.add({ months: 1 });
  const today = Temporal.Now.zonedDateTimeISO(timeZone).toPlainDate().toString();
  const [date, setDate] = useState(today.startsWith(month) ? today : start.toString());
  const [expanded, setExpanded] = useState(() => window.matchMedia("(min-width: 1200px)").matches);
  const [editor, setEditor] = useState<{ item?: AgendaOccurrence; date: string }>();
  const occurrences = useQuery({ queryKey: ["agenda-occurrences", month], queryFn: () => listAgendaOccurrences(start.toString(), end.toString()) });
  const rows = occurrences.data ?? [];
  const daily = rows.filter((row) => row.date === date);
  const days = [...new Set(rows.map((row) => row.date))].sort();
  const refresh = () => client.invalidateQueries({ queryKey: ["agenda-occurrences"] });
  const deletion = useMutation({
    mutationFn: (item: AgendaOccurrence) => deleteAgendaOccurrence(item.id, item.version),
    onSuccess: refresh,
    onError: (error) => { if (error instanceof ApiError && error.status === 409) void refresh(); }
  });
  return <aside className={styles.panel} aria-label="Ocorrências do dia">
    <button className={styles.heading} aria-label="Ocorrências do dia" aria-expanded={expanded} aria-controls="daily-occurrences-content" onClick={() => setExpanded(!expanded)}>
      <NotebookPen aria-hidden /><span>Ocorrências do dia<small>{rows.length} anotação(ões) no mês</small></span><ChevronDown aria-hidden />
    </button>
    <div id="daily-occurrences-content" hidden={!expanded}>
      <div className={styles.content}>
        <p className={styles.intro}>Registre os acontecimentos do dia, mesmo quando não houver tarefas.</p>
        <Field label="Data das ocorrências"><TextInput type="date" value={date} min={start.toString()} max={end.subtract({ days: 1 }).toString()} onChange={(event) => {
          const value = event.target.value;
          if (value >= start.toString() && value < end.toString()) { setDate(value); deletion.reset(); }
        }} /></Field>
        {days.length ? <nav className={styles.days} aria-label="Dias com ocorrências">
          {days.map((day) => <button key={day} aria-label={`Ver ocorrências de ${day.split("-").reverse().join("/")}`} aria-pressed={day === date} onClick={() => { setDate(day); deletion.reset(); }}>{day.slice(8)}/{day.slice(5, 7)}</button>)}
        </nav> : null}
        <Button icon={<Plus />} onClick={() => setEditor({ date })}>Nova ocorrência</Button>
        {occurrences.isLoading ? <p>Carregando ocorrências…</p> : null}
        {occurrences.error ? <div role="alert" className={styles.error}>{occurrences.error.message}<Button onClick={() => occurrences.refetch()}>Tentar novamente</Button></div> : null}
        {deletion.error ? <p role="alert" className={styles.error}>{deletion.error.message}</p> : null}
        {!occurrences.isLoading && !occurrences.error && !daily.length ? <p className={styles.empty}>Nenhuma ocorrência neste dia.</p> : null}
        <div className={styles.notes} aria-busy={occurrences.isFetching}>
          {daily.map((item, index) => <article key={item.id} className={styles.note} aria-label={`Ocorrência ${index + 1}`}>
            <p>{item.body}</p>
            <footer><small>{item.createdByName}<br />{new Intl.DateTimeFormat("pt-BR", { timeZone, dateStyle: "short", timeStyle: "short" }).format(new Date(item.createdAt))}</small>
              <div><Button variant="ghost" icon={<Pencil />} aria-label={`Editar ocorrência ${index + 1}`} onClick={() => setEditor({ item, date: item.date })} />
                <Button variant="ghost" icon={<Trash2 />} aria-label={`Excluir ocorrência ${index + 1}`} disabled={deletion.isPending} onClick={() => { if (window.confirm("Excluir esta ocorrência? Esta ação não pode ser desfeita.")) deletion.mutate(item); }} /></div>
            </footer>
          </article>)}
        </div>
      </div>
    </div>
    {editor ? <OccurrenceEditor item={editor.item} date={editor.date} onClose={() => setEditor(undefined)} onSaved={() => { void refresh(); setEditor(undefined); }} /> : null}
  </aside>;
}

function OccurrenceEditor({ item, date, onClose, onSaved }: { item?: AgendaOccurrence; date: string; onClose: () => void; onSaved: () => void }) {
  const client = useQueryClient();
  const [body, setBody] = useState(item?.body ?? "");
  const mutation = useMutation({
    mutationFn: () => item ? updateAgendaOccurrence(item.id, { date, body: body.trim(), version: item.version }) : createAgendaOccurrence({ date, body: body.trim() }),
    onSuccess: onSaved,
    onError: (error) => { if (error instanceof ApiError && error.status === 409) void client.invalidateQueries({ queryKey: ["agenda-occurrences"] }); }
  });
  function submit(event: FormEvent) { event.preventDefault(); if (body.trim()) mutation.mutate(); }
  return <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}><Dialog.Portal>
    <Dialog.Overlay className={dialogStyles.overlay} />
    <Dialog.Content className={dialogStyles.dialog}>
      <div className={dialogStyles.dialogHeader}><Dialog.Title>{item ? "Editar ocorrência" : "Nova ocorrência"}</Dialog.Title><Dialog.Close className={dialogStyles.closeButton} aria-label="Fechar"><X /></Dialog.Close></div>
      <Dialog.Description>Anotação de {date.split("-").reverse().join("/")}, compartilhada com os Administrativos.</Dialog.Description>
      <form onSubmit={submit}>
        <Field label="Anotação"><TextArea autoFocus value={body} onChange={(event) => setBody(event.target.value)} maxLength={4000} required rows={6} placeholder="O que aconteceu neste dia?" /></Field>
        <small>{body.length}/4000 caracteres</small>
        {mutation.error ? <p className={styles.error} role="alert">{mutation.error.message}</p> : null}
        <div className={dialogStyles.dialogActions}><Button type="button" onClick={onClose}>Cancelar</Button><Button type="submit" variant="primary" disabled={mutation.isPending || !body.trim()}>Salvar ocorrência</Button></div>
      </form>
    </Dialog.Content>
  </Dialog.Portal></Dialog.Root>;
}
