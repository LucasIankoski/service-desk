import type { TicketStatus } from "../api/types";
import { statusLabel } from "./ticketPresentation";
import styles from "./StatusTrail.module.css";

const path: TicketStatus[] = ["OPEN", "TRIAGE", "IN_PROGRESS", "WAITING_REQUESTER", "RESOLVED", "CLOSED"];

export function StatusTrail({ status }: { status: TicketStatus }) {
  const activeIndex = Math.max(path.indexOf(status), 0);
  return (
    <ol className={styles.trail} aria-label="Trilha de atendimento">
      {path.map((item, index) => (
        <li key={item} className={index <= activeIndex ? styles.done : styles.step}>
          <span>{index + 1}</span>
          <p>{statusLabel(item)}</p>
        </li>
      ))}
      {status === "CANCELED" ? <li className={styles.canceled}><span>!</span><p>Cancelada</p></li> : null}
    </ol>
  );
}
