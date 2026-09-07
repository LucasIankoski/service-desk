import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Paperclip, Send } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { createTicket, listCategories } from "../api/tickets";
import { Button } from "../components/Button";
import { Field, SelectInput, TextArea } from "../components/FormField";
import styles from "./NewTicketPage.module.css";

const schema = z.object({
  description: z.string().min(10, "Descreva a demanda com mais detalhes.").max(8000),
  categoryId: z.string().min(1, "Selecione uma categoria.")
});

type TicketForm = z.infer<typeof schema>;

export default function NewTicketPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const categories = useQuery({ queryKey: ["categories"], queryFn: listCategories });
  const [files, setFiles] = useState<File[]>([]);
  const form = useForm<TicketForm>({
    resolver: zodResolver(schema),
    defaultValues: { description: "", categoryId: "" }
  });
  const mutation = useMutation({
    mutationFn: (value: TicketForm) => createTicket({ ...value, files }),
    onSuccess: (ticket) => {
      queryClient.invalidateQueries({ queryKey: ["tickets"] });
      navigate(`/tickets/${ticket.id}`);
    }
  });

  return (
    <section className={styles.page}>
      <header className={styles.heading}>
        <span>Nova demanda</span>
        <h2>Abrir solicitação</h2>
      </header>
      <form className={styles.form} onSubmit={form.handleSubmit((value) => mutation.mutate(value))}>
        <Field label="Categoria" error={form.formState.errors.categoryId?.message}>
          <SelectInput {...form.register("categoryId")} required disabled={!categories.isSuccess || !categories.data.length}>
            <option value="">Selecione</option>
            {categories.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </SelectInput>
        </Field>
        {categories.isError ? <p className={styles.error} role="alert">Não foi possível carregar as categorias.</p> : null}
        {categories.isSuccess && !categories.data.length ? <p role="alert">Nenhuma categoria disponível. Solicite o cadastro ao administrador.</p> : null}
        <Field label="Descrição" error={form.formState.errors.description?.message}>
          <TextArea {...form.register("description")} placeholder="Inclua impacto, local, horário e qualquer evidência útil." />
        </Field>
        <Field label="Anexos">
          <label className={styles.files}>
            <Paperclip aria-hidden />
            <span>{files.length ? `${files.length} arquivo(s) selecionado(s)` : "Selecionar até 5 arquivos"}</span>
            <input
              type="file"
              multiple
              onChange={(event) => setFiles(Array.from(event.target.files ?? []).slice(0, 5))}
            />
          </label>
        </Field>
        {mutation.error ? <p className={styles.error} role="alert">{mutation.error.message}</p> : null}
        <div className={styles.actions}>
          <Button type="submit" variant="primary" icon={<Send />} disabled={mutation.isPending || !categories.isSuccess || !categories.data.length}>
            Enviar solicitação
          </Button>
        </div>
      </form>
    </section>
  );
}
