import { useEffect, useRef, useState } from "react";
import { getDocument, GlobalWorkerOptions, PasswordResponses, TextLayer } from "pdfjs-dist";
import type { PDFDocumentProxy } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { Button } from "./Button";
import "pdfjs-dist/web/pdf_viewer.css";
import styles from "./PdfViewer.module.css";

GlobalWorkerOptions.workerSrc = workerUrl;

export default function PdfViewer({ url, name }: { url: string; name: string }) {
  const [document, setDocument] = useState<PDFDocumentProxy | null>(null);
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState<number | "fit">("fit");
  const [width, setWidth] = useState(600);
  const [error, setError] = useState("");
  const [rendering, setRendering] = useState(false);
  const [passwordPrompt, setPasswordPrompt] = useState("");
  const [password, setPassword] = useState("");
  const passwordCallback = useRef<((password: string) => void) | null>(null);
  const container = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const text = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let active = true;
    const task = getDocument({ url, withCredentials: true, cMapUrl: "/pdfjs/cmaps/", cMapPacked: true, standardFontDataUrl: "/pdfjs/standard_fonts/", wasmUrl: "/pdfjs/wasm/", enableXfa: false });
    task.onPassword = (callback: (value: string) => void, reason: number) => {
      if (!active) return;
      passwordCallback.current = callback;
      setPasswordPrompt(reason === PasswordResponses.INCORRECT_PASSWORD ? "Senha incorreta. Tente novamente." : "Este PDF está protegido por senha.");
    };
    task.promise.then(pdf => { if (active) { setDocument(pdf); setPasswordPrompt(""); setPassword(""); } }).catch(() => { if (active) setError("Não foi possível abrir este PDF. O arquivo pode estar inválido ou seu acesso pode ter mudado."); });
    return () => { active = false; passwordCallback.current = null; void task.destroy(); };
  }, [url]);
  useEffect(() => {
    if (!container.current) return;
    const observer = new ResizeObserver(entries => setWidth(Math.max(100, entries[0].contentRect.width - 8)));
    observer.observe(container.current); return () => observer.disconnect();
  }, []);
  useEffect(() => {
    if (!document || !canvas.current || !text.current) return;
    let cancelled = false;
    let cancelRender: (() => void) | undefined;
    let textLayer: TextLayer | undefined;
    setRendering(true); setError("");
    void (async () => {
      try {
        const pdfPage = await document.getPage(page);
        if (cancelled) return;
        const natural = pdfPage.getViewport({ scale: 1 });
        const viewport = pdfPage.getViewport({ scale: zoom === "fit" ? width / natural.width : zoom });
        const target = canvas.current!, layer = text.current!;
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        target.width = Math.floor(viewport.width * ratio); target.height = Math.floor(viewport.height * ratio);
        target.style.width = `${viewport.width}px`; target.style.height = `${viewport.height}px`;
        layer.replaceChildren(); layer.style.setProperty("--scale-factor", String(viewport.scale));
        layer.style.width = `${viewport.width}px`; layer.style.height = `${viewport.height}px`;
        const render = pdfPage.render({ canvas: target, viewport, transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0] });
        cancelRender = () => render.cancel();
        const renderText = pdfPage.getTextContent().then(content => {
          if (cancelled) return;
          textLayer = new TextLayer({ textContentSource: content, container: layer, viewport });
          return textLayer.render();
        });
        await Promise.all([render.promise, renderText]);
      } catch { if (!cancelled) setError("Não foi possível exibir esta página do PDF."); }
      finally { if (!cancelled) setRendering(false); }
    })();
    return () => { cancelled = true; cancelRender?.(); textLayer?.cancel(); };
  }, [document, page, zoom, width]);
  return <section className={styles.viewer} aria-label={`Visualizador PDF: ${name}`}>
    {passwordPrompt && <form onSubmit={e => { e.preventDefault(); passwordCallback.current?.(password); setPassword(""); }}>
      <p>{passwordPrompt}</p><label>Senha do PDF<input autoComplete="off" type="password" value={password} onChange={e => setPassword(e.target.value)} /></label><Button type="submit">Abrir PDF</Button>
      <p>A senha é utilizada somente neste navegador.</p>
    </form>}
    {error && <p role="alert">{error}</p>}
    {!document && !error && !passwordPrompt && <p role="status">Carregando PDF…</p>}
    {document && <div className={styles.toolbar}>
      <Button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>Página anterior</Button>
      <span aria-live="polite">Página {page} de {document.numPages}</span>
      <Button disabled={page >= document.numPages} onClick={() => setPage(p => p + 1)}>Próxima página</Button>
      <label>Zoom<select aria-label="Zoom" value={zoom} onChange={e => setZoom(e.target.value === "fit" ? "fit" : Number(e.target.value))}><option value="fit">Ajustar à largura</option>{[0.5, 0.75, 1, 1.25, 1.5, 2].map(v => <option key={v} value={v}>{v * 100}%</option>)}</select></label>
    </div>}
    {rendering && <p role="status">Exibindo página…</p>}
    <div ref={container} className={styles.viewport} tabIndex={0} role="region" aria-label="Conteúdo do PDF">
      <div className={styles.sheet}><canvas ref={canvas} aria-label={`Página ${page} de ${name}`} /><div ref={text} className="textLayer" /></div>
    </div>
  </section>;
}
