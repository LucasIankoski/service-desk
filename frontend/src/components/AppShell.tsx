import { Bell, CalendarDays, Home, LogOut, Plus, Settings, Ticket } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { logout } from "../api/auth";
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

  const canAdmin = session.data.roles.includes("ADMIN");
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
          {canAgenda ? <NavItem to="/agenda" icon={<CalendarDays />} label="Agenda" /> : null}
          {canAdmin ? <NavItem to="/admin" icon={<Settings />} label="Administrador" /> : null}
        </nav>
      </aside>

      <div className={styles.workspace}>
        <header className={styles.topbar}>
          <div>
            <span className={styles.kicker}>Central de Serviços</span>
            <h1>{settings?.institutionName ?? "Atendimento interno"}</h1>
          </div>
          <div className={styles.topActions}>
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
        {canAgenda ? <NavItem to="/agenda" icon={<CalendarDays />} label="Agenda" /> : null}
        {canAdmin ? <NavItem to="/admin" icon={<Settings />} label="Admin" /> : null}
      </nav>
    </div>
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
