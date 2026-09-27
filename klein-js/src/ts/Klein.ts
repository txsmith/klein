import { fromKotlin, type Checked } from "./Checked.js";
import { EnvironmentContract } from "./EnvironmentContract.js";
import { freeze, kotlin, mapExceptions } from "./Kotlin.js";
import { Token } from "./Token.js";

export namespace Klein {
  export function tokenize(source: string): Checked<readonly Token[]> {
    return fromKotlin(kotlin.tokenize(source), (tokens) => freeze(tokens.map(Token.fromKotlin)));
  }

  export function checkContract(contractSource: string): EnvironmentContract {
    return mapExceptions(() => EnvironmentContract.fromKotlin(kotlin.checkContract(contractSource)));
  }
}
