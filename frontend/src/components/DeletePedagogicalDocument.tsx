import { useState } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { useMutation } from "@tanstack/react-query";
import { Button } from "./Button";
import { useSession } from "../hooks/useSession";
import { ApiError } from "../api/http";
import styles from "./DeletePedagogicalDocument.module.css";

interface Props {
  kind: "planejamento" | "projeto";
  label: string;
  dirty: boolean;
  disabled: boolean;
  remove: () => Promise<void>;
  onDeleted: () => void;
  onConflict: () => Promise<void>;
}

export function DeletePedagogicalDocument({ kind, label, dirty, disabled, remove, onDeleted, onConflict }: Props) {
  const session = useSession();
  const [open, setOpen] = useState(false);
  const mutation = useMutation({
    mutationFn: remove,
    onSuccess: onDeleted,
    onError: async error => {
      if (error instanceof ApiError && error.status === 409) await onConflict();
    }
  });
  if (!session.data?.roles.some(role => role === "ADMIN" || role === "MANAGER")) return null;
  return <Dialog.Root open={open} onOpenChange={value => { if (!mutation.isPending) { setOpen(value); mutation.reset(); } }}>
    <Dialog.Trigger asChild><Button variant="danger" disabled={disabled}>Excluir {kind}</Button></Dialog.Trigger>
    <Dialog.Portal>
      <Dialog.Overlay className={styles.overlay} />
      <Dialog.Content className={styles.confirm} onEscapeKeyDown={event => { if (mutation.isPending) event.preventDefault(); }}
        onPointerDownOutside={event => { if (mutation.isPending) event.preventDefault(); }}>
        <Dialog.Title>Excluir {kind}?</Dialog.Title>
        <Dialog.Description>O {kind} “{label}” será removido definitivamente. Esta ação não pode ser desfeita.
          {dirty && " As alterações não salvas também serão descartadas."}</Dialog.Description>
        {mutation.error && <p role="alert">{mutation.error instanceof ApiError && mutation.error.status === 409
          ? "Este registro foi alterado por outra pessoa. Cancele a exclusão e confira a versão atual. Seu preenchimento foi preservado."
          : mutation.error.message}</p>}
        <div className={styles.actions}>
          <Button disabled={mutation.isPending} onClick={() => { setOpen(false); mutation.reset(); }}>Cancelar</Button>
          <Button variant="danger" disabled={disabled || mutation.isPending || (mutation.error instanceof ApiError && mutation.error.status === 409)}
            onClick={() => mutation.mutate()}>{mutation.isPending ? "Excluindo…" : "Confirmar exclusão"}</Button>
        </div>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog.Root>;
}
