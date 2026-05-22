# Prompt 03 — Knowledge Base

**Common references:** `../common/02_knowledge_content.md` (verbatim file content + loading rules)

---

## Context

Project: `com.example.unlimitagent`, Spring Boot 4 / Java 17.

The agent uses two static text files loaded at startup.
Their content is specified in `../common/02_knowledge_content.md`.

---

## Task

### 1 & 2. Knowledge text files

Generate the two knowledge files using the exact content from `../common/02_knowledge_content.md`:

- `src/main/resources/knowledge/system_description.txt`
- `src/main/resources/knowledge/past_incidents.txt`

Write them as plain text exactly as specified — no reformatting.

### 3. `src/main/java/com/example/unlimitagent/knowledge/KnowledgeBase.java`

A Spring `@Component` that:

- Receives a `ResourceLoader` via constructor injection.
- At construction time, reads both `.txt` files from the classpath:
  - `classpath:knowledge/system_description.txt`
  - `classpath:knowledge/past_incidents.txt`
- Stores the contents in `private final String` fields.
- Exposes two getters: `getSystemDescription()` and `getPastIncidents()`.
- Throws `IllegalStateException` with a descriptive message if either file is
  missing, unreadable, or blank.

The files are read once at startup and cached for the lifetime of the application.

---

## Output

Return all three artifacts (two `.txt` files and one `.java` file) labelled with their paths.
No explanation.
