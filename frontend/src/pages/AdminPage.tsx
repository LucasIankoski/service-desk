import * as Tabs from "@radix-ui/react-tabs";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle, KeyRound, MailCheck, Palette, Plus, RotateCcw, Shield, Users } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Navigate } from "react-router";
import {
  createCategory,
  createUser,
  anonymizeUser,
  listAdminCategories,
  listUsers,
  resetTemporaryPassword,
  updateCategory,
  updateUser
} from "../api/admin";
import { listAuditEvents } from "../api/audit";
import { getAdminSettings, previewTheme, testSmtp, updateGeneralSettings, updateLoginBackground, updateSmtp, updateTheme } from "../api/settings";
import type { Role, Theme, User } from "../api/types";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Field, SelectInput, TextInput } from "../components/FormField";
import styles from "./AdminPage.module.css";
import { useSession } from "../hooks/useSession";

const allRoles: Role[] = ["REQUESTER", "AGENT", "MANAGER", "ADMIN"];
const roleLabels: Record<Role, string> = {
  REQUESTER: "Solicitante",
  AGENT: "Atendente",
  MANAGER: "Gestor",
  ADMIN: "Administrador"
};

export default function AdminPage() {
  const session = useSession();
  if (session.data && !session.data.roles.includes("ADMIN")) {
    return <Navigate to="/tickets" replace />;
  }
  return (
    <section className={styles.page}>
      <header className={styles.heading}>
        <span>Administração</span>
        <h2>Configurações da Central</h2>
      </header>
      <Tabs.Root defaultValue="users" className={styles.tabs}>
        <Tabs.List className={styles.tabList} aria-label="Áreas administrativas">
          <Tabs.Trigger value="users"><Users /> Usuários</Tabs.Trigger>
          <Tabs.Trigger value="categories"><Shield /> Categorias</Tabs.Trigger>
          <Tabs.Trigger value="general"><CheckCircle /> Geral</Tabs.Trigger>
          <Tabs.Trigger value="theme"><Palette /> Tema</Tabs.Trigger>
          <Tabs.Trigger value="smtp"><MailCheck /> SMTP</Tabs.Trigger>
          <Tabs.Trigger value="audit"><KeyRound /> Auditoria</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="users"><UsersTab /></Tabs.Content>
        <Tabs.Content value="categories"><CategoriesTab /></Tabs.Content>
        <Tabs.Content value="general"><GeneralTab /></Tabs.Content>
        <Tabs.Content value="theme"><ThemeTab /></Tabs.Content>
        <Tabs.Content value="smtp"><SmtpTab /></Tabs.Content>
        <Tabs.Content value="audit"><AuditTab /></Tabs.Content>
      </Tabs.Root>
    </section>
  );
}

function UsersTab() {
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ["admin-users"], queryFn: () => listUsers() });
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: createUser,
    onSuccess: (result) => {
      setTemporaryPassword(result.temporaryPassword);
      queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    }
  });
  const reset = useMutation({
    mutationFn: resetTemporaryPassword,
    onSuccess: (result) => setTemporaryPassword(result.temporaryPassword)
  });
  const update = useMutation({
    mutationFn: ({ id, input }: { id: string; input: { displayName: string; roles: Role[]; active: boolean } }) =>
      updateUser(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-users"] })
  });
  const anonymize = useMutation({
    mutationFn: anonymizeUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-users"] })
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const roles = allRoles.filter((role) => form.get(role));
    create.mutate({
      email: String(form.get("email")),
      displayName: String(form.get("displayName")),
      roles
    });
    event.currentTarget.reset();
  }

  return (
    <div className={styles.panel}>
      <form className={styles.adminForm} onSubmit={onSubmit}>
        <Field label="Nome">
          <TextInput name="displayName" required maxLength={120} />
        </Field>
        <Field label="E-mail">
          <TextInput name="email" type="email" required />
        </Field>
        <div className={styles.checkGrid} aria-label="Perfis">
          {allRoles.map((role) => (
            <label key={role}><input type="checkbox" name={role} defaultChecked={role === "REQUESTER"} /> {roleLabels[role]}</label>
          ))}
        </div>
        <Button type="submit" variant="primary" icon={<Plus />}>Criar usuário</Button>
      </form>
      {temporaryPassword ? <p className={styles.secret}>Senha temporária: <code>{temporaryPassword}</code></p> : null}
      <div className={styles.table}>
        {users.data?.content.map((user) => (
          <UserEditor
            key={user.id}
            user={user}
            pending={update.isPending || reset.isPending || anonymize.isPending}
            onSave={(input) => update.mutate({ id: user.id, input })}
            onReset={() => reset.mutate(user.id)}
            onAnonymize={() => {
              if (window.confirm(`Anonimizar permanentemente a conta de ${user.displayName}?`)) {
                anonymize.mutate(user.id);
              }
            }}
          />
        ))}
      </div>
      {create.error || update.error || reset.error || anonymize.error ? (
        <p className={styles.error} role="alert">
          {(create.error ?? update.error ?? reset.error ?? anonymize.error)?.message}
        </p>
      ) : null}
    </div>
  );
}

function UserEditor({
  user,
  pending,
  onSave,
  onReset,
  onAnonymize
}: {
  user: User;
  pending: boolean;
  onSave: (input: { displayName: string; roles: Role[]; active: boolean }) => void;
  onReset: () => void;
  onAnonymize: () => void;
}) {
  return (
    <form className={styles.userRow} onSubmit={(event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      onSave({
        displayName: String(form.get("displayName")),
        roles: allRoles.filter((role) => form.get(role)),
        active: form.get("active") === "on"
      });
    }}>
      <div className={styles.userIdentity}>
        <TextInput name="displayName" defaultValue={user.displayName} required maxLength={120} />
        <span>{user.email}</span>
      </div>
      <div className={styles.roleEditor} aria-label={`Perfis de ${user.displayName}`}>
        {allRoles.map((role) => (
          <label key={role}>
            <input type="checkbox" name={role} defaultChecked={user.roles.includes(role)} />
            {roleLabels[role]}
          </label>
        ))}
        <label><input type="checkbox" name="active" defaultChecked={user.active} /> Conta ativa</label>
      </div>
      <div className={styles.rowActions}>
        <Button type="submit" disabled={pending}>Salvar</Button>
        <Button type="button" icon={<RotateCcw />} onClick={onReset} disabled={pending}>Senha</Button>
        {!user.anonymized ? (
          <Button type="button" variant="danger" onClick={onAnonymize} disabled={pending}>Anonimizar</Button>
        ) : null}
      </div>
    </form>
  );
}

function CategoriesTab() {
  const queryClient = useQueryClient();
  const categories = useQuery({ queryKey: ["admin-categories"], queryFn: listAdminCategories });
  const create = useMutation({
    mutationFn: createCategory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-categories"] })
  });
  const update = useMutation({
    mutationFn: ({ id, name, active }: { id: string; name: string; active: boolean }) => updateCategory(id, { name, active }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-categories"] })
  });

  return (
    <div className={styles.panel}>
      <form className={styles.inlineForm} onSubmit={(event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        create.mutate({ name: String(form.get("name")), active: true });
        event.currentTarget.reset();
      }}>
        <Field label="Nova categoria"><TextInput name="name" required maxLength={100} /></Field>
        <Button type="submit" variant="primary" icon={<Plus />}>Adicionar</Button>
      </form>
      <div className={styles.table}>
        {categories.data?.map((category) => (
          <article key={category.id} className={styles.row}>
            <strong>{category.name}</strong>
            <Badge tone={category.active ? "teal" : "neutral"}>{category.active ? "Ativa" : "Inativa"}</Badge>
            <Button onClick={() => update.mutate({ id: category.id, name: category.name, active: !category.active })}>
              {category.active ? "Inativar" : "Ativar"}
            </Button>
          </article>
        ))}
      </div>
    </div>
  );
}

function GeneralTab() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ["admin-settings"], queryFn: getAdminSettings });
  const mutation = useMutation({
    mutationFn: updateGeneralSettings,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
      queryClient.invalidateQueries({ queryKey: ["public-settings"] });
    }
  });

  if (!settings.data) return <p className={styles.state}>Carregando configurações.</p>;

  return (
    <form className={styles.panelForm} onSubmit={(event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      mutation.mutate({
        institutionName: String(form.get("institutionName")),
        supportEmail: String(form.get("supportEmail")),
        supportPhone: String(form.get("supportPhone")),
        timezoneName: String(form.get("timezoneName")),
        attachmentLimitMb: Number(form.get("attachmentLimitMb")),
        reopenDays: Number(form.get("reopenDays")),
        deadlineWarningHours: Number(form.get("deadlineWarningHours")),
        version: settings.data.version
      });
    }}>
      <Field label="Instituição"><TextInput name="institutionName" defaultValue={settings.data.institutionName} required /></Field>
      <Field label="E-mail de contato"><TextInput name="supportEmail" type="email" defaultValue={settings.data.supportEmail ?? ""} /></Field>
      <Field label="Telefone de contato"><TextInput name="supportPhone" defaultValue={settings.data.supportPhone ?? ""} /></Field>
      <Field label="Fuso horário"><TextInput name="timezoneName" defaultValue={settings.data.timezoneName} required /></Field>
      <div className={styles.threeCols}>
        <Field label="MiB por anexo"><TextInput name="attachmentLimitMb" type="number" min={1} max={25} defaultValue={settings.data.attachmentLimitMb} /></Field>
        <Field label="Dias para reabrir"><TextInput name="reopenDays" type="number" min={1} max={30} defaultValue={settings.data.reopenDays} /></Field>
        <Field label="Aviso de prazo (h)"><TextInput name="deadlineWarningHours" type="number" min={1} max={168} defaultValue={settings.data.deadlineWarningHours} /></Field>
      </div>
      <Button type="submit" variant="primary" disabled={mutation.isPending}>Salvar geral</Button>
      {mutation.error ? <p className={styles.error}>{mutation.error.message}</p> : null}
    </form>
  );
}

function ThemeTab() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ["admin-settings"], queryFn: getAdminSettings });
  const [theme, setTheme] = useState<Theme | null>(null);
  const [backgroundFile, setBackgroundFile] = useState<File | null>(null);
  const effectiveTheme = theme ?? settings.data?.theme;
  const preview = useMutation({ mutationFn: previewTheme });
  const save = useMutation({
    mutationFn: () => updateTheme(effectiveTheme!, settings.data!.version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
      queryClient.invalidateQueries({ queryKey: ["public-settings"] });
    }
  });
  const saveBackground = useMutation({
    mutationFn: () => updateLoginBackground(backgroundFile!),
    onSuccess: () => {
      setBackgroundFile(null);
      queryClient.invalidateQueries({ queryKey: ["admin-settings"] });
      queryClient.invalidateQueries({ queryKey: ["public-settings"] });
    }
  });

  if (!settings.data || !effectiveTheme) return <p className={styles.state}>Carregando tema.</p>;
  const fields: Array<[keyof Theme, string]> = [
    ["primaryColor", "Primária"],
    ["accentColor", "Destaque"],
    ["sidebarColor", "Sidebar"],
    ["canvasColor", "Fundo"]
  ];

  return (
    <div className={styles.panel}>
      <div className={styles.swatches}>
        {fields.map(([key, label]) => (
          <Field key={key} label={label}>
            <input
              className={styles.color}
              type="color"
              value={effectiveTheme[key]}
              onChange={(event) => setTheme({ ...effectiveTheme, [key]: event.target.value })}
            />
          </Field>
        ))}
      </div>
      <div className={styles.themePreview} style={{
        background: effectiveTheme.canvasColor,
        borderColor: effectiveTheme.primaryColor
      }}>
        <aside style={{ background: effectiveTheme.sidebarColor }}>SD</aside>
        <main>
          <strong style={{ color: effectiveTheme.primaryColor }}>Prévia operacional</strong>
          <button style={{ background: effectiveTheme.accentColor }}>Ação principal</button>
        </main>
      </div>
      <div className={styles.actionRow}>
        <Button onClick={() => preview.mutate(effectiveTheme)}>Validar contraste</Button>
        <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>Publicar tema</Button>
      </div>
      <div className={styles.backgroundEditor}>
        <Field label="Plano de fundo do login (JPG, PNG ou WebP)">
          <TextInput
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={(event) => setBackgroundFile(event.target.files?.[0] ?? null)}
          />
        </Field>
        <Button
          type="button"
          onClick={() => saveBackground.mutate()}
          disabled={!backgroundFile || saveBackground.isPending}
        >
          Publicar imagem
        </Button>
        {settings.data.loginBackgroundConfigured ? <span>Imagem de login configurada.</span> : null}
      </div>
      {preview.data?.warnings.length ? <ul className={styles.warningList}>{preview.data.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul> : null}
      {preview.data?.valid ? <p className={styles.ok}>Contraste aprovado.</p> : null}
      {save.error || saveBackground.error ? (
        <p className={styles.error} role="alert">{(save.error ?? saveBackground.error)?.message}</p>
      ) : null}
    </div>
  );
}

function SmtpTab() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ["admin-settings"], queryFn: getAdminSettings });
  const save = useMutation({
    mutationFn: updateSmtp,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-settings"] })
  });
  const test = useMutation({ mutationFn: testSmtp });

  if (!settings.data) return <p className={styles.state}>Carregando SMTP.</p>;

  return (
    <form className={styles.panelForm} onSubmit={(event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      save.mutate({
        host: String(form.get("host")),
        port: Number(form.get("port")),
        tls: form.get("tls") === "on",
        fromName: String(form.get("fromName")),
        fromAddress: String(form.get("fromAddress")),
        username: String(form.get("username")),
        password: String(form.get("password") || ""),
        version: settings.data.version
      });
    }}>
      <div className={styles.twoCols}>
        <Field label="Host"><TextInput name="host" defaultValue={settings.data.smtp.host ?? ""} /></Field>
        <Field label="Porta"><TextInput name="port" type="number" defaultValue={settings.data.smtp.port ?? 587} /></Field>
      </div>
      <label className={styles.toggle}><input type="checkbox" name="tls" defaultChecked={settings.data.smtp.tls} /> Usar STARTTLS</label>
      <div className={styles.twoCols}>
        <Field label="Nome remetente"><TextInput name="fromName" defaultValue={settings.data.smtp.fromName ?? ""} /></Field>
        <Field label="E-mail remetente"><TextInput name="fromAddress" type="email" defaultValue={settings.data.smtp.fromAddress ?? ""} /></Field>
      </div>
      <div className={styles.twoCols}>
        <Field label="Usuário"><TextInput name="username" defaultValue={settings.data.smtp.username ?? ""} /></Field>
        <Field label={settings.data.smtp.passwordConfigured ? "Nova senha SMTP" : "Senha SMTP"}>
          <TextInput name="password" type="password" autoComplete="new-password" />
        </Field>
      </div>
      <div className={styles.actionRow}>
        <Button type="submit" variant="primary" disabled={save.isPending}>Salvar SMTP</Button>
        <Button type="button" icon={<MailCheck />} onClick={() => test.mutate()} disabled={test.isPending}>Testar conexão</Button>
      </div>
      {save.error || test.error ? <p className={styles.error}>{(save.error ?? test.error)?.message}</p> : null}
      {test.isSuccess ? <p className={styles.ok}>Conexão SMTP validada.</p> : null}
    </form>
  );
}

function AuditTab() {
  const audit = useQuery({ queryKey: ["audit"], queryFn: () => listAuditEvents() });
  const rows = useMemo(() => audit.data?.content ?? [], [audit.data]);
  return (
    <div className={styles.panel}>
      <div className={styles.table}>
        {rows.map((event) => (
          <article key={event.id} className={styles.row}>
            <div>
              <strong>{event.action}</strong>
              <span>{event.entityType} · {event.entityId ?? "sem entidade"}</span>
            </div>
            <span>{new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(event.createdAt))}</span>
          </article>
        ))}
      </div>
    </div>
  );
}
