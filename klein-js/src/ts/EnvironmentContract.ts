import { fromKotlin as fromKotlinChecked, type Checked } from "./Checked.js";
import { DecodedEdition } from "./DecodedEdition.js";
import { Edition, Pin } from "./Edition.js";
import { Environment, HandlerRegistration } from "./Environment.js";
import { freeze, kotlin, mapExceptions } from "./Kotlin.js";
import type { ReleaseNumber, RevisionNumber } from "./Numbering.js";
import { Type, type ContractType, type RuleType } from "./Type.js";
import { fromKotlin as fromKotlinValue, type Value as KleinValue } from "./Value.js";

export type ContractDeclaration = ContractDeclaration.Function | ContractDeclaration.Value;

export namespace ContractDeclaration {
  export class Function {
    readonly #kotlin: kotlin.ContractDeclaration;

    private constructor(declaration: kotlin.ContractDeclaration) {
      this.#kotlin = declaration;
    }

    /** @internal */
    static fromKotlin(declaration: kotlin.ContractDeclaration): Function {
      return new Function(declaration);
    }

    get name(): string {
      return this.#kotlin.name;
    }

    get revision(): RevisionNumber {
      return this.#kotlin.revision as RevisionNumber;
    }

    #type?: ContractType;
    #answerType?: RuleType;
    #parameterTypes?: readonly RuleType[];

    get type(): ContractType {
      return (this.#type ??= Type.fromKotlin(this.#kotlin.type));
    }

    get answerType(): RuleType {
      return (this.#answerType ??= Type.fromKotlin(this.#kotlin.answerType));
    }

    get parameterTypes(): readonly RuleType[] {
      return (this.#parameterTypes ??= freeze((this.#kotlin.parameterTypes ?? []).map((it) => Type.fromKotlin<null>(it))));
    }
  }

  export class Value {
    readonly #kotlin: kotlin.ContractDeclaration;

    private constructor(declaration: kotlin.ContractDeclaration) {
      this.#kotlin = declaration;
    }

    /** @internal */
    static fromKotlin(declaration: kotlin.ContractDeclaration): Value {
      return new Value(declaration);
    }

    get name(): string {
      return this.#kotlin.name;
    }

    get revision(): RevisionNumber {
      return this.#kotlin.revision as RevisionNumber;
    }

    #type?: ContractType;
    #answerType?: RuleType;

    get type(): ContractType {
      return (this.#type ??= Type.fromKotlin(this.#kotlin.type));
    }

    get answerType(): RuleType {
      return (this.#answerType ??= Type.fromKotlin(this.#kotlin.answerType));
    }
  }
}

export class EnvironmentContract {
  readonly #kotlin: kotlin.EnvironmentContract;

  private constructor(contract: kotlin.EnvironmentContract) {
    this.#kotlin = contract;
  }

  /** @internal */
  static fromKotlin(contract: kotlin.EnvironmentContract): EnvironmentContract {
    return new EnvironmentContract(contract);
  }

  get environment(): string {
    return this.#kotlin.environment;
  }

  #declarations?: readonly ContractDeclaration[];
  #releases?: readonly ReleaseNumber[];

  get declarations(): readonly ContractDeclaration[] {
    return (this.#declarations ??= freeze(
      this.#kotlin.declarations.map((it) =>
        it.isFunction ? ContractDeclaration.Function.fromKotlin(it) : ContractDeclaration.Value.fromKotlin(it),
      ),
    ));
  }

  get releases(): readonly ReleaseNumber[] {
    return (this.#releases ??= freeze(this.#kotlin.releases.map((it) => it as ReleaseNumber)));
  }

  check(ruleSource: string, release: ReleaseNumber): Checked<RuleType> {
    return mapExceptions(() => fromKotlinChecked(this.#kotlin.check(ruleSource, release), (it) => Type.fromKotlin<null>(it)));
  }

  compileRule(ruleSource: string, release: ReleaseNumber): Checked<Edition>;
  compileRule(source: string, pins: ReadonlyMap<string, Pin> | ReadonlyMap<string, RevisionNumber>): Checked<Edition>;
  compileRule(
    source: string,
    releaseOrPins: ReleaseNumber | ReadonlyMap<string, Pin> | ReadonlyMap<string, RevisionNumber>,
  ): Checked<Edition> {
    return mapExceptions(() => {
      if (typeof releaseOrPins === "number") {
        return fromKotlinChecked(this.#kotlin.compileRule(source, releaseOrPins), Edition.fromKotlin);
      }
      const names = [...releaseOrPins.keys()];
      const revisions = [...releaseOrPins.values()].map((it) => (it instanceof Pin ? it.revision : it));
      return fromKotlinChecked(this.#kotlin.compileRuleAtRevisions(source, names, revisions), Edition.fromKotlin);
    });
  }

  evaluateValue(source: string, release: ReleaseNumber, expected: RuleType): Checked<KleinValue> {
    return mapExceptions(() => fromKotlinChecked(this.#kotlin.evaluateValue(source, release, Type.toKotlin(expected)), fromKotlinValue));
  }

  decodeEditionJson(text: string): DecodedEdition {
    return mapExceptions(() => DecodedEdition.fromKotlin(this.#kotlin.decodeEditionJson(text)));
  }

  implement(...registrations: HandlerRegistration[]): Environment {
    return mapExceptions(() => Environment.fromKotlin(this.#kotlin.implement(registrations.map(HandlerRegistration.toKotlin))));
  }
}
