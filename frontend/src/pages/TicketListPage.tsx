import * as Dialog from "@radix-ui/react-dialog";
import { useQuery } from "@tanstack/react-query";
import { Filter, Plus, Search, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import type { Priority, TicketStatus } from "../api/types";
import { listAssignees, listCategories, listTickets, type TicketFilters } from "../api/tickets";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Field, SelectInput, TextInput } from "../components/FormField";
import { formatDateTime, priorityLabel, priorityTone, statusLabel, statusTone } from "../components/ticketPresentation";
import styles from "./TicketListPage.module.css";
import { useSession } from "../hooks/useSession";

const statuses: Array<TicketStatus | ""> = ["", "OPEN", "TRIAGE", "IN_PROGRESS", "WAITING_REQUESTER", "RESOLVED", "CLOSED", "CANCELED"];
const priorities: Array<Priority | ""> = ["", "LOW", "NORMAL", "HIGH", "CRITICAL"];

export default function TicketListPage() {
  const [filters, setFilters] = useState<TicketFilters>({});
  const [page, setPage] = useState(0);
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);
  const session = useSession();
  const canOperate = !!session.data?.roles.some((role) => role === "AGENT" || role === "MANAGER");
  const tickets = useQuery({ queryKey: ["tickets", filters, page], queryFn: () => listTickets(filters, page) });
  const categories = useQuery({ queryKey: ["categories"], queryFn: listCategories });
  const assignees = useQuery({ queryKey: ["assignees"], queryFn: listAssignees, enabled: canOperate });

  const changeFilters = (next: TicketFilters) => {
    setPage(0);
    setFilters(next);
  };

  const filterForm = (
    <TicketFilterForm
      filters={filters}
      categories={categories.data ?? []}
      assignees={assignees.data ?? []}
      showOperationalFilters={canOperate}
      onChange={changeFilters}
    />
  );

  return (
    <section className={styles.page}>
      <div className={styles.heading}>
        <div>
          <span>Fila única</span>
          <h2>Solicitações</h2>
        </div>
        <div className={styles.headingActions}>
          <Dialog.Root open={mobileFiltersOpen} onOpenChange={setMobileFiltersOpen}>
            <Dialog.Trigger asChild>
              <Button icon={<Filter />} className={styles.mobileFilterButton}>Filtros</Button>
            </Dialog.Trigger>
            <Dialog.Portal>
              <Dialog.Overlay className={styles.overlay} />
              <Dialog.Content className={styles.drawer} aria-label="Filtros">
                <div className={styles.drawerHeader}>
                  <Dialog.Title>Filtros</Dialog.Title>
                  <Dialog.Close asChild><Button icon={<X />} aria-label="Fechar filtros" /></Dialog.Close>
                </div>
                {filterForm}
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
          <Link to="/tickets/new" className={styles.primaryLink}><Plus /> Abrir solicitação</Link>
        </div>
      </div>

      <div className={styles.grid}>
        <aside className={styles.filters} aria-label="Filtros de solicitações">
          {filterForm}
        </aside>
        <div className={styles.list}>
          {tickets.isLoading ? <p className={styles.state}>Carregando solicitações.</p> : null}
          {tickets.error ? <p className={styles.error} role="alert">{tickets.error.message}</p> : null}
          {tickets.data?.content.length === 0 ? <p className={styles.state}>Nenhuma solicitação encontrada.</p> : null}
          {tickets.data?.content.map((ticket) => (
            <Link to={`/tickets/${ticket.id}`} key={ticket.id} className={styles.ticket}>
              <div className={styles.ticketMain}>
                <strong>{ticket.subject}</strong>
                <span>{ticket.publicNumber} · {ticket.requesterName ?? "Solicitante"}</span>
              </div>
              <div className={styles.ticketMeta}>
                <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>
                <Badge tone={priorityTone(ticket.priority)}>{priorityLabel(ticket.priority)}</Badge>
                <span>{ticket.categoryName ?? "Sem categoria"}</span>
                <span>{formatDateTime(ticket.dueAt)}</span>
              </div>
            </Link>
          ))}
          {tickets.data && tickets.data.totalPages > 1 ? (
            <nav className={styles.pagination} aria-label="Paginação de solicitações">
              <Button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                Anterior
              </Button>
              <span>Página {page + 1} de {tickets.data.totalPages}</span>
              <Button
                type="button"
                disabled={page + 1 >= tickets.data.totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Próxima
              </Button>
            </nav>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function TicketFilterForm({
  filters,
  categories,
  assignees,
  showOperationalFilters,
  onChange
}: {
  filters: TicketFilters;
  categories: { id: string; name: string }[];
  assignees: { id: string; displayName: string }[];
  showOperationalFilters: boolean;
  onChange: (filters: TicketFilters) => void;
}) {
  return (
    <div className={styles.filterForm}>
      <Field label="Busca">
        <span className={styles.searchField}>
          <Search aria-hidden />
          <TextInput
            value={filters.subject ?? ""}
            onChange={(event) => onChange({ ...filters, subject: event.target.value })}
            placeholder="Assunto"
          />
        </span>
      </Field>
      <Field label="Número">
        <TextInput
          value={filters.number ?? ""}
          onChange={(event) => onChange({ ...filters, number: event.target.value })}
          placeholder="SD-2026-000001"
        />
      </Field>
      <Field label="Status">
        <SelectInput
          value={filters.status ?? ""}
          onChange={(event) => onChange({ ...filters, status: event.target.value as TicketStatus | "" })}
        >
          {statuses.map((status) => (
            <option key={status || "all"} value={status}>{status ? statusLabel(status) : "Todos"}</option>
          ))}
        </SelectInput>
      </Field>
      <Field label="Prioridade">
        <SelectInput
          value={filters.priority ?? ""}
          onChange={(event) => onChange({ ...filters, priority: event.target.value as Priority | "" })}
        >
          {priorities.map((priority) => (
            <option key={priority || "all"} value={priority}>{priority ? priorityLabel(priority) : "Todas"}</option>
          ))}
        </SelectInput>
      </Field>
      <Field label="Categoria">
        <SelectInput
          value={filters.categoryId ?? ""}
          onChange={(event) => onChange({ ...filters, categoryId: event.target.value })}
        >
          <option value="">Todas</option>
          {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </SelectInput>
      </Field>
      {showOperationalFilters ? (
        <Field label="Responsável">
          <SelectInput
            value={filters.assigneeId ?? ""}
            onChange={(event) => onChange({ ...filters, assigneeId: event.target.value })}
          >
            <option value="">Todos</option>
            {assignees.map((assignee) => (
              <option key={assignee.id} value={assignee.id}>{assignee.displayName}</option>
            ))}
          </SelectInput>
        </Field>
      ) : null}
      <Field label="Prazo a partir de">
        <TextInput
          type="datetime-local"
          value={filters.dueAfter ?? ""}
          onChange={(event) => onChange({ ...filters, dueAfter: event.target.value })}
        />
      </Field>
      <Field label="Prazo até">
        <TextInput
          type="datetime-local"
          value={filters.dueBefore ?? ""}
          onChange={(event) => onChange({ ...filters, dueBefore: event.target.value })}
        />
      </Field>
      <Button type="button" onClick={() => onChange({})}>Limpar filtros</Button>
    </div>
  );
}
