import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { LockKeyhole, Mail, RefreshCcw } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { forgotPassword, login } from "../api/auth";
import { ApiError } from "../api/http";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { Button } from "../components/Button";
import { Field, TextInput } from "../components/FormField";
import styles from "./LoginPage.module.css";

const schema = z.object({
  email: z.string().email("Informe um e-mail válido."),
  password: z.string().min(1, "Informe a senha.")
});

type LoginForm = z.infer<typeof schema>;

export default function LoginPage() {
  const settings = usePublicSettings();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [resetEmail, setResetEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const form = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" }
  });
  const loginMutation = useMutation({
    mutationFn: (value: LoginForm) => login(value.email, value.password),
    onSuccess: (me) => {
      queryClient.setQueryData(["me"], me);
      navigate(me.passwordChangeRequired ? "/change-password" : "/tickets");
    }
  });
  const forgot = useMutation({
    mutationFn: forgotPassword,
    onSuccess: () => setMessage("Se a conta existir e o SMTP estiver ativo, enviaremos as instruções.")
  });

  const error = loginMutation.error instanceof ApiError ? loginMutation.error.message : undefined;

  return (
    <main className={`${styles.screen} ${styles.loginScreen}`}>
      <div
        className={styles.visual}
        style={settings?.loginBackgroundUrl ? { backgroundImage: `url(${settings.loginBackgroundUrl})` } : undefined}
        aria-hidden="true"
      />
      <div className={styles.loginArea}>
        <section className={styles.panel} aria-labelledby="login-title">
          <div className={styles.brand}>
            <span>SD</span>
            <div>
              <p>Central de Serviços</p>
              <h1 id="login-title">{settings?.institutionName ?? "Atendimento interno"}</h1>
            </div>
          </div>
          <form className={styles.form} onSubmit={form.handleSubmit((value) => loginMutation.mutate(value))}>
            <Field label="E-mail" error={form.formState.errors.email?.message}>
              <span className={styles.iconField}>
                <Mail aria-hidden />
                <TextInput autoComplete="email" {...form.register("email")} />
              </span>
            </Field>
            <Field label="Senha" error={form.formState.errors.password?.message}>
              <span className={styles.iconField}>
                <LockKeyhole aria-hidden />
                <TextInput type="password" autoComplete="current-password" {...form.register("password")} />
              </span>
            </Field>
            {error ? <p className={styles.error} role="alert">{error}</p> : null}
            <Button type="submit" variant="primary" disabled={loginMutation.isPending}>
              Entrar
            </Button>
          </form>
          <div className={styles.recovery}>
            <Field label="Recuperar senha">
              <span className={styles.inline}>
                <TextInput
                  value={resetEmail}
                  onChange={(event) => setResetEmail(event.target.value)}
                  placeholder="email@instituicao.gov.br"
                />
                <Button
                  type="button"
                  icon={<RefreshCcw />}
                  onClick={() => forgot.mutate(resetEmail)}
                  disabled={!resetEmail || forgot.isPending}
                  aria-label="Solicitar recuperação"
                />
              </span>
            </Field>
            {message ? <p className={styles.note}>{message}</p> : null}
          </div>
          {(settings?.supportEmail || settings?.supportPhone) ? (
            <footer className={styles.support}>
              {settings.supportEmail ? <span>{settings.supportEmail}</span> : null}
              {settings.supportPhone ? <span>{settings.supportPhone}</span> : null}
            </footer>
          ) : null}
        </section>
      </div>
    </main>
  );
}
