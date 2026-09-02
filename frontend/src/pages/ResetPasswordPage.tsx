import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router";
import { z } from "zod";
import { resetPassword } from "../api/auth";
import { Button } from "../components/Button";
import { Field, TextInput } from "../components/FormField";
import { usePublicSettings } from "../app/PublicSettingsContext";
import styles from "./LoginPage.module.css";

const schema = z.object({
  password: z.string().min(12, "A senha precisa ter pelo menos 12 caracteres.")
});

type ResetForm = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const navigate = useNavigate();
  const settings = usePublicSettings();
  const form = useForm<ResetForm>({ resolver: zodResolver(schema), defaultValues: { password: "" } });
  const mutation = useMutation({
    mutationFn: (value: ResetForm) => resetPassword(token, value.password),
    onSuccess: () => navigate("/login")
  });

  return (
    <main className={styles.screen}>
      <section className={styles.panel} aria-labelledby="reset-title">
        <div className={styles.brand}>
          <span><KeyRound aria-hidden /></span>
          <div>
            <p>{settings?.institutionName ?? "Central de Serviços"}</p>
            <h1 id="reset-title">Redefinir senha</h1>
          </div>
        </div>
        <form className={styles.form} onSubmit={form.handleSubmit((value) => mutation.mutate(value))}>
          <Field label="Nova senha" error={form.formState.errors.password?.message}>
            <TextInput type="password" autoComplete="new-password" {...form.register("password")} />
          </Field>
          {mutation.error ? <p className={styles.error} role="alert">{mutation.error.message}</p> : null}
          <Button type="submit" variant="primary" disabled={!token || mutation.isPending}>
            Atualizar senha
          </Button>
        </form>
        <p className={styles.note}><Link to="/login">Voltar para o login</Link></p>
      </section>
    </main>
  );
}
