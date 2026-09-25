import { checkContract, InvalidContract, KleinError, type Contract, type Diagnostic } from "klein";

export type ContractCheck =
  | { ok: true; contract: Contract }
  | { ok: false; diagnostics: Diagnostic[]; messages: string[] };

export function checkContractSource(source: string): ContractCheck {
  try {
    return { ok: true, contract: checkContract(source) };
  } catch (error) {
    if (!(error instanceof KleinError)) throw error;
    const diagnostics = error.errors.flatMap((e) => (e instanceof InvalidContract ? e.diagnostics : []));
    const messages = error.errors.filter((e) => !(e instanceof InvalidContract)).map((e) => e.message);
    return { ok: false, diagnostics, messages };
  }
}

export function lineAndColumn(source: string, offset: number): { line: number; column: number } {
  const before = source.slice(0, offset);
  const line = before.split("\n").length;
  const column = offset - before.lastIndexOf("\n");
  return { line, column };
}
