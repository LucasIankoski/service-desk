import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getAdminSettings } from "../api/settings";
import { apiFetch } from "../api/http";
import type { AdminSettings } from "../api/types";
import { Button } from "./Button";

export function SchoolLogoSettings() {
  const client = useQueryClient();
  const settings = useQuery({ queryKey: ["admin-settings"], queryFn: getAdminSettings });
  const [file, setFile] = useState<File | null>(null);
  const update = useMutation({
    mutationFn: (remove: boolean) => {
      const body = new FormData();
      if (file) body.append("file", file);
      return apiFetch<AdminSettings>("/api/v1/admin/settings/school-logo?version=" + settings.data!.version,
        { method: remove ? "DELETE" : "POST", ...(remove ? {} : { body }) });
    },
    onSuccess: data => {
      client.setQueryData(["admin-settings"], data);
      client.invalidateQueries({ queryKey: ["public-settings"] });
      setFile(null);
    }
  });
  return <section aria-label="Logotipo da escola">
    <h3>Logotipo da escola</h3><p>Exibido no cabeçalho dos planejamentos pedagógicos e no ícone do atalho da aplicação no celular.</p>
    <p>Prefira uma imagem nítida, com pouco texto. Após trocar a logo, pode ser necessário recriar o atalho no celular.</p>
    {settings.data?.schoolLogoUrl && <img src={settings.data.schoolLogoUrl} alt="Logotipo atual" style={{ width: 100, height: 100, objectFit: "contain" }} />}
    <label>Imagem do logotipo <input type="file" accept="image/png,image/jpeg,image/webp" onChange={e => setFile(e.target.files?.[0] ?? null)} /></label>
    <Button disabled={!file || !settings.data || update.isPending} onClick={() => update.mutate(false)}>Salvar logotipo</Button>
    <Button disabled={!settings.data?.schoolLogoUrl || update.isPending} onClick={() => update.mutate(true)}>Remover logotipo</Button>
    {update.error && <p role="alert">{update.error.message}</p>}
    {update.isSuccess && <p role="status">Logotipo atualizado.</p>}
  </section>;
}
