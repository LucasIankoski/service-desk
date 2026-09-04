import { lazy, Suspense, useEffect } from "react";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { getPublicSettings } from "../api/settings";
import type { Theme } from "../api/types";
import { PublicSettingsContext } from "./PublicSettingsContext";
import { AppShell } from "../components/AppShell";
import { LoadingScreen } from "../components/LoadingScreen";

const LoginPage = lazy(() => import("../pages/LoginPage"));
const TicketListPage = lazy(() => import("../pages/TicketListPage"));
const NewTicketPage = lazy(() => import("../pages/NewTicketPage"));
const TicketDetailPage = lazy(() => import("../pages/TicketDetailPage"));
const AdminPage = lazy(() => import("../pages/AdminPage"));
const ResetPasswordPage = lazy(() => import("../pages/ResetPasswordPage"));
const ChangePasswordPage = lazy(() => import("../pages/ChangePasswordPage"));
const AgendaPage = lazy(() => import("../pages/AgendaPage"));

const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />
  },
  {
    path: "/reset-password",
    element: <ResetPasswordPage />
  },
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/tickets" replace /> },
      { path: "tickets", element: <TicketListPage /> },
      { path: "tickets/new", element: <NewTicketPage /> },
      { path: "tickets/:id", element: <TicketDetailPage /> },
      { path: "agenda", element: <AgendaPage /> },
      { path: "change-password", element: <ChangePasswordPage /> },
      { path: "admin", element: <AdminPage /> }
    ]
  }
]);

export function App() {
  const publicSettings = useQuery({
    queryKey: ["public-settings"],
    queryFn: getPublicSettings
  });

  useEffect(() => {
    if (publicSettings.data?.theme) {
      applyTheme(publicSettings.data.theme);
    }
  }, [publicSettings.data?.theme]);

  return (
    <Suspense fallback={<LoadingScreen />}>
      <PublicSettingsContext.Provider value={publicSettings.data}>
        <RouterProvider router={router} />
      </PublicSettingsContext.Provider>
    </Suspense>
  );
}

function applyTheme(theme: Theme) {
  const root = document.documentElement;
  root.style.setProperty("--color-primary", theme.primaryColor);
  root.style.setProperty("--color-accent", theme.accentColor);
  root.style.setProperty("--color-sidebar", theme.sidebarColor);
  root.style.setProperty("--color-canvas", theme.canvasColor);
}
