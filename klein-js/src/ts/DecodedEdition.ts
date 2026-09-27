import { Edition, Pin } from "./Edition.js";
import { freezeMap, type kotlin } from "./Kotlin.js";
import type { LanguageVersion, RevisionNumber } from "./Numbering.js";

export type StaleReason =
  | StaleReason.ChecksumMismatch
  | StaleReason.LanguageChanged
  | StaleReason.CompilerChanged
  | StaleReason.UnknownPins
  | StaleReason.DeclarationChanged;

export namespace StaleReason {
  export class ChecksumMismatch {
    static readonly instance = new ChecksumMismatch();
    #brand = true;
    private constructor() {}
  }

  export class LanguageChanged {
    static readonly instance = new LanguageChanged();
    #brand = true;
    private constructor() {}
  }

  export class CompilerChanged {
    static readonly instance = new CompilerChanged();
    #brand = true;
    private constructor() {}
  }

  export class UnknownPins {
    readonly #kotlin: readonly kotlin.Pin[];

    private constructor(pins: readonly kotlin.Pin[]) {
      this.#kotlin = pins;
    }

    /** @internal */
    static fromKotlin(pins: readonly kotlin.Pin[]): UnknownPins {
      return new UnknownPins(pins);
    }

    #pins?: ReadonlyMap<string, RevisionNumber>;

    get pins(): ReadonlyMap<string, RevisionNumber> {
      return (this.#pins ??= freezeMap(this.#kotlin.map((it) => [it.name, it.revision as RevisionNumber])));
    }
  }

  export class DeclarationChanged {
    static readonly instance = new DeclarationChanged();
    #brand = true;
    private constructor() {}
  }
}

export type DecodedEdition = DecodedEdition.Intact | DecodedEdition.Stale;

export namespace DecodedEdition {
  export class Intact {
    readonly #kotlin: kotlin.Edition;

    private constructor(edition: kotlin.Edition) {
      this.#kotlin = edition;
    }

    /** @internal */
    static fromKotlin(edition: kotlin.Edition): Intact {
      return new Intact(edition);
    }

    #edition?: Edition;

    get edition(): Edition {
      return (this.#edition ??= Edition.fromKotlin(this.#kotlin));
    }
  }

  export class Stale {
    readonly #kotlin: kotlin.DecodedEdition;

    private constructor(decoded: kotlin.DecodedEdition) {
      this.#kotlin = decoded;
    }

    /** @internal */
    static fromKotlin(decoded: kotlin.DecodedEdition): Stale {
      return new Stale(decoded);
    }

    get language(): LanguageVersion {
      return this.#kotlin.language as LanguageVersion;
    }

    #pins?: ReadonlyMap<string, Pin>;
    #reason?: StaleReason;

    get pins(): ReadonlyMap<string, Pin> {
      return (this.#pins ??= Pin.fromKotlinAll(this.#kotlin.pins));
    }

    get source(): string {
      return this.#kotlin.source;
    }

    get reason(): StaleReason {
      return (this.#reason ??= reasonFromKotlin(this.#kotlin));
    }
  }

  /** @internal */
  export function fromKotlin(decoded: kotlin.DecodedEdition): DecodedEdition {
    return decoded.edition == null ? Stale.fromKotlin(decoded) : Intact.fromKotlin(decoded.edition);
  }
}

function reasonFromKotlin(decoded: kotlin.DecodedEdition): StaleReason {
  switch (decoded.reason) {
    case "ChecksumMismatch":
      return StaleReason.ChecksumMismatch.instance;
    case "LanguageChanged":
      return StaleReason.LanguageChanged.instance;
    case "CompilerChanged":
      return StaleReason.CompilerChanged.instance;
    case "UnknownPins":
      return StaleReason.UnknownPins.fromKotlin(decoded.unknownPins ?? []);
    case "DeclarationChanged":
      return StaleReason.DeclarationChanged.instance;
    default:
      throw new Error(`unknown stale reason ${decoded.reason}`);
  }
}
