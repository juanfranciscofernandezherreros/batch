# Naming conditions in `if` statements

Every `if` condition must be expressed through a boolean variable or method with
a descriptive name that states its business meaning.

Prefer:

```java
boolean hasValidHeader = headers.containsAll(requiredHeaders);
if (hasValidHeader) {
    // ...
}
```

Avoid placing compound or non-obvious expressions directly in an `if`:

```java
if (headers.containsAll(requiredHeaders) && rows.size() > 1) {
    // ...
}
```

Use positive names where practical, such as `isValid`, `hasRows`,
`shouldImport`, or `canRetry`.
