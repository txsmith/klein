declare const revisionNumber: unique symbol;
declare const releaseNumber: unique symbol;
declare const languageVersion: unique symbol;

export type RevisionNumber = number & { readonly [revisionNumber]: true };
export type ReleaseNumber = number & { readonly [releaseNumber]: true };
export type LanguageVersion = number & { readonly [languageVersion]: true };

export function RevisionNumber(value: number): RevisionNumber {
  return whole(value, "a revision number") as RevisionNumber;
}

export function ReleaseNumber(value: number): ReleaseNumber {
  return whole(value, "a release number") as ReleaseNumber;
}

export function LanguageVersion(value: number): LanguageVersion {
  return whole(value, "a language version") as LanguageVersion;
}

function whole(value: number, what: string): number {
  if (!Number.isInteger(value)) throw new TypeError(`${what} must be a whole number, not ${value}`);
  return value;
}
