import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { editRuns, richTextToPlain, updatePlainRichText, RichTextView } from "./ProjectRichText";

describe("texto formatado dos projetos", () => {
  it("edita texto simples preservando parágrafos, listas e negritos existentes", () => {
    const value = { blocks: [
      { type: "PARAGRAPH" as const, runs: [{ text: "Vamos ", bold: false }, { text: "brincar", bold: true }] },
      { type: "BULLET" as const, runs: [{ text: "Explorar", bold: false }] }
    ] };
    expect(updatePlainRichText(value, richTextToPlain(value))).toBe(value);
    const edited = updatePlainRichText(value, "Vamos brincar juntos\n\n- Explorar\n\n");
    expect(edited.blocks[0].runs.find(r => r.bold)?.text).toContain("brincar");
    expect(edited.blocks[1].type).toBe("BULLET");
    expect(richTextToPlain(edited)).toBe("Vamos brincar juntos\n\n- Explorar\n\n");
    const replaced = updatePlainRichText({ blocks: [{ type: "HEADING", runs: [{ text: "TÍTULO ANTIGO", bold: true }] }] }, "Novo desenvolvimento em texto simples.");
    expect(replaced.blocks).toEqual([{ type: "PARAGRAPH", runs: [{ text: "Novo desenvolvimento em texto simples.", bold: false }] }]);
  });
  it("preserva negrito ao inserir e remover texto em outros trechos", () => {
    const runs = [{ text: "Vamos ", bold: false }, { text: "brincar", bold: true }, { text: " juntos", bold: false }];
    expect(editRuns(runs, "Hoje vamos brincar juntos").find(r => r.bold)?.text).toBe("brincar");
    expect(editRuns(runs, "Vamos brincar").at(-1)).toEqual({ text: "brincar", bold: true });
  });
  it("renderiza listas e trata marcação HTML como texto", () => {
    render(<RichTextView value={{ blocks: [
      { type: "PARAGRAPH", runs: [{ text: "<img src=x onerror=alert(1)>", bold: true }] },
      { type: "BULLET", runs: [{ text: "Explorar", bold: false }] },
      { type: "BULLET", runs: [{ text: "Criar", bold: false }] }
    ] }} />);
    expect(screen.getAllByRole("list")).toHaveLength(1);
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("<img src=x onerror=alert(1)>").tagName).toBe("STRONG");
  });
});
