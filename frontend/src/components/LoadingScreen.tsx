import styles from "./LoadingScreen.module.css";

export function LoadingScreen() {
  return (
    <main className={styles.screen} aria-live="polite">
      <div className={styles.mark} />
      <span>Carregando</span>
    </main>
  );
}
