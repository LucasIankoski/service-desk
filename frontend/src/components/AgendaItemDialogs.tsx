import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, Clock3, MapPin, Pencil, RotateCcw, Trash2, UserRound, X } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Temporal } from "temporal-polyfill";
import { agendaAssignees, changeAgendaItemStatus, createAgendaItem, deleteAgendaItem, updateAgendaItem, type AgendaItemInput } from "../api/agenda";
import { ApiError } from "../api/http";
import type { AgendaShift, AgendaItem, AgendaItemKind, AgendaItemPriority } from "../api/types";
import { Button } from "./Button";
import { Field, SelectInput, TextArea, TextInput } from "./FormField";
import { formatAgendaPeriod, periodFieldsToInstants, shiftLabels, type AgendaPeriodFields } from "./agendaDateTime";
import styles from "./AgendaItemDialogs.module.css";

export type EditorState = { item?: AgendaItem; period: AgendaPeriodFields };
export const priorityLabels = { LOW: "Baixa", MEDIUM: "Média", HIGH: "Alta" };

function useAgendaConflict() {
  const client = useQueryClient();
  return (error: Error) => {
    if (error instanceof ApiError && error.status === 409) {
      void client.invalidateQueries({ queryKey: ["agenda-items"] });
    }
  };
}

export function ItemDetails({ item, isManager, timeZone, onClose, onEdit, onChanged, onDeleted }: {
  item?: AgendaItem;
  isManager: boolean;
  timeZone: string;
  onClose: () => void;
  onEdit: (item: AgendaItem) => void;
  onChanged: (item: AgendaItem) => void;
  onDeleted: () => void;
}) {
  const onError = useAgendaConflict();
  const statusMutation = useMutation({
    onError,
    mutationFn: () => changeAgendaItemStatus(item!.id, item!.status === "COMPLETED" ? "PENDING" : "COMPLETED", item!.version),
    onSuccess: onChanged
  });
  const deleteMutation = useMutation({
    onError,
    mutationFn: () => deleteAgendaItem(item!.id, item!.version),
    onSuccess: onDeleted
  });

  return (
    <Dialog.Root open={!!item} onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={styles.dialog} aria-describedby={undefined}>
          {item ? <>
            <div className={styles.dialogHeader}>
              <div>
                <span className={item.kind === "INSTITUTION_EVENT" ? styles.eventPill : styles.demandPill}>
                  {item.kind === "INSTITUTION_EVENT" ? "Evento institucional" : "Demanda interna"}
                </span>
                <Dialog.Title>{item.title}</Dialog.Title>
              </div>
              <Dialog.Close className={styles.closeButton} aria-label="Fechar"><X /></Dialog.Close>
            </div>
            <div className={styles.details}>
              <p><Clock3 aria-hidden /> {formatAgendaPeriod(item.startAt, item.endAt, item.allDay, timeZone, item.shift)}</p>
              {item.location ? <p><MapPin aria-hidden /> {item.location}</p> : null}
              {item.kind === "INTERNAL_DEMAND" ? <p><UserRound aria-hidden />
                {agendaAssignees(item).length ? `Responsáveis: ${agendaAssignees(item).map((person) => person.displayName).join(", ")}` : "Sem responsável"}
              </p> : null}
              {item.kind === "INTERNAL_DEMAND" ? (
                <p><Check aria-hidden /> {item.status === "COMPLETED" ? "Concluída" : "Pendente"}</p>
              ) : null}
              {item.kind === "INTERNAL_DEMAND" ? <p>Prioridade: {priorityLabels[item.priority ?? "MEDIUM"]}</p> : null}
              {item.description ? <div className={styles.description}>{item.description}</div> : null}
            </div>
            {isManager ? (
              <div className={styles.dialogActions}>
                {item.kind === "INTERNAL_DEMAND" ? (
                  <Button
                    icon={item.status === "COMPLETED" ? <RotateCcw /> : <Check />}
                    onClick={() => statusMutation.mutate()}
                    disabled={statusMutation.isPending}
                  >
                    {item.status === "COMPLETED" ? "Reabrir" : "Concluir"}
                  </Button>
                ) : null}
                <Button icon={<Pencil />} onClick={() => onEdit(item)}>Editar</Button>
                <Button
                  variant="danger"
                  icon={<Trash2 />}
                  disabled={deleteMutation.isPending}
                  onClick={() => { if (window.confirm(`Excluir “${item.title}”? Esta ação não pode ser desfeita.`)) deleteMutation.mutate(); }}
                >Excluir</Button>
              </div>
            ) : null}
            {(statusMutation.error ?? deleteMutation.error) ? <p className={styles.error} role="alert">{(statusMutation.error ?? deleteMutation.error)?.message}</p> : null}
          </> : null}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function ItemEditor({ state, managers, timeZone, onClose, onSaved, taskMode = false }: {
  state: EditorState;
  taskMode?: boolean;
  managers: { id: string; displayName: string }[];
  timeZone: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [kind, setKind] = useState<AgendaItemKind>(state.item?.kind ?? (taskMode ? "INTERNAL_DEMAND" : "INSTITUTION_EVENT"));
  const [assignedIds, setAssignedIds] = useState(() => agendaAssignees(state.item).map((person) => person.id));
  const [assigneeSearch, setAssigneeSearch] = useState("");
  const available = new Map([...agendaAssignees(state.item), ...managers].map((person) => [person.id, person]));
  const options = [...available.values()].sort((a, b) => a.displayName.localeCompare(b.displayName, "pt-BR"));
  const [allDay, setAllDay] = useState(state.period.allDay);
  const [periodChoice, setPeriodChoice] = useState<AgendaShift | "CUSTOM" | "">(
    state.item?.shift ?? (state.item && !state.item.allDay ? "CUSTOM" : ""));
  const [periodFields, setPeriodFields] = useState(() => ({
    ...state.period,
    startDateTime: state.period.startDateTime || `${state.period.startDate}T09:00`,
    endDateTime: state.period.endDateTime || `${state.period.endDate}T10:00`
  }));
  const shift = !allDay && kind === "INTERNAL_DEMAND" && periodChoice !== "CUSTOM" && periodChoice !== "" ? periodChoice : null;
  const dateOnly = allDay || !!shift;
  function changeDate(name: "startDate" | "endDate", value: string) {
    const timeName = name === "startDate" ? "startDateTime" : "endDateTime";
    setPeriodFields((current) => ({ ...current, [name]: value, [timeName]: `${value}T${current[timeName].slice(11) || (name === "startDate" ? "09:00" : "10:00")}` }));
  }
  function changeDateTime(name: "startDateTime" | "endDateTime", value: string) {
    setPeriodFields((current) => ({ ...current, [name]: value, [name === "startDateTime" ? "startDate" : "endDate"]: value.slice(0, 10) }));
  }
  const [formError, setFormError] = useState<string>();
  const onError = useAgendaConflict();
  const mutation = useMutation({
    onError,
    mutationFn: (input: AgendaItemInput) => {
      if (!state.item) return createAgendaItem(input);
      const { kind: _kind, ...update } = input;
      return updateAgendaItem(state.item.id, { ...update, version: state.item.version });
    },
    onSuccess: onSaved
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(undefined);
    const form = new FormData(event.currentTarget);
    try {
      if (!allDay && kind === "INTERNAL_DEMAND" && !periodChoice) {
        setFormError("Selecione o período da tarefa.");
        return;
      }
      const period = periodFieldsToInstants({ ...periodFields, allDay, shift }, timeZone);
      if (Temporal.Instant.compare(period.startAt, period.endAt) >= 0) {
        setFormError("O término deve ser posterior ao início.");
        return;
      }
      mutation.mutate({
        kind,
        priority: kind === "INTERNAL_DEMAND" ? String(form.get("priority")) as AgendaItemPriority : null,
        title: String(form.get("title") ?? ""),
        description: String(form.get("description") ?? "") || null,
        location: kind === "INSTITUTION_EVENT" ? String(form.get("location") ?? "") || null : null,
        assigneeIds: kind === "INTERNAL_DEMAND" ? assignedIds : [],
        ...period,
        shift,
        allDay
      });
    } catch {
      setFormError("Revise as datas e horários informados.");
    }
  }

  return (
    <Dialog.Root open onOpenChange={(open) => { if (!open) onClose(); }}>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={[styles.dialog, styles.editorDialog].join(" ")} aria-describedby={undefined}>
          <div className={styles.dialogHeader}>
            <div>
              <span className={styles.kicker}>{state.item ? "Editar item" : taskMode ? "Nova tarefa" : "Novo item"}</span>
              <Dialog.Title>{state.item ? state.item.title : taskMode ? "Nova tarefa" : "Adicionar à agenda"}</Dialog.Title>
            </div>
            <Dialog.Close className={styles.closeButton} aria-label="Fechar"><X /></Dialog.Close>
          </div>
          <form className={styles.editorForm} onSubmit={submit}>
            {!taskMode ? <fieldset className={styles.typeChoice} disabled={!!state.item}>
              <legend>Tipo</legend>
              <label className={kind === "INSTITUTION_EVENT" ? styles.typeActive : styles.typeOption}>
                <input type="radio" name="kind" checked={kind === "INSTITUTION_EVENT"} onChange={() => setKind("INSTITUTION_EVENT")} />
                <CalendarDays aria-hidden /> Evento institucional
              </label>
              <label className={kind === "INTERNAL_DEMAND" ? styles.typeActive : styles.typeOption}>
                <input type="radio" name="kind" checked={kind === "INTERNAL_DEMAND"} onChange={() => setKind("INTERNAL_DEMAND")} />
                <Check aria-hidden /> Demanda interna
              </label>
            </fieldset> : null}

            <Field label="Título"><TextInput name="title" required maxLength={160} defaultValue={state.item?.title ?? ""} autoFocus /></Field>
            <Field label="Descrição"><TextArea name="description" maxLength={4000} defaultValue={state.item?.description ?? ""} /></Field>
            {kind === "INSTITUTION_EVENT" ? (
              <Field label="Local"><TextInput name="location" maxLength={200} defaultValue={state.item?.location ?? ""} placeholder="Ex.: Auditório" /></Field>
            ) : (
              <fieldset className={styles.assignees}>
                <legend>Responsáveis</legend>
                <p>Selecione uma ou mais pessoas. Nenhuma seleção significa sem responsável.</p>
                <Field label="Buscar responsável"><TextInput type="search" value={assigneeSearch} onChange={(event) => setAssigneeSearch(event.target.value)} /></Field>
                <div className={styles.assigneeOptions}>
                  {options.filter((person) => person.displayName.toLocaleLowerCase("pt-BR").includes(assigneeSearch.toLocaleLowerCase("pt-BR"))).map((person) => <label key={person.id}>
                    <input type="checkbox" checked={assignedIds.includes(person.id)} onChange={(event) => setAssignedIds((ids) => event.target.checked ? [...ids, person.id] : ids.filter((id) => id !== person.id))} />
                    {person.displayName}
                  </label>)}
                </div>
                {!options.length ? <p>Nenhum Administrativo disponível. É possível salvar sem responsável.</p> : null}
                <small>{assignedIds.length} selecionado(s){assignedIds.length ? `: ${assignedIds.map((id) => available.get(id)?.displayName ?? "Responsável indisponível").join(", ")}` : ""}</small>
              </fieldset>
            )}

            {kind === "INTERNAL_DEMAND" ? <Field label="Prioridade">
              <SelectInput name="priority" defaultValue={state.item?.priority ?? "MEDIUM"}>
                {Object.entries(priorityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </SelectInput>
            </Field> : null}
            <label className={styles.allDay}>
              <input type="checkbox" checked={allDay} onChange={(event) => setAllDay(event.target.checked)} />
              Dia inteiro
            </label>
            {!allDay && kind === "INTERNAL_DEMAND" ? <Field label="Período">
              <SelectInput required value={periodChoice} onChange={(event) => setPeriodChoice(event.target.value as AgendaShift | "CUSTOM" | "")}>
                <option value="">Selecione o período</option>
                {Object.entries(shiftLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                <option value="CUSTOM">Horário personalizado</option>
              </SelectInput>
            </Field> : null}
            {dateOnly ? (
              <div className={styles.dateGrid}>
                <Field label="Data inicial"><TextInput type="date" name="startDate" required value={periodFields.startDate} onChange={(event) => changeDate("startDate", event.target.value)} /></Field>
                <Field label="Data final"><TextInput type="date" name="endDate" required value={periodFields.endDate} onChange={(event) => changeDate("endDate", event.target.value)} /></Field>
              </div>
            ) : (kind === "INSTITUTION_EVENT" || periodChoice === "CUSTOM") ? (
              <div className={styles.dateGrid}>
                <Field label="Início"><TextInput type="datetime-local" name="startDateTime" required value={periodFields.startDateTime} onChange={(event) => changeDateTime("startDateTime", event.target.value)} /></Field>
                <Field label="Término"><TextInput type="datetime-local" name="endDateTime" required value={periodFields.endDateTime} onChange={(event) => changeDateTime("endDateTime", event.target.value)} /></Field>
              </div>
            ) : null}
            {(formError ?? mutation.error?.message) ? <p className={styles.error} role="alert">{formError ?? mutation.error?.message}</p> : null}
            <div className={styles.dialogActions}>
              <Button type="button" onClick={onClose}>Cancelar</Button>
              <Button type="submit" variant="primary" disabled={mutation.isPending}>{state.item ? "Salvar alterações" : taskMode ? "Criar tarefa" : "Adicionar à agenda"}</Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
