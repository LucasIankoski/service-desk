import FullCalendar, { type DateSelectInfo, type DatesSetInfo, type EventClickInfo, type EventInput } from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/react/daygrid";
import interactionPlugin from "@fullcalendar/react/interaction";
import listPlugin from "@fullcalendar/react/list";
import ptBrLocale from "@fullcalendar/react/locales/pt-br";
import themePlugin from "@fullcalendar/react/themes/classic";
import timeGridPlugin from "@fullcalendar/react/timegrid";
import "@fullcalendar/react/skeleton.css";
import "@fullcalendar/react/themes/classic/theme.css";
import "@fullcalendar/react/themes/classic/palette.css";
import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Check, Clock3, MapPin, Pencil, Plus, RotateCcw, Trash2, UserRound, X } from "lucide-react";
import { type FormEvent, type ReactNode, useMemo, useState } from "react";
import { Navigate } from "react-router";
import { Temporal } from "temporal-polyfill";
import {
  changeAgendaItemStatus,
  createAgendaItem,
  deleteAgendaItem,
  listAgendaItems,
  listManagers,
  updateAgendaItem,
  type AgendaItemInput
} from "../api/agenda";
import type { AgendaItem, AgendaItemKind } from "../api/types";
import { Button } from "../components/Button";
import { Field, SelectInput, TextArea, TextInput } from "../components/FormField";
import {
  defaultAgendaPeriod,
  formatAgendaPeriod,
  instantToCalendarValue,
  periodFieldsFromInstants,
  periodFieldsFromSelection,
  periodFieldsToInstants,
  type AgendaPeriodFields
} from "../components/agendaDateTime";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { useSession } from "../hooks/useSession";
import styles from "./AgendaPage.module.css";

type Range = { start: string; end: string };
type KindFilter = "ALL" | AgendaItemKind;
type EditorState = { item?: AgendaItem; period: AgendaPeriodFields };

export default function AgendaPage() {
  const session = useSession();
  const settings = usePublicSettings();
  const queryClient = useQueryClient();
  const timeZone = settings?.timezoneName ?? "America/Sao_Paulo";
  const isManager = !!session.data?.roles.includes("MANAGER");
  const canAccess = !!session.data?.roles.some((role) => role === "MANAGER" || role === "REQUESTER");
  const [range, setRange] = useState<Range>();
  const [kindFilter, setKindFilter] = useState<KindFilter>("ALL");
  const [hideCompleted, setHideCompleted] = useState(false);
  const [selected, setSelected] = useState<AgendaItem>();
  const [editor, setEditor] = useState<EditorState>();

  const agenda = useQuery({
    queryKey: ["agenda-items", range?.start, range?.end],
    queryFn: () => listAgendaItems(range!.start, range!.end),
    enabled: !!range && canAccess
  });
  const managers = useQuery({ queryKey: ["agenda-managers"], queryFn: listManagers, enabled: isManager });

  const visibleItems = useMemo(() => (agenda.data ?? []).filter((item) => {
    if (kindFilter !== "ALL" && item.kind !== kindFilter) return false;
    return !(hideCompleted && item.status === "COMPLETED");
  }), [agenda.data, hideCompleted, kindFilter]);

  const calendarEvents = useMemo<EventInput[]>(() => visibleItems.map((item) => ({
    id: item.id,
    title: item.title,
    start: instantToCalendarValue(item.startAt, item.allDay, timeZone),
    end: instantToCalendarValue(item.endAt, item.allDay, timeZone),
    allDay: item.allDay,
    color: item.kind === "INSTITUTION_EVENT" ? "var(--color-accent)" : "var(--color-primary)",
    classNames: [
      item.kind === "INSTITUTION_EVENT" ? styles.eventItem : styles.demandItem,
      item.status === "COMPLETED" ? styles.completedItem : ""
    ].filter(Boolean)
  })), [timeZone, visibleItems]);

  if (!canAccess) return <Navigate to="/tickets" replace />;

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ["agenda-items"] });
  }

  function onDatesSet(info: DatesSetInfo) {
    const next = { start: info.startStr, end: info.endStr };
    setRange((current) => current?.start === next.start && current.end === next.end ? current : next);
  }

  function onSelect(info: DateSelectInfo) {
    if (!isManager) return;
    setEditor({ period: periodFieldsFromSelection(info.startStr, info.endStr, info.allDay, timeZone) });
  }

  function onEventClick(info: EventClickInfo) {
    const item = agenda.data?.find((candidate) => candidate.id === info.event.id);
    if (item) setSelected(item);
  }

  const initialView = window.matchMedia("(max-width: 780px)").matches ? "listMonth" : "dayGridMonth";

  return (
    <section className={styles.page}>
      <header className={styles.heading}>
        <div>
          <span>Planejamento institucional</span>
          <h2>Agenda</h2>
          <p>{isManager ? "Organize eventos e demandas internas em um só calendário." : "Acompanhe os próximos eventos da instituição."}</p>
        </div>
        {isManager ? (
          <Button variant="primary" icon={<Plus />} onClick={() => setEditor({ period: defaultAgendaPeriod(timeZone) })}>
            Novo item
          </Button>
        ) : null}
      </header>

      <div className={styles.controls} aria-label="Filtros da agenda">
        <div className={styles.segmented}>
          <FilterButton active={kindFilter === "ALL"} onClick={() => setKindFilter("ALL")}>Tudo</FilterButton>
          <FilterButton active={kindFilter === "INSTITUTION_EVENT"} onClick={() => setKindFilter("INSTITUTION_EVENT")}>Eventos</FilterButton>
          {isManager ? <FilterButton active={kindFilter === "INTERNAL_DEMAND"} onClick={() => setKindFilter("INTERNAL_DEMAND")}>Demandas</FilterButton> : null}
        </div>
        {isManager ? (
          <label className={styles.completedToggle}>
            <input type="checkbox" checked={hideCompleted} onChange={(event) => setHideCompleted(event.target.checked)} />
            Ocultar concluídas
          </label>
        ) : null}
        <div className={styles.legend} aria-label="Legenda">
          <span><i className={styles.eventDot} /> Evento</span>
          {isManager ? <span><i className={styles.demandDot} /> Demanda interna</span> : null}
        </div>
      </div>

      <div className={styles.calendarPanel} aria-busy={agenda.isFetching}>
        {agenda.error ? <p className={styles.error} role="alert">{agenda.error.message}</p> : null}
        <FullCalendar
          plugins={[themePlugin, dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin]}
          locale={ptBrLocale}
          timeZone={timeZone}
          initialView={initialView}
          headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek,timeGridDay,listMonth" }}
          height="auto"
          nowIndicator
          navLinks
          selectable={isManager}
          selectMirror
          editable={false}
          events={calendarEvents}
          datesSet={onDatesSet}
          select={onSelect}
          eventClick={onEventClick}
          eventDidMount={(info) => {
            const parent = info.el.parentElement;
            if (parent?.getAttribute("role") === "list") parent.removeAttribute("role");
          }}
          noEventsContent="Nenhum item neste período."
          dayMaxEvents={3}
        />
        {agenda.isLoading ? <div className={styles.loading}>Carregando agenda…</div> : null}
      </div>

      <ItemDetails
        item={selected}
        isManager={isManager}
        timeZone={timeZone}
        onClose={() => setSelected(undefined)}
        onEdit={(item) => {
          setSelected(undefined);
          setEditor({ item, period: periodFieldsFromInstants(item.startAt, item.endAt, item.allDay, timeZone) });
        }}
        onChanged={(item) => { refresh(); setSelected(item); }}
        onDeleted={() => { refresh(); setSelected(undefined); }}
      />

      {editor ? (
        <ItemEditor
          state={editor}
          managers={managers.data ?? []}
          timeZone={timeZone}
          onClose={() => setEditor(undefined)}
          onSaved={() => { refresh(); setEditor(undefined); }}
        />
      ) : null}
    </section>
  );
}

function FilterButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" aria-pressed={active} className={active ? styles.filterActive : styles.filter} onClick={onClick}>{children}</button>;
}

function ItemDetails({ item, isManager, timeZone, onClose, onEdit, onChanged, onDeleted }: {
  item?: AgendaItem;
  isManager: boolean;
  timeZone: string;
  onClose: () => void;
  onEdit: (item: AgendaItem) => void;
  onChanged: (item: AgendaItem) => void;
  onDeleted: () => void;
}) {
  const statusMutation = useMutation({
    mutationFn: () => changeAgendaItemStatus(item!.id, item!.status === "COMPLETED" ? "PENDING" : "COMPLETED", item!.version),
    onSuccess: onChanged
  });
  const deleteMutation = useMutation({
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
              <p><Clock3 aria-hidden /> {formatAgendaPeriod(item.startAt, item.endAt, item.allDay, timeZone)}</p>
              {item.location ? <p><MapPin aria-hidden /> {item.location}</p> : null}
              {item.assigneeName ? <p><UserRound aria-hidden /> Responsável: {item.assigneeName}</p> : null}
              {item.kind === "INTERNAL_DEMAND" && !item.assigneeName ? <p><UserRound aria-hidden /> Sem responsável</p> : null}
              {item.kind === "INTERNAL_DEMAND" ? (
                <p><Check aria-hidden /> {item.status === "COMPLETED" ? "Concluída" : "Pendente"}</p>
              ) : null}
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

function ItemEditor({ state, managers, timeZone, onClose, onSaved }: {
  state: EditorState;
  managers: { id: string; displayName: string }[];
  timeZone: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [kind, setKind] = useState<AgendaItemKind>(state.item?.kind ?? "INSTITUTION_EVENT");
  const [allDay, setAllDay] = useState(state.period.allDay);
  const [formError, setFormError] = useState<string>();
  const mutation = useMutation({
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
      const period = periodFieldsToInstants({
        allDay,
        startDate: String(form.get("startDate") ?? ""),
        endDate: String(form.get("endDate") ?? ""),
        startDateTime: String(form.get("startDateTime") ?? ""),
        endDateTime: String(form.get("endDateTime") ?? "")
      }, timeZone);
      if (Temporal.Instant.compare(period.startAt, period.endAt) >= 0) {
        setFormError("O término deve ser posterior ao início.");
        return;
      }
      mutation.mutate({
        kind,
        title: String(form.get("title") ?? ""),
        description: String(form.get("description") ?? "") || null,
        location: kind === "INSTITUTION_EVENT" ? String(form.get("location") ?? "") || null : null,
        assigneeId: kind === "INTERNAL_DEMAND" ? String(form.get("assigneeId") ?? "") || null : null,
        ...period,
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
              <span className={styles.kicker}>{state.item ? "Editar item" : "Novo item"}</span>
              <Dialog.Title>{state.item ? state.item.title : "Adicionar à agenda"}</Dialog.Title>
            </div>
            <Dialog.Close className={styles.closeButton} aria-label="Fechar"><X /></Dialog.Close>
          </div>
          <form className={styles.editorForm} onSubmit={submit}>
            <fieldset className={styles.typeChoice} disabled={!!state.item}>
              <legend>Tipo</legend>
              <label className={kind === "INSTITUTION_EVENT" ? styles.typeActive : styles.typeOption}>
                <input type="radio" name="kind" checked={kind === "INSTITUTION_EVENT"} onChange={() => setKind("INSTITUTION_EVENT")} />
                <CalendarDays aria-hidden /> Evento institucional
              </label>
              <label className={kind === "INTERNAL_DEMAND" ? styles.typeActive : styles.typeOption}>
                <input type="radio" name="kind" checked={kind === "INTERNAL_DEMAND"} onChange={() => setKind("INTERNAL_DEMAND")} />
                <Check aria-hidden /> Demanda interna
              </label>
            </fieldset>

            <Field label="Título"><TextInput name="title" required maxLength={160} defaultValue={state.item?.title ?? ""} autoFocus /></Field>
            <Field label="Descrição"><TextArea name="description" maxLength={4000} defaultValue={state.item?.description ?? ""} /></Field>
            {kind === "INSTITUTION_EVENT" ? (
              <Field label="Local"><TextInput name="location" maxLength={200} defaultValue={state.item?.location ?? ""} placeholder="Ex.: Auditório" /></Field>
            ) : (
              <Field label="Responsável">
                <SelectInput name="assigneeId" defaultValue={state.item?.assigneeId ?? ""}>
                  <option value="">Sem responsável</option>
                  {managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.displayName}</option>)}
                </SelectInput>
              </Field>
            )}

            <label className={styles.allDay}>
              <input type="checkbox" checked={allDay} onChange={(event) => setAllDay(event.target.checked)} />
              Dia inteiro
            </label>
            {allDay ? (
              <div className={styles.dateGrid}>
                <Field label="Data inicial"><TextInput type="date" name="startDate" required defaultValue={state.period.startDate} /></Field>
                <Field label="Data final"><TextInput type="date" name="endDate" required defaultValue={state.period.endDate} /></Field>
              </div>
            ) : (
              <div className={styles.dateGrid}>
                <Field label="Início"><TextInput type="datetime-local" name="startDateTime" required defaultValue={state.period.startDateTime || `${state.period.startDate}T09:00`} /></Field>
                <Field label="Término"><TextInput type="datetime-local" name="endDateTime" required defaultValue={state.period.endDateTime || `${state.period.endDate}T10:00`} /></Field>
              </div>
            )}
            {(formError ?? mutation.error?.message) ? <p className={styles.error} role="alert">{formError ?? mutation.error?.message}</p> : null}
            <div className={styles.dialogActions}>
              <Button type="button" onClick={onClose}>Cancelar</Button>
              <Button type="submit" variant="primary" disabled={mutation.isPending}>{state.item ? "Salvar alterações" : "Adicionar à agenda"}</Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
