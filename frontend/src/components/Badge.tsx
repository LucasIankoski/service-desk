import type { ReactNode } from "react";
import styles from "./Badge.module.css";

export function Badge({ tone = "neutral", children }: { tone?: "neutral" | "blue" | "teal" | "amber" | "red"; children: ReactNode }) {
  return <span className={[styles.badge, styles[tone]].join(" ")}>{children}</span>;
}
