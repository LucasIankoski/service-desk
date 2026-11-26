import { BookOpen, Bell, Building2, CalendarDays, Camera, ListChecks, Home, LogOut, Plus, Settings, Ticket } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { logout } from "../api/auth";
import { setActiveUnit } from "../api/http";
import type { UnitAccess } from "../api/types";
import { listNotifications, markAllNotificationsRead, unreadNotificationCount } from "../api/notifications";
import { useSession, isUnauthorized } from "../hooks/useSession";
import { usePublicSettings } from "../app/PublicSettingsContext";
import { Button } from "./Button";
import styles from "./AppShell.module.css";

export function AppShell() {
  const session = useSession();
  const settings = usePublicSettings();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const notices = useQuery({ queryKey: ["notifications"], queryFn: listNotifications, enabled: !!session.data });
  const unread = useQuery({ queryKey: ["notifications", "unread"], queryFn: unreadNotificationCount, enabled: !!session.data });
  const markAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    }
  });
  const signOut = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.clear();
      navigate("/login");
    }
  });

  if (session.isLoading) {
    return null;
  }
  if (isUnauthorized(session.error)) {
    return <Navigate to="/login" replace />;
  }
  if (!session.data) {
    return <Navigate to="/login" replace />;
  }
  if (session.data.passwordChangeRequired && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }
  const units = session.data.units;
  if (units && !session.data.activeUnitId && !session.data.passwordChangeRequired) {
    return <UnitPicker units={units} onSignOut={() => signOut.mutate()} />;
  }
  // Com uma unidade só, o topo continua mostrando a instituição, como antes da 017.
  const activeUnit = units && units.length > 1 ? units.find((unit) => unit.id === session.data.activeUnitId) : undefined;
  const unitCodes = new Map(units?.map((unit) => [unit.id, unit.code]));

  const canAdmin = session.data.roles.includes("ADMIN");
  const canPedagogical = session.data.roles.some(role => ["ADMIN", "MANAGER", "REQUESTER"].includes(role));
  const canAgenda = session.data.roles.some((role) => role === "MANAGER" || role === "REQUESTER");

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar} aria-label="Navegação principal">
        <div className={styles.brand}>
          <span className={styles.brandMark}>SD</span>
          <strong>{settings?.institutionName ?? "Central de Serviços"}</strong>
        </div>
        <nav className={styles.nav}>
          <NavItem to="/tickets" icon={<Home />} label="Fila" />
          <NavItem to="/tickets/new" icon={<Plus />} label="Nova solicitação" />
          {canPedagogical ? <NavItem to="/pedagogico" icon={<BookOpen />} label="Pedagógico" /> : null}
          {canPedagogical ? <NavItem to="/registros" icon={<Camera />} label="Registros" /> : null}
          {canAgenda ? <NavItem to="/agenda" icon={<CalendarDays />} label="Agenda" /> : null}
          {session.data.roles.includes("MANAGER") ? <NavItem to="/tarefas" icon={<ListChecks />} label="Tarefas" /> : null}
          {canAdmin ? <NavItem to="/admin" icon={<Settings />} label="Administrador" /> : null}
        </nav>
      </aside>

      <div className={styles.workspace}>
        <header className={styles.topbar}>
          <div>
            <span className={styles.kicker}>Central de Serviços</span>
            <h1>{activeUnit?.name ?? settings?.institutionName ?? "Atendimento interno"}</h1>
          </div>
          <div className={styles.topActions}>
            {units && units.length > 1 ? (
              <label className={styles.unitSwitcher}>
                <Building2 aria-hidden="true" />
                <span className={styles.srOnly}>Unidade</span>
                <select value={session.data.activeUnitId ?? ""} onChange={(event) => switchUnit(event.target.value)}>
                  {units.map((unit) => <option key={unit.id} value={unit.id}>{unit.name}</option>)}
                </select>
              </label>
            ) : null}
            <details className={styles.notifications}>
              <summary aria-label="Abrir notificações">
                <Bell />
                {unread.data?.count ? <span>{unread.data.count}</span> : null}
              </summary>
              <div className={styles.noticePanel}>
                <div className={styles.noticeHeader}>
                  <strong>Notificações</strong>
                  <button type="button" onClick={() => markAll.mutate()}>marcar lidas</button>
                </div>
                {notices.data?.content.length ? notices.data.content.map((notice) => (
                  <article key={notice.id} className={notice.read ? styles.notice : styles.noticeUnread}>
                    {units && units.length > 1 && notice.unitId ? (
                      <span className={styles.noticeUnit}>{unitCodes.get(notice.unitId) ?? ""}</span>
                    ) : null}
                    <strong>{notice.title}</strong>
                    <p>{notice.message}</p>
                  </article>
                )) : <p className={styles.emptyNotice}>Nada novo por aqui.</p>}
              </div>
            </details>
            <span className={styles.user}>{session.data.displayName}</span>
            <Button variant="ghost" icon={<LogOut />} onClick={() => signOut.mutate()} aria-label="Sair" />
          </div>
        </header>

        <main className={styles.content}>
          <Outlet />
        </main>
      </div>

      <nav className={styles.bottomNav} aria-label="Navegação mobile">
        <NavItem to="/tickets" icon={<Ticket />} label="Fila" />
        <NavItem to="/tickets/new" icon={<Plus />} label="Abrir" />
        {canPedagogical ? <NavItem to="/pedagogico" icon={<BookOpen />} label="Pedagógico" /> : null}
        {canPedagogical ? <NavItem to="/registros" icon={<Camera />} label="Registros" /> : null}
        {canAgenda ? <NavItem to="/agenda" icon={<CalendarDays />} label="Agenda" /> : null}
        {session.data.roles.includes("MANAGER") ? <NavItem to="/tarefas" icon={<ListChecks />} label="Tarefas" /> : null}
        {canAdmin ? <NavItem to="/admin" icon={<Settings />} label="Admin" /> : null}
      </nav>
    </div>
  );
}

/** Troca a unidade desta aba e recarrega, para que nenhum dado da unidade anterior fique em tela. */
function switchUnit(unitId: string) {
  setActiveUnit(unitId);
  window.location.assign("/tickets");
}

function UnitPicker({ units, onSignOut }: { units: UnitAccess[]; onSignOut: () => void }) {
  return (
    <main className={styles.unitPicker}>
      <section className={styles.unitPanel} aria-labelledby="unit-picker-title">
        <h1 id="unit-picker-title">{units.length ? "Escolha a unidade" : "Nenhuma unidade disponível"}</h1>
        {units.length ? (
          <div className={styles.unitChoices}>
            {units.map((unit) => (
              <Button key={unit.id} icon={<Building2 />} onClick={() => switchUnit(unit.id)}>
                {unit.name}
              </Button>
            ))}
          </div>
        ) : (
          <p>Sua conta não está vinculada a nenhuma unidade ativa. Procure o administrador.</p>
        )}
        <Button variant="ghost" icon={<LogOut />} onClick={onSignOut}>Sair</Button>
      </section>
    </main>
  );
}

function NavItem({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  return (
    <NavLink to={to} className={({ isActive }) => isActive ? styles.activeLink : styles.link}>
      {icon}
      <span>{label}</span>
    </NavLink>
  );
}
