import { NavLink } from "react-router";
import { BookOpen, Images, FolderOpen } from "lucide-react";
import styles from "../pages/PedagogicalPage.module.css";

export function PedagogicalModules({ classId }: { classId: string }) {
  const base = `/pedagogico/turmas/${classId}`;
  return <nav className={styles.modules} aria-label="Áreas da turma">
    <NavLink to={base} end><BookOpen size={20} />Planejamento Pedagógico</NavLink>
    <NavLink to={`${base}/projetos`}><FolderOpen size={20} />Projetos</NavLink>
    <NavLink to={`${base}/registros`}><Images size={20} />Registros</NavLink>
  </nav>;
}
