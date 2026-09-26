/**
 * Tiny assertion helpers.
 *
 * Deliberately local rather than jsr:@std/assert so the whole server, including
 * its test suite, has zero external dependencies and `deno test` runs offline.
 */

export class AssertionError extends Error {
  override name = "AssertionError";
}

function show(value: unknown): string {
  if (typeof value === "string") return JSON.stringify(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function assert(condition: unknown, message = "Expected a truthy value"): void {
  if (!condition) throw new AssertionError(message);
}

function deepEquals(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true;
  if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    return a.every((item, index) => deepEquals(item, b[index]));
  }
  const aKeys = Object.keys(a as Record<string, unknown>).sort();
  const bKeys = Object.keys(b as Record<string, unknown>).sort();
  if (aKeys.length !== bKeys.length) return false;
  if (!aKeys.every((key, index) => key === bKeys[index])) return false;
  return aKeys.every((key) =>
    deepEquals(
      (a as Record<string, unknown>)[key],
      (b as Record<string, unknown>)[key],
    )
  );
}

export function assertEquals<T>(actual: T, expected: T, message?: string): void {
  if (!deepEquals(actual, expected)) {
    throw new AssertionError(
      message ??
        `Values are not equal.\n  actual:   ${show(actual)}\n  expected: ${show(expected)}`,
    );
  }
}

export function assertNotEquals<T>(actual: T, expected: T, message?: string): void {
  if (Object.is(actual, expected)) {
    throw new AssertionError(message ?? `Values should differ, both were ${show(actual)}`);
  }
}

export function assertMatch(actual: string, pattern: RegExp, message?: string): void {
  if (!pattern.test(actual)) {
    throw new AssertionError(
      message ?? `${show(actual)} does not match ${String(pattern)}`,
    );
  }
}

export function assertIncludes(haystack: string, needle: string, message?: string): void {
  if (!haystack.includes(needle)) {
    throw new AssertionError(
      message ?? `Expected text to include ${show(needle)}.\n  actual: ${show(haystack)}`,
    );
  }
}

export async function assertRejects(
  fn: () => unknown | Promise<unknown>,
  message?: string,
): Promise<unknown> {
  try {
    await fn();
  } catch (error) {
    return error;
  }
  throw new AssertionError(message ?? "Expected the call to reject, but it resolved.");
}

export function assertThrows(fn: () => unknown, message?: string): unknown {
  try {
    fn();
  } catch (error) {
    return error;
  }
  throw new AssertionError(message ?? "Expected the call to throw, but it returned.");
}
