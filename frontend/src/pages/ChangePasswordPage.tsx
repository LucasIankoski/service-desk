import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { changePassword } from "../api/auth";
import type { Me } from "../api/types";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { Button } from "../components/Button";
import { Field, TextInput } from "../components/FormField";
import styles from "./LoginPage.module.css";

const schema = z.object({
  password: z.string().min(12, "A senha precisa ter pelo menos 12 caracteres."),
  confirmPassword: z.string().min(12, "Confirme a nova senha.")
}).refine((value) => value.password === value.confirmPassword, {
  message: "As senhas precisam ser iguais.",
  path: ["confirmPassword"]
});

type ChangePasswordForm = z.infer<typeof schema>;

export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const settings = usePublicSettings();
  const queryClient = useQueryClient();
  const form = useForm<ChangePasswordForm>({
    resolver: zodResolver(schema),
    defaultValues: { password: "", confirmPassword: "" }
  });
  const mutation = useMutation({
    mutationFn: (value: ChangePasswordForm) => changePassword(value.password),
    onSuccess: () => {
      queryClient.setQueryData<Me | undefined>(["me"], (current) => (
        current ? { ...current, passwordChangeRequired: false } : current
      ));
      navigate("/tickets", { replace: true });
    }
  });
  const error = mutation.error instanceof Error ? mutation.error.message : undefined;

  return (
    <section className={styles.panel} aria-labelledby="change-password-title">
      <div className={styles.brand}>
        <span><ShieldCheck aria-hidden /></span>
        <div>
          <p>{settings?.institutionName ?? "Central de Serviços"}</p>
          <h1 id="change-password-title">Troque sua senha temporária</h1>
        </div>
      </div>
      <p className={styles.note}>
        Por segurança, esta senha só pode ser vista uma vez pelo administrador. Defina uma senha definitiva para continuar.
      </p>
      <form className={styles.form} onSubmit={form.handleSubmit((value) => mutation.mutate(value))}>
        <Field label="Nova senha" error={form.formState.errors.password?.message}>
          <TextInput type="password" autoComplete="new-password" {...form.register("password")} />
        </Field>
        <Field label="Confirmar senha" error={form.formState.errors.confirmPassword?.message}>
          <TextInput type="password" autoComplete="new-password" {...form.register("confirmPassword")} />
        </Field>
        {error ? <p className={styles.error} role="alert">{error}</p> : null}
        <Button type="submit" variant="primary" disabled={mutation.isPending}>
          Salvar e continuar
        </Button>
      </form>
    </section>
  );
}
