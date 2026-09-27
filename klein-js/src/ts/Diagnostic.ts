import { freeze, kotlin } from "./Kotlin.js";
import { SourceSpan } from "./SourceSpan.js";

export interface Diagnostic {
  readonly message: string;
  readonly span: SourceSpan;
}

class ReportedDiagnostic implements Diagnostic {
  readonly #kotlin: kotlin.Diagnostic;

  constructor(diagnostic: kotlin.Diagnostic) {
    this.#kotlin = diagnostic;
  }

  get message(): string {
    return this.#kotlin.message;
  }

  #span?: SourceSpan;

  get span(): SourceSpan {
    return (this.#span ??= new SourceSpan(this.#kotlin.start, this.#kotlin.end));
  }

  static toKotlin(diagnostic: ReportedDiagnostic): kotlin.Diagnostic {
    return diagnostic.#kotlin;
  }
}

export function fromKotlin(diagnostic: kotlin.Diagnostic): Diagnostic {
  return new ReportedDiagnostic(diagnostic);
}

export function fromKotlinAll(diagnostics: readonly kotlin.Diagnostic[]): readonly Diagnostic[] {
  return freeze(diagnostics.map(fromKotlin));
}

export function toKotlin(diagnostic: Diagnostic): kotlin.Diagnostic {
  if (diagnostic instanceof ReportedDiagnostic) return ReportedDiagnostic.toKotlin(diagnostic);
  return new kotlin.Diagnostic(diagnostic.message, diagnostic.span.start, diagnostic.span.end);
}
