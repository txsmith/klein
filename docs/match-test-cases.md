# Pattern Matching Test Cases

This document outlines all test cases for pattern matching implementation, organized by category.

## Lexer Tests

**File:** `klein-lib/src/commonTest/kotlin/klein/lexer/MatchTest.kt`

### Keyword Recognition
1. `matchKeywordAlone` - `match` recognized as keyword
2. `matchNotPrefixOfIdentifier` - `matcher` is identifier, not keyword
3. `matchNotSuffixOfIdentifier` - `rematch` is identifier, not keyword
4. `matchWithNewline` - `match\n` properly tokenized
5. `matchFollowedByIdentifier` - `match x` tokenizes correctly

## Parser Tests - Basic Structure

**File:** `klein-lib/src/commonTest/kotlin/klein/parser/MatchTest.kt`

### Value Matching (with subject)

#### Simple Constructor Patterns
6. `simpleConstructorMatch` - Basic enum matching
   ```klein
   match status
     Approved -> 1
     Rejected -> 0
   ```

7. `multipleConstructorArms` - Multiple constructor patterns
   ```klein
   match color
     Red -> 1
     Green -> 2
     Blue -> 3
   ```

8. `constructorMatchWithElse` - Constructor patterns + else
   ```klein
   match status
     Approved -> 1
     Rejected -> 0
     else -> -1
   ```

#### Variable Patterns
9. `variablePatternSimple` - Bind to variable
   ```klein
   match x
     y -> y + 1
   ```

10. `variablePatternMultipleArms` - Multiple bindings
    ```klein
    match value
      x -> x * 2
      else -> 0
    ```

#### Wildcard Patterns
11. `wildcardPattern` - Underscore pattern
    ```klein
    match x
      _ -> 0
    ```

12. `wildcardWithOtherPatterns` - Mix wildcard and others
    ```klein
    match status
      Approved -> 1
      _ -> 0
    ```

#### Literal Patterns
13. `intLiteralPattern` - Integer pattern
    ```klein
    match x
      1 -> 'one'
      2 -> 'two'
      else -> 'other'
    ```

14. `boolLiteralPattern` - Boolean pattern
    ```klein
    match flag
      true -> 'yes'
      false -> 'no'
    ```

15. `stringLiteralPattern` - String pattern
    ```klein
    match name
      'Alice' -> 1
      'Bob' -> 2
      else -> 0
    ```

16. `mixedLiteralPatterns` - Different literal types
    ```klein
    match value
      1 -> 'int'
      true -> 'bool'
      'hello' -> 'string'
    ```

#### Guards
17. `simpleGuard` - Pattern with guard
    ```klein
    match amount
      x if x > 1000 -> 'large'
      x if x > 100 -> 'medium'
      x -> 'small'
    ```

18. `guardWithComplexCondition` - Guard with boolean operators
    ```klein
    match amount
      x if x > 1000 and x < 10000 -> 'medium'
      x if x >= 10000 -> 'large'
      x -> 'small'
    ```

19. `constructorWithGuard` - Constructor pattern + guard
    ```klein
    match status
      Approved if amount > 1000 -> 'large approval'
      Approved -> 'small approval'
      Rejected -> 'rejected'
    ```

### Condition Matching (no subject)

#### Simple Conditions
20. `simpleConditionMatch` - Basic condition matching
    ```klein
    match
      score >= 90 -> 'A'
      score >= 80 -> 'B'
      else -> 'F'
    ```

21. `conditionWithBooleanOps` - Conditions with and/or
    ```klein
    match
      x > 0 and y > 0 -> 'positive'
      x < 0 or y < 0 -> 'negative'
      else -> 'zero'
    ```

22. `conditionWithFunctionCalls` - Function calls in conditions
    ```klein
    match
      isValid(x) -> process(x)
      isWarning(x) -> warn(x)
      else -> reject(x)
    ```

23. `conditionWithFieldAccess` - Field access in conditions
    ```klein
    match
      customer.creditScore < 500 -> 'High'
      customer.yearsWithUs < 1 -> 'Medium'
      else -> 'Low'
    ```

### Else Arms

24. `elseArmOnly` - Just else (degenerate case)
    ```klein
    match x
      else -> 0
    ```

25. `elseInConditionMatch` - Else in condition match
    ```klein
    match
      x > 10 -> 'big'
      else -> 'small'
    ```

26. `matchWithoutElse` - No else clause (valid)
    ```klein
    match x
      1 -> 'one'
      2 -> 'two'
    ```

## Indentation Tests - Value Match

#### Multi-line Bodies
27. `valueMatchWithBlockBody` - Block in match arm
    ```klein
    match status
      Approved ->
        log('approved')
        process()
        1
      Rejected -> 0
    ```

28. `valueMatchMultipleBlockBodies` - Multiple arms with blocks
    ```klein
    match status
      Approved ->
        log('approved')
        process()
      Rejected ->
        log('rejected')
        notify()
      Pending ->
        wait()
    ```

29. `valueMatchMixedInlineAndBlock` - Some inline, some block
    ```klein
    match status
      Approved -> 1
      Rejected ->
        reason = 'failed'
        log(reason)
        0
      Pending -> -1
    ```

#### Dedent Detection
30. `valueMatchDedentBetweenArms` - Proper dedent between block arms
    ```klein
    match x
      1 ->
        a = x * 2
        b = a + 1
        b
      2 ->
        c = x * 3
        c
    ```

31. `valueMatchDedentToElse` - Dedent from block to else
    ```klein
    match x
      1 ->
        a = x * 2
        log(a)
        a
      else ->
        0
    ```

#### Nested Match (Value)
32. `nestedValueMatch` - Match inside value match body
    ```klein
    match outer
      A ->
        match inner
          X -> 1
          Y -> 2
      B -> 3
    ```

33. `deeplyNestedValueMatch` - Three levels
    ```klein
    match x
      1 ->
        match y
          2 ->
            match z
              3 -> 'found'
              else -> 'not found'
          else -> 'no'
      else -> 'default'
    ```

## Indentation Tests - Condition Match

#### Multi-line Conditions
34. `conditionMatchWithContinuation` - Multi-line condition
    ```klein
    match
      customer.creditScore < 500
        and customer.yearsWithUs < 1 -> 'High'
      score >= 80 -> 'Medium'
      else -> 'Low'
    ```

35. `conditionMatchComplexContinuation` - Longer continuation
    ```klein
    match
      x > 0
        and y > 0
        and z > 0 -> 'all positive'
      x < 0
        or y < 0
        or z < 0 -> 'some negative'
      else -> 'zero'
    ```

#### Multi-line Bodies
36. `conditionMatchWithBlockBody` - Block after condition
    ```klein
    match
      score >= 90 ->
        log('excellent')
        grade = 'A'
        grade
      score >= 80 -> 'B'
      else -> 'F'
    ```

37. `conditionMatchAllBlockBodies` - All arms with blocks
    ```klein
    match
      x > 100 ->
        a = x * 2
        log(a)
        a
      x > 50 ->
        b = x + 1
        b
      else ->
        0
    ```

#### Both Multi-line
38. `conditionMatchMultilineConditionAndBody` - Both multi-line
    ```klein
    match
      customer.creditScore < 500
        and customer.yearsWithUs < 1 ->
          log('high risk')
          notify()
          'High'
      score >= 80 -> 'Low'
    ```

#### Nested Match (Condition)
39. `nestedConditionMatch` - Match inside condition match
    ```klein
    match
      approved ->
        match amount
          x if x > 1000 -> 'large'
          x -> 'small'
      rejected -> 'no'
    ```

40. `conditionMatchInNestedIf` - Condition match inside if
    ```klein
    result =
      if enabled then
        match
          status > 10 -> 'high'
          status > 5 -> 'medium'
          else -> 'low'
      else
        -1
    ```

## Complex Nesting Tests

41. `valueMatchInLambda` - Value match inside lambda
    ```klein
    |x -> match x
      1 -> 'one'
      2 -> 'two'
      else -> 'other'
    |
    ```

42. `conditionMatchInLambda` - Condition match inside lambda
    ```klein
    |x -> match
      x > 10 -> 'big'
      x > 5 -> 'medium'
      else -> 'small'
    |
    ```

43. `matchAsArgument` - Match expression as function argument
    ```klein
    foo(match x
      1 -> 'one'
      else -> 'other'
    )
    ```

44. `matchInRecordField` - Match in record literal
    ```klein
    record = {
      value = match x
        1 -> 10
        2 -> 20
        else -> 0
    }
    ```

45. `matchInBinaryOp` - Match in binary operation
    ```klein
    result = (match x
      1 -> 10
      else -> 0
    ) + 5
    ```

46. `lambdaInMatchBody` - Lambda in match arm body
    ```klein
    match x
      1 -> |y -> y + 1|
      2 -> |y -> y * 2|
      else -> |y -> y|
    ```

## Error Cases - Structure

47. `missingArrow` - No arrow after pattern
    ```klein
    match x
      1 2
    ```
    Expected: `Expected '->'`

48. `missingBodyAfterArrow` - Arrow but no body
    ```klein
    match x
      1 ->
    ```
    Expected: `Expected expression`

49. `emptyMatch` - No arms at all
    ```klein
    match x
    ```
    Expected: `Expected match arm`

50. `matchWithOnlyNewline` - Just newline, no arms
    ```klein
    match x

    ```
    Expected: `Expected match arm`

51. `elseNotLast` - else in middle
    ```klein
    match x
      1 -> 'one'
      else -> 'other'
      2 -> 'two'
    ```
    Expected: `else must be last match arm`

52. `multipleElse` - Two else clauses
    ```klein
    match x
      1 -> 'one'
      else -> 'a'
      else -> 'b'
    ```
    Expected: `Duplicate else clause`

53. `arrowWithoutPattern` - Arrow without left side
    ```klein
    match x
      -> 1
    ```
    Expected: `Expected pattern or condition`

## Error Cases - Pattern/Condition Mismatch

54. `patternArmWithoutSubject` - Pattern when no subject
    ```klein
    match
      Approved -> 1
    ```
    Expected: `Pattern arm requires subject` or parse as identifier expression?
    (This is ambiguous - needs decision)

55. `guardWithoutSubject` - Guard in condition match
    ```klein
    match
      x if x > 10 -> 1
    ```
    Expected: `Guard not allowed in condition match`

56. `guardOnElse` - Guard on else arm
    ```klein
    match x
      1 -> 'one'
      else if true -> 'other'
    ```
    Expected: `else cannot have guard`

## Error Cases - Indentation

57. `inconsistentArmIndentation` - Arms at different levels
    ```klein
    match x
      1 -> 'one'
        2 -> 'two'
    ```
    Expected: `Inconsistent indentation`

58. `bodyNotIndented` - Multi-line body not indented
    ```klein
    match x
      1 ->
      a = 1
      a
    ```
    Expected: `Expected indented block`

59. `armIndentedTooMuch` - Arm indented like it's in previous body
    ```klein
    match x
      1 ->
        a = 1
        a
        2 -> 'two'
    ```
    Expected: `Unexpected indentation` or parse as part of block?

60. `arrowOnWrongLine` - Arrow on line by itself at wrong level
    ```klein
    match x
      1
      -> 'one'
    ```
    Expected: `Expected '->' after pattern`

61. `dedentMissingBetweenArms` - Second arm at same level as body
    ```klein
    match
      x > 10 ->
        a = 1
        b = 2
        y > 5 -> 3
    ```
    Expected: Should be parsed as part of block (identifier `y`)

62. `matchArmIndentedBelowMatchKeyword` - Arm less indented than match
    ```klein
      match x
    1 -> 'one'
    ```
    Expected: `Expected indented match arms`

## Error Cases - Invalid Patterns

63. `invalidPatternSyntax` - Nested braces or other invalid syntax
    ```klein
    match x
      { { } } -> 1
    ```
    Expected: Parse error (depends on future record pattern syntax)

64. `patternWithOperator` - Operator in pattern
    ```klein
    match x
      1 + 2 -> 'three'
    ```
    Expected: `Invalid pattern` or parse as expression?
    (Ambiguous - needs decision)

65. `functionCallAsPattern` - Function call in pattern position
    ```klein
    match x
      foo() -> 1
    ```
    Expected: `Invalid pattern` or parse as expression?
    (Ambiguous - needs decision)

## Edge Cases

66. `singleArmNoElse` - Just one arm, no else
    ```klein
    match x
      1 -> 'one'
    ```

67. `guardAlwaysFalse` - Guard that's obviously false
    ```klein
    match x
      y if false -> 1
      else -> 0
    ```
    (Valid syntax, semantic warning later)

68. `variablePatternShadowing` - Pattern shadows outer variable
    ```klein
    x = 10
    match y
      x -> x + 1
    ```
    (Valid, pattern `x` shadows outer `x`)

69. `wildcardInElsePosition` - Wildcard as last arm instead of else
    ```klein
    match x
      1 -> 'one'
      _ -> 'other'
    ```
    (Valid, equivalent to else)

70. `emptyBlockInMatchArm` - Empty block body (probably invalid)
    ```klein
    match x
      1 ->
      else -> 0
    ```
    Expected: `Expected expression` or `Empty block not allowed`

71. `matchSingleLineMultipleArms` - All on one line (probably invalid)
    ```klein
    match x 1 -> 'one' 2 -> 'two'
    ```
    Expected: `Expected newline after match subject`

72. `veryDeeplyNested` - Stress test deep nesting (5+ levels)
    ```klein
    match a
      1 ->
        match b
          2 ->
            match c
              3 ->
                match d
                  4 -> 'deep'
    ```

## Summary

- **Total test cases: 72**
- Lexer tests: 5
- Basic structure: 21
- Indentation (value match): 7
- Indentation (condition match): 8
- Complex nesting: 6
- Error cases - structure: 7
- Error cases - mismatch: 3
- Error cases - indentation: 6
- Error cases - patterns: 3
- Edge cases: 6

## Open Questions from Test Cases

1. **Pattern vs Expression Ambiguity (#54, 64, 65):** When there's no subject, how do we parse:
   - `Approved -> 1` (constructor pattern or identifier expression?)
   - `1 + 2 -> 3` (invalid pattern or valid expression?)
   - `foo() -> 1` (invalid pattern or valid expression?)

2. **Arrow Placement (#60):** Should arrow be allowed on next line?
   ```klein
   match x
     1
     -> 'one'
   ```

3. **Negative Literals:** Should `-1 -> 'negative'` be allowed as pattern?

4. **Case Sensitivity:** Should variable patterns allow uppercase? `match x ABC -> ...`

These questions need answers before implementing the tests.
