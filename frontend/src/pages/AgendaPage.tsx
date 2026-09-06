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
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import { Navigate } from "react-router";
import { listAgendaItems, listManagers } from "../api/agenda";
import { ItemDetails, ItemEditor, type EditorState } from "../components/AgendaItemDialogs";
import type { AgendaItemKind } from "../api/types";
import { Button } from "../components/Button";
import {
  defaultAgendaPeriod,
  instantToCalendarValue,
  periodFieldsFromInstants,
  periodFieldsFromSelection,
} from "../components/agendaDateTime";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { useSession } from "../hooks/useSession";
import styles from "./AgendaPage.module.css";

type Range = { start: string; end: string };
type KindFilter = "ALL" | AgendaItemKind;

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
  const [selectedId, setSelectedId] = useState<string>();
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
    return queryClient.invalidateQueries({ queryKey: ["agenda-items"] });
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
    if (item) setSelectedId(item.id);
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
        key={selectedId ?? "closed"}
        item={agenda.data?.find((item) => item.id === selectedId)}
        isManager={isManager}
        timeZone={timeZone}
        onClose={() => setSelectedId(undefined)}
        onEdit={(item) => {
          setSelectedId(undefined);
          setEditor({ item, period: periodFieldsFromInstants(item.startAt, item.endAt, item.allDay, timeZone) });
        }}
        onChanged={() => refresh()}
        onDeleted={() => { refresh(); setSelectedId(undefined); }}
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
