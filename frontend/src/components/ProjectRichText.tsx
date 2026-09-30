import { Fragment } from "react";
import type { RichText, TextBlock, TextRun } from "../api/projects";

export function mergeRuns(runs: TextRun[]): TextRun[] {
  const result: TextRun[] = [];
  for (const run of runs) {
    if (!run.text) continue;
    const last = result.at(-1);
    if (last?.bold === run.bold) last.text += run.text;
    else result.push({ ...run });
  }
  return result;
}
function sliceRuns(runs: TextRun[], start: number, end: number): TextRun[] {
  let offset = 0;
  return runs.flatMap(run => {
    const from = Math.max(0, start - offset), to = Math.min(run.text.length, end - offset);
    offset += run.text.length;
    return to > from ? [{ ...run, text: run.text.slice(from, to) }] : [];
  });
}
export function editRuns(runs: TextRun[], text: string): TextRun[] {
  const previous = runs.map(r => r.text).join("");
  let start = 0, tail = 0;
  while (start < previous.length && start < text.length && previous[start] === text[start]) start++;
  while (tail < previous.length - start && tail < text.length - start && previous[previous.length - tail - 1] === text[text.length - tail - 1]) tail++;
  const insertion = text.slice(start, text.length - tail);
  const bold = start > 0 ? sliceRuns(runs, start - 1, start).at(0)?.bold ?? false : false;
  return mergeRuns([...sliceRuns(runs, 0, start), { text: insertion, bold }, ...sliceRuns(runs, previous.length - tail, previous.length)]);
}
function Runs({ runs }: { runs: TextRun[] }) {
  return runs.map((r, i) => r.bold ? <strong key={i}>{r.text}</strong> : <Fragment key={i}>{r.text}</Fragment>);
}
export function RichTextView({ value }: { value: RichText }) {
  const groups: TextBlock[][] = [];
  for (const block of value.blocks) {
    const last = groups.at(-1);
    if (block.type === "BULLET" && last?.[0].type === "BULLET") last.push(block);
    else groups.push([block]);
  }
  return <>{groups.map((group, i) => group[0].type === "BULLET"
    ? <ul key={i}>{group.map((b, j) => <li key={j}><Runs runs={b.runs} /></li>)}</ul>
    : group[0].type === "HEADING" ? <h3 key={i}><Runs runs={group[0].runs} /></h3>
    : <p key={i}><Runs runs={group[0].runs} /></p>)}</>;
}
export function richTextToPlain(value: RichText): string {
  return value.blocks.map(b => (b.type === "BULLET" ? "- " : "") + b.runs.map(r => r.text).join("")).join("\n\n");
}
export function updatePlainRichText(value: RichText, text: string): RichText {
  if (richTextToPlain(value) === text) return value;
  const previousRuns = value.blocks.flatMap((b, i) => [
    { text: (i ? "\n\n" : "") + (b.type === "BULLET" ? "- " : ""), bold: false }, ...b.runs
  ]);
  const edited = editRuns(previousRuns, text);
  let offset = 0;
  const blocks: TextBlock[] = text.split("\n\n").map(paragraph => {
    const bullet = paragraph.startsWith("- ") && !paragraph.includes("\n");
    const runs = sliceRuns(edited, offset + (bullet ? 2 : 0), offset + paragraph.length);
    offset += paragraph.length + 2;
    const historicalHeading = value.blocks.some(b => b.type === "HEADING" && b.runs.map(r => r.text).join("") === paragraph);
    return { type: historicalHeading ? "HEADING" : bullet ? "BULLET" : "PARAGRAPH", runs };
  });
  return { blocks };
}
export function ProjectRichText({ label, value, onChange, rows = 6 }: {
  label: string; value: RichText; onChange: (value: RichText) => void; rows?: number;
}) {
  return <label>{label}<textarea aria-label={label} rows={rows} maxLength={40000}
    value={richTextToPlain(value)} onChange={e => onChange(updatePlainRichText(value, e.target.value))} /></label>;
}
