import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusTrail } from "./StatusTrail";

describe("StatusTrail", () => {
  it("renders the service trail in Portuguese", () => {
    render(<StatusTrail status="IN_PROGRESS" />);

    expect(screen.getByLabelText("Trilha de atendimento")).toBeInTheDocument();
    expect(screen.getByText("Aberta")).toBeInTheDocument();
    expect(screen.getByText("Em atendimento")).toBeInTheDocument();
    expect(screen.getByText("Fechada")).toBeInTheDocument();
  });
});
