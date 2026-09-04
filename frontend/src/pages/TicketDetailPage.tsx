import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, MessageSquare, UserCheck } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router";
import { z } from "zod";
import { addComment, assignTicket, classifyTicket, getTicket, listAssignees, listCategories, updateStatus } from "../api/tickets";
import type { CommentVisibility, Priority, TicketStatus } from "../api/types";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Field, SelectInput, TextArea, TextInput } from "../components/FormField";
import { StatusTrail } from "../components/StatusTrail";
import { formatDateTime, priorityLabel, priorityTone, statusLabel, statusTone } from "../components/ticketPresentation";
import { useSession } from "../hooks/useSession";
import styles from "./TicketDetailPage.module.css";

const commentSchema = z.object({ body: z.string().min(2).max(4000), visibility: z.enum(["PUBLIC", "INTERNAL"]) });
type CommentForm = z.infer<typeof commentSchema>;

const priorities: Priority[] = ["LOW", "NORMAL", "HIGH", "CRITICAL"];

export default function TicketDetailPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const session = useSession();
  const ticket = useQuery({ queryKey: ["ticket", id], queryFn: () => getTicket(id), enabled: !!id });
  const categories = useQuery({ queryKey: ["categories"], queryFn: listCategories });
  const [files, setFiles] = useState<File[]>([]);
  const commentForm = useForm<CommentForm>({
    resolver: zodResolver(commentSchema),
    defaultValues: { body: "", visibility: "PUBLIC" }
  });

  const canOperate = !!session.data?.roles.some((role) => role === "AGENT" || role === "MANAGER");
  const canManage = !!session.data?.roles.includes("MANAGER");
  const assignees = useQuery({ queryKey: ["assignees"], queryFn: listAssignees, enabled: canOperate });
  const refresh = (data: unknown) => {
    queryClient.setQueryData(["ticket", id], data);
    queryClient.invalidateQueries({ queryKey: ["tickets"] });
  };
  const mutateStatus = useMutation({ mutationFn: (status: TicketStatus) => updateStatus(id, status, ticket.data!.version), onSuccess: refresh });
  const mutateAssign = useMutation({
    mutationFn: (assigneeId: string) => assignTicket(id, assigneeId, ticket.data!.version),
    onSuccess: refresh
  });
  const mutateClassify = useMutation({
    mutationFn: (form: FormData) => classifyTicket(id, {
      categoryId: String(form.get("categoryId")),
      priority: String(form.get("priority")) as Priority,
      dueAt: String(form.get("dueAt") || "") ? new Date(String(form.get("dueAt"))).toISOString() : null,
      version: ticket.data!.version
    }),
    onSuccess: refresh
  });
  const mutateComment = useMutation({
    mutationFn: (value: CommentForm) => addComment(id, { ...value, files }),
    onSuccess: (data) => {
      refresh(data);
      commentForm.reset({ body: "", visibility: "PUBLIC" });
      setFiles([]);
    }
  });

  if (ticket.isLoading) return <p className={styles.state}>Carregando solicitação.</p>;
  if (ticket.error) return <p className={styles.error} role="alert">{ticket.error.message}</p>;
  if (!ticket.data) return null;

  return (
    <section className={styles.page}>
      <header className={styles.heading}>
        <div>
          <Link to="/tickets">Solicitações</Link>
          <h2>{ticket.data.subject}</h2>
          <p>{ticket.data.publicNumber}</p>
        </div>
        <div className={styles.badges}>
          <Badge tone={statusTone(ticket.data.status)}>{statusLabel(ticket.data.status)}</Badge>
          <Badge tone={priorityTone(ticket.data.priority)}>{priorityLabel(ticket.data.priority)}</Badge>
        </div>
      </header>

      <div className={styles.layout}>
        <article className={styles.main}>
          <StatusTrail status={ticket.data.status} />
          <section className={styles.description}>
            <h3>Descrição</h3>
            <p>{ticket.data.description}</p>
            {ticket.data.attachments.length ? (
              <div className={styles.attachments}>
                {ticket.data.attachments.map((file) => (
                  <a key={file.id} href={`/api/v1/tickets/attachments/${file.id}`}>
                    <Download /> {file.originalName}
                  </a>
                ))}
              </div>
            ) : null}
          </section>

          <section className={styles.comments}>
            <h3>Comentários</h3>
            {ticket.data.comments.length ? ticket.data.comments.map((comment) => (
              <article key={comment.id} className={styles.comment}>
                <div>
                  <strong>{comment.authorName ?? "Usuário"}</strong>
                  <span>{formatDateTime(comment.createdAt)}</span>
                  {comment.visibility === "INTERNAL" ? <Badge tone="amber">Nota interna</Badge> : null}
                </div>
                <p>{comment.body}</p>
                {comment.attachments.map((file) => (
                  <a key={file.id} href={`/api/v1/tickets/attachments/${file.id}`}><Download /> {file.originalName}</a>
                ))}
              </article>
            )) : <p className={styles.state}>Ainda não há comentários.</p>}
            {ticket.data.status !== "RESOLVED" ? (
              <form className={styles.commentForm} onSubmit={commentForm.handleSubmit((value) => mutateComment.mutate(value))}>
                <Field label="Novo comentário">
                  <TextArea {...commentForm.register("body")} />
                </Field>
                <div className={styles.commentOptions}>
                  <Field label="Visibilidade">
                    <SelectInput {...commentForm.register("visibility")}>
                      <option value="PUBLIC">Público</option>
                      {canOperate ? <option value="INTERNAL">Nota interna</option> : null}
                    </SelectInput>
                  </Field>
                  <Field label="Anexos">
                    <TextInput type="file" multiple onChange={(event) => setFiles(Array.from(event.target.files ?? []).slice(0, 5))} />
                  </Field>
                </div>
                <Button type="submit" variant="primary" icon={<MessageSquare />} disabled={mutateComment.isPending}>
                  Comentar
                </Button>
              </form>
            ) : (
              <p className={styles.state}>Solicitações resolvidas não recebem novos comentários.</p>
            )}
          </section>
        </article>

        <aside className={styles.side}>
          <dl className={styles.facts}>
            <div><dt>Solicitante</dt><dd>{ticket.data.requesterName}</dd></div>
            <div><dt>Responsável</dt><dd>{ticket.data.assigneeName ?? "Sem responsável"}</dd></div>
            <div><dt>Categoria</dt><dd>{ticket.data.categoryName ?? "Sem categoria"}</dd></div>
            <div><dt>Prazo</dt><dd>{formatDateTime(ticket.data.dueAt)}</dd></div>
          </dl>

          {canOperate && ticket.data.status !== "RESOLVED" ? (
            <div className={styles.actions}>
              <Button icon={<UserCheck />} onClick={() => mutateAssign.mutate(session.data!.id)} disabled={mutateAssign.isPending}>
                Assumir
              </Button>
              {canManage ? (
                <form onSubmit={(event) => {
                  event.preventDefault();
                  const form = new FormData(event.currentTarget);
                  mutateAssign.mutate(String(form.get("assigneeId")));
                }}>
                  <Field label="Redistribuir para">
                    <SelectInput name="assigneeId" defaultValue={ticket.data.assigneeId ?? ""} required>
                      <option value="">Selecione</option>
                      {assignees.data?.map((assignee) => (
                        <option key={assignee.id} value={assignee.id}>{assignee.displayName}</option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Button type="submit" disabled={mutateAssign.isPending}>Redistribuir</Button>
                </form>
              ) : null}
              <form onSubmit={(event) => {
                event.preventDefault();
                mutateClassify.mutate(new FormData(event.currentTarget));
              }}>
                <Field label="Categoria">
                  <SelectInput name="categoryId" defaultValue={ticket.data.categoryId ?? ""} required>
                    <option value="">Selecione</option>
                    {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
                  </SelectInput>
                </Field>
                <Field label="Prioridade">
                  <SelectInput name="priority" defaultValue={ticket.data.priority}>
                    {priorities.map((priority) => <option key={priority} value={priority}>{priorityLabel(priority)}</option>)}
                  </SelectInput>
                </Field>
                <Field label="Prazo">
                  <TextInput name="dueAt" type="datetime-local" />
                </Field>
                <Button type="submit" variant="secondary">Salvar classificação</Button>
              </form>
              <Field label="Mudar status">
                <SelectInput value="" onChange={(event) => mutateStatus.mutate(event.target.value as TicketStatus)}>
                  <option value="">Selecione</option>
                  {operationalNextStatuses(ticket.data.status).map((status) => (
                    <option key={status} value={status}>{statusLabel(status)}</option>
                  ))}
                </SelectInput>
              </Field>
              {mutateStatus.error || mutateAssign.error || mutateClassify.error ? (
                <p className={styles.error} role="alert">Não foi possível aplicar a ação.</p>
              ) : null}
            </div>
          ) : !canOperate ? (
            <div className={styles.actions}>
              {ticket.data.status === "RESOLVED" ? (
                <Button onClick={() => mutateStatus.mutate("IN_PROGRESS")}>Reabrir</Button>
              ) : null}
            </div>
          ) : null}
        </aside>
      </div>
    </section>
  );
}

function operationalNextStatuses(current: TicketStatus): TicketStatus[] {
  const flow: Record<TicketStatus, TicketStatus[]> = {
    OPEN: ["IN_PROGRESS"],
    IN_PROGRESS: ["WAITING_REQUESTER", "RESOLVED"],
    WAITING_REQUESTER: ["IN_PROGRESS"],
    RESOLVED: []
  };
  return flow[current];
}
