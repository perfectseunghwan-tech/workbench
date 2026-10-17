# Workspace Rules

## 1. Workspace

The active development workspace is:

`C:\workbench\`

Codex may read, create, modify, rename, and delete files inside this workspace
when required to complete the user's requested task.

All file modifications must remain within `C:\workbench\`.

The external attachment directory is:

`C:\Users\itumo\.codex\attachments\`

Attachment rules:

- Codex may read files in this directory for requirements and reference analysis.
- Treat all attachment files as read-only.
- Do not create, modify, rename, move, or delete attachment files.
- Do not expand writable permissions merely to access attachments.
- If an attachment cannot be accessed, report the actual error.
- Never modify files outside the active workspace unless explicitly authorized
  and permitted by the Sandbox.

## 2. Project Structure

Primary project structure:

`C:\workbench\`

- `backend\` — backend application
- `frontend\` — frontend application
- `docs\specs\` — feature specifications and implementation plans
- `AGENTS.md` — default Codex development rules

Roles:

- `AGENTS.md` defines **how Codex works**.
- `docs\specs\*.md` defines **what must be implemented** for a specific task.

A specification is authoritative only when the user explicitly references it
or explicitly asks Codex to create or update it.

## 3. Instruction Priority

Apply instructions in this order:

1. User's explicit instruction for the current task.
2. Referenced specification under `docs\specs\`.
3. This `AGENTS.md`.
4. Existing project architecture, conventions, and implementation patterns.

A specification does not authorize unrelated changes.

Permission to modify the workspace does not authorize changes outside the
requested task.

If a specification differs from the actual source structure:

- Preserve the specification's functional intent.
- Follow the actual existing project structure.
- Reuse established implementation patterns.
- Make the smallest reasonable deviation.
- Report the deviation after implementation.

## 4. Autonomous Execution Policy

Within `C:\workbench`, Codex should autonomously complete normal development
operations that are clearly within the user's requested scope.

Do not request additional confirmation for normal operations such as:

- Reading source files.
- Searching source code.
- Inspecting project structure.
- Reading referenced specifications.
- Creating or modifying files required by the task.
- Running targeted compilation.
- Running targeted unit tests.
- Running relevant lint or type checks.
- Reviewing SQL or DDL.
- Inspecting Git diff and changed files.
- Other ordinary non-destructive development operations already allowed by
  the active task and Sandbox.

Do not ask the user to approve individual file edits or ordinary inspection
commands.

Do not ask whether targeted validation should be executed when it is already
appropriate under these rules.

Continue through implementation and targeted verification without stopping for
routine intermediate confirmations.

Ask the user only when proceeding would require:

- Destructive database or filesystem operations.
- Changes outside `C:\workbench`.
- Changes to credentials, secrets, tokens, or security settings.
- Significant architectural changes.
- Material changes to business behavior.
- Multiple valid choices that would create materially different user-visible
  behavior.
- An operation explicitly blocked by the Sandbox.

## 5. Handling Ambiguity

When requirements are ambiguous:

1. Inspect the existing implementation.
2. Check any referenced specification.
3. Check related API contracts and database structures.
4. Follow existing architecture and implementation patterns.
5. Prefer the smallest compatible change.

Resolve minor technical ambiguity without interrupting the user.

Examples of decisions that normally do not require user confirmation:

- Which existing Service pattern to follow.
- Which existing DTO style to reuse.
- Method or variable naming that follows current conventions.
- Whether an existing Mapper or Repository pattern should be reused.
- Minor internal implementation details with no business impact.

Ask the user only when ambiguity affects:

- Business rules.
- Data integrity.
- Security.
- External interfaces.
- Destructive operations.
- Significant user-visible behavior.

If a reasonable assumption is necessary, make the smallest safe assumption and
report it briefly after implementation.

## 6. Source Analysis

Before modifying code:

1. Read the user's request and referenced specification, if any.
2. Identify likely affected modules, directories, and files.
3. Inspect the relevant existing implementation.
4. Identify the minimum affected file set.
5. Trace only the necessary dependencies and data flow.
6. Check related APIs and database objects when applicable.
7. Reuse existing components, services, utilities, DTOs, APIs, and patterns.

### Exploration rules

- Do not scan or read the entire repository unless necessary.
- Search likely affected directories first.
- Follow symbols, imports, references, API calls, and call chains only as needed.
- Expand the search scope incrementally.
- Do not repeatedly scan the entire project.
- Do not repeatedly read unchanged files.
- Prefer targeted file sections instead of entire large files.
- Reuse information already discovered during the current task.
- Do not repeat architectural analysis that is already established.

Exclude from broad searches unless explicitly required:

- `node_modules`
- `.git`
- `dist`
- `build`
- `coverage`
- generated files

## 7. Specification-Driven Work

When the user references a file under `docs\specs\`:

1. Read the specification before modifying source code.
2. Treat it as the primary functional requirement.
3. Compare it with the actual current source.
4. Identify only what is already implemented, partially implemented, missing,
   or inconsistent.
5. Implement only the changes required to satisfy the specification.
6. Preserve existing compatible functionality.
7. Do not broaden the task beyond the specification.

If expected file names or components in the specification differ from the
actual source:

- Do not create duplicate architecture merely to match the document.
- Follow the actual source structure.
- Preserve the specification's functional intent.

### Specification execution rule

When the specification is sufficiently complete:

- Do not repeat requirement analysis already settled in the specification.
- Do not redesign the feature unless the actual source materially conflicts
  with the specification.
- Inspect only enough source to map the specification to the actual code.
- Proceed to implementation once the implementation path is clear.
- Do not create another planning document unless explicitly requested.
- Do not rewrite the specification merely to restate the same requirements.

## 8. Planning-Only Requests

When the user explicitly requests only analysis, review, investigation, design,
or implementation planning:

- Inspect only relevant source and reference files.
- Compare requirements with the current implementation.
- Identify implemented, partial, and missing functionality.
- Identify affected frontend, backend, database, and configuration areas.
- Produce a concise implementation approach.
- Define appropriate verification criteria.
- Identify material risks and unresolved assumptions.

Do not:

- Modify application source files.
- Execute database changes.
- Start development servers.
- Begin implementation.

If the user asks Codex to create or update a specification under `docs\specs\`,
only the requested specification file may be written unless additional changes
are explicitly requested.

## 9. Implementation Rules

When implementation is requested:

- Make the smallest change necessary.
- Modify only files directly related to the task.
- Follow the referenced specification when one exists.
- Preserve existing APIs, business logic, routing, and database behavior unless
  a change is explicitly required.
- Preserve existing architecture and implementation patterns.
- Reuse existing components, styles, utilities, services, DTOs, and APIs.
- Do not perform unrelated refactoring.
- Do not reorganize unrelated files.
- Do not add unnecessary abstractions.
- Do not add new frameworks or dependencies unless required.
- Do not remove existing functionality unless required.
- Implement changes in small, logically related units.

### Shared frontend design rules

- Define reusable design values in `frontend/src/assets/design-tokens.css`.
- Define shared component appearance in `frontend/src/assets/ui-components.css`.
- Keep view-scoped styles limited to screen-specific layout and unique states.
- Do not duplicate shared radius, color, shadow, control, table, dialog, alert,
  or status styles in individual views. Intentional circles and directional
  corner treatments are exceptions.

Once implementation begins:

- Continue through implementation and targeted verification.
- Do not stop for routine intermediate approvals.
- Resolve non-critical technical issues using the smallest compatible change
  when the correct approach is clear from existing code.

### Source formatting

- Before completing any source-code modification or new development, format all
  source files created or modified by Codex during the current task.
- For frontend source, use the project's configured Prettier formatter. Prefer
  formatting only the files changed in the current task with
  `npm.cmd --prefix frontend exec -- prettier --write --experimental-cli <files>`.
- Use `npm run format` from the workspace root only when formatting the entire
  frontend source tree is explicitly appropriate.
- Do not format unrelated files or overwrite unrelated user changes.
- If an affected project area has no configured formatter, preserve its existing
  style and report that automated formatting was unavailable.
- Review the resulting diff after formatting and before running final validation.

## 10. Database Rules

- Do not modify the database schema unless required by the task or specification.
- Follow the project's existing DDL or migration approach.
- Review SQL syntax and schema compatibility.
- Consider impact on existing tables and data.
- Prefer non-destructive validation.

Do not:

- Drop or truncate existing tables without explicit authorization.
- Execute destructive DDL or DML without explicit authorization.
- Run database migrations automatically unless explicitly requested.

## 11. Command Execution

The primary environment is Windows.

Prefer Windows-compatible commands and PowerShell where appropriate.

Before running commands:

- Confirm the intended working directory.
- Operate within `C:\workbench` whenever possible.
- Use explicit paths when necessary.
- Quote paths containing spaces or special characters.
- Prefer read-only commands during analysis.
- Avoid unnecessarily broad recursive commands.

Useful inspection commands include:

- `Get-Location`
- `Get-ChildItem`
- `Get-Content`
- `Test-Path`
- `rg --files`
- `rg`

Do not request confirmation for ordinary read-only commands within the workspace.

Do not request confirmation for targeted build or test commands already permitted
by the current task.

### Command failure handling

When a command fails:

1. Determine whether the failure occurred during environment initialization or
   after command execution began.
2. Distinguish Sandbox failures from filesystem permission failures.
3. Distinguish missing commands or invalid paths from execution failures.
4. Retry with a simpler or more targeted command when appropriate.
5. Do not repeatedly execute the same failing command without changing the
   diagnostic approach.
6. Do not modify Sandbox configuration or disable security restrictions automatically.
7. Never assume a command succeeded without confirming its result.

If the required files or commands cannot be accessed, report the actual limitation.

Never claim analysis or verification was completed when it was not.

## 12. Verification Strategy

Use the minimum verification appropriate to the change.

Verification should be proportional to implementation risk.

### General rules

- Verify changed files and their direct dependencies first.
- Prefer targeted compilation, tests, linting, type checking, or static checks.
- Do not automatically run full-project builds or complete test suites.
- Expand validation only when:
  - targeted verification fails,
  - shared or core functionality changed,
  - regression risk is significant,
  - or the user explicitly requests broader verification.
- Do not repeatedly run identical tests when relevant code has not changed.
- Prefer non-interactive validation commands.
- Never report a test as passed unless it actually completed successfully.

### Frontend

Use only relevant checks when configured and necessary:

- relevant unit tests
- lint checks
- type checks
- `npm run build`
- existing project-specific validation scripts

Do not run `npm run dev` unless explicitly requested.

Do not automatically start the frontend development server.

Do not perform browser-based runtime testing that requires starting a
development server unless explicitly requested.

### Backend

- Prefer targeted compilation or build checks.
- Run relevant unit tests when available.
- Use targeted validation before broader testing.
- Do not automatically start a persistent backend server.
- Expand validation only when justified by the affected scope.

### Runtime verification

If adequate verification requires starting a development server:

- Do not start it automatically unless explicitly requested.
- Report what could not be runtime-verified.
- Provide the relevant command or manual verification procedure when useful.
- Mark the affected functionality as not runtime-verified.

## 13. Token and Time Efficiency

Minimize unnecessary source exploration, reasoning repetition, command execution,
and verification while preserving correctness.

- Do not repeat analysis already established in a referenced specification.
- Do not redesign requirements already decided in `docs\specs\`.
- Do not repeat architectural analysis unless the source contradicts the
  specification or implementation requires additional investigation.
- Do not read large files in full when targeted sections are sufficient.
- Do not repeatedly read unchanged files.
- Do not repeatedly execute the same command without a technical reason.
- Avoid full-project builds and test suites for localized changes.
- Expand analysis and verification incrementally.
- For straightforward tasks, proceed after targeted inspection.
- For complex tasks, create only a concise implementation sequence.
- Do not narrate every intermediate reasoning step or command.
- Keep intermediate status updates minimal.
- Prefer execution over repeated explanation once the implementation direction
  is clear.
- Do not sacrifice correctness, security, or necessary verification merely to
  reduce token usage.

## 14. Development Restrictions

Do not:

- Modify files outside `C:\workbench`.
- Modify or delete external attachment files.
- Delete unrelated source files or directories.
- Modify credentials, secrets, passwords, tokens, or private keys.
- Expose sensitive information in logs or reports.
- Change dependency versions unless required by the task.
- Install new packages unless required by the task.
- Perform destructive database operations without explicit authorization.
- Drop or truncate existing database tables without explicit authorization.
- Make unrelated architectural changes.
- Perform broad refactoring unrelated to the task.
- Remove existing functionality without a clear requirement.
- Override Sandbox security settings to bypass execution failures.
- Run destructive Git commands without explicit authorization.
- Overwrite existing user changes without examining their relevance first.

## 15. Completion Report

After implementation, report concisely:

1. **Summary** — what was implemented or changed.
2. **Modified files** — files created, updated, or deleted.
3. **Key changes** — important implementation details.
4. **Validation** — checks actually executed and their results.
5. **Not validated** — relevant tests or runtime checks not performed.
6. **Specification deviations** — only when applicable.
7. **Limitations / risks** — only when meaningful.

Do not include empty or meaningless sections.

For simple changes, keep the report very short.

For planning-only requests, report only information required for the next
design or implementation decision.
