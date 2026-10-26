import { NavLink } from "react-router";
import { usePedagogicalScope } from "../hooks/usePedagogicalScope";
import { BookOpen, Images, FolderOpen } from "lucide-react";
import styles from "../pages/PedagogicalPage.module.css";

export function PedagogicalModules({ classId, legacyAee = false }: { classId: string; legacyAee?: boolean }) {
  const scope = usePedagogicalScope();
  const base = scope.classBase(classId);
  return <nav className={styles.modules} aria-label="Áreas da turma">
    <NavLink to={legacyAee ? `${base}?historico=1` : base} end><BookOpen size={20} />Planejamento Pedagógico</NavLink>
    <NavLink to={`${base}/projetos`}><FolderOpen size={20} />Projetos</NavLink>
    <NavLink to={`${base}/registros`}><Images size={20} />Registros</NavLink>
    {scope.scoped && <NavLink to={`${base}/documentos`}><FolderOpen size={20} />Documentos</NavLink>}
  </nav>;
}
