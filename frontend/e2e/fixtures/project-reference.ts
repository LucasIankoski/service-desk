import type { Project, RichText, TextRun } from "../../src/api/projects";
const text = (text: string): TextRun => ({ text, bold: false });
const bold = (text: string): TextRun => ({ text, bold: true });
const paragraph = (...runs: TextRun[]): RichText => ({ blocks: [{ type: "PARAGRAPH", runs }] });
export const projectReference: Project = {
  id: "project", classId: "class", className: "BII", archived: false,
  title: "Amor e Graça no País das Maravilhas", startDate: "2026-10-01", endDate: "2026-10-09", ageRange: "Crianças de 0 à 6 anos",
  status: "FINALIZED", teachers: [{ id: "a", displayName: "Cristine" }], version: 0,
  createdAt: "2026-09-28T12:00:00Z", updatedAt: "2026-09-28T12:00:00Z",
  generalObjective: paragraph(text("Proporcionar às crianças momentos de diversão, imaginação, criatividade e interação por meio de experiências lúdicas inspiradas no universo do País das Maravilhas, valorizando o brincar e tornando a Semana da Criança ainda mais especial.")),
  specificObjectives: ["Estimular a imaginação e a criatividade;", "Promover a socialização e a interação entre as crianças;", "Desenvolver habilidades motoras por meio de brincadeiras e desafios;", "Incentivar a expressão artística e o faz de conta;", "Explorar diferentes sabores, materiais, espaços e experiências;", "Proporcionar momentos de alegria, diversão e construção de memórias afetivas."],
  activities: [
    { date: "2026-10-01", title: "A rainha mandou", description: paragraph(text("Abertura do Mês das Crianças com o tema "), bold("“Amor e Graça no País das Maravilhas”."), text(" Os professores entrarão no clima da proposta, caracterizados como personagens divertidos, proporcionando uma recepção diferente e cheia de surpresas.")) },
    { date: "2026-10-02", title: "Siga o coelho branco", description: paragraph(text("As crianças participarão de uma caça ao tesouro com pistas, desafios e descobertas. Cada turma deverá confeccionar seu próprio baú dos tesouros e organizar as pistas da aventura.")) },
    { date: "2026-10-05", title: "O chapeleiro maluco", description: paragraph(text("Dia do chapéu maluco. As crianças poderão vir para a escola usando chapéus criativos, coloridos, personalizados e divertidos, valorizando a imaginação e a expressão individual.")) },
    { date: "2026-10-06", title: "Coma-me! Beba-me!", description: paragraph(text("Realização de uma experiência culinária com receitas divertidas e participação das crianças no preparo.")) },
    { date: "2026-10-07", title: "As cartas viraram a escola de cabeça para baixo", description: paragraph(text("Neste dia, a mochila não vale. As crianças deverão trazer seus materiais em recipientes diferentes e criativos, como cestas, caixas, malas, carrinhos, bolsas ou outras opções escolhidas pelas famílias.")) },
    { date: "2026-10-08", title: "O gato sumiu", description: paragraph(text("Será realizado o "), bold("Caminho das Maravilhas"), text(", com circuito de obstáculos, equilíbrio, saltos e desafios. Ao completar o percurso, as crianças encontrarão o Gato escondido.")) },
    { date: "2026-10-09", title: "A grande hora do chá", description: { blocks: [
      { type: "PARAGRAPH", runs: [text("Cada turma ficará responsável por uma estação:")] },
      { type: "PARAGRAPH", runs: [bold("Berçários:"), text(" O Sorriso do Gato\nEspaço para fotos, caretas, poses e acessórios divertidos.")] },
      { type: "PARAGRAPH", runs: [bold("Maternais:"), text(" Pinturas Malucas\nEspaço de pintura e exploração de diferentes materiais.")] },
      { type: "PARAGRAPH", runs: [bold("Jardins:"), text(" Brincadeiras\n- Corrida de saco\n- Ovo na colher\n- Corrida em dupla com os pés amarrados.")] },
      { type: "PARAGRAPH", runs: [text("Para encerrar a Semana da Criança, será realizado um "), bold("Baile à Fantasia"), text(", com música, brincadeiras e comidas especiais.")] }
    ] } }
  ],
  conclusion: paragraph(text("O projeto "), bold("“Amor e Graça no País das Maravilhas”"), text(" busca proporcionar às crianças experiências alegres, criativas e significativas, valorizando o brincar, a imaginação e a convivência. Ao longo da semana, cada proposta contribuirá para a criação de momentos especiais e memórias afetivas dentro do ambiente escolar."))
};
