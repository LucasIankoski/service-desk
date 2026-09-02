import type { ButtonHTMLAttributes, ReactNode } from "react";
import styles from "./Button.module.css";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "danger" | "ghost";
  icon?: ReactNode;
};

export function Button({ className, variant = "secondary", icon, children, ...props }: ButtonProps) {
  return (
    <button className={[styles.button, styles[variant], className].filter(Boolean).join(" ")} {...props}>
      {icon}
      {children ? <span>{children}</span> : null}
    </button>
  );
}
