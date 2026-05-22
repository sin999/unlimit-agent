# Prompt 02 — Domain Models

**Common references:** `../common/01_domain_concepts.md` (entity definitions, field constraints, JSON format)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17. Lombok is on the classpath.

Implement the five domain types from `../common/01_domain_concepts.md` as Java classes.
All files go under `src/main/java/com/example/unlimitagent/model/`.

---

## Task

### 1. `IncidentRequest.java`

A request DTO. Fields:
- `String description` — `@NotBlank`

Use a Java record or a Lombok `@Data` class — keep it minimal.

### 2. `Severity.java`

An enum with values `LOW`, `MEDIUM`, `HIGH`.

Add a `@JsonCreator` static factory method accepting a `String` so Jackson can
deserialise `"high"`, `"HIGH"`, or `"High"` to the same constant without error.

### 3. `Hypothesis.java`

A record or Lombok `@Data` class:
- `String title`
- `String reasoning`
- `List<String> nextSteps` — annotated `@JsonProperty("next_steps")`

### 4. `IncidentAnalysis.java`

Top-level response record:
- `String category`
- `String summary`
- `Severity severity`
- `List<Hypothesis> hypotheses`

Annotate with `@JsonIgnoreProperties(ignoreUnknown = true)` so extra LLM-generated
fields do not break deserialisation.

### 5. `AnalysisStage.java`

Enum with constants `PARSE_INPUT`, `ENRICH_WITH_CONTEXT`, `GENERATE_RESPONSE`.

Each constant carries a human-readable `label` field (e.g. `"Parsing input"`).
Provide a constructor and a `getLabel()` getter.
Labels must match the values in `../common/01_domain_concepts.md`.

---

## Output

Return all five files with full package declarations and imports.
No explanation — just the code blocks labelled with their file paths.
