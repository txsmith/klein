import { kotlin } from "./Kotlin.js";

export class SourceSpan {
  static readonly zero = new SourceSpan(0, 0);

  constructor(
    readonly start: number,
    readonly end: number,
  ) {}

  static pos(pos: number): SourceSpan {
    return new SourceSpan(pos, pos);
  }

  plus(other: SourceSpan): SourceSpan {
    return new SourceSpan(this.start, other.end);
  }

  formatInSource(source: string, contextLines: number = 2, message: string | null = null): string {
    return kotlin.formatInSource(this.start, this.end, source, contextLines, message);
  }
}
