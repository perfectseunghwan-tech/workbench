# Workspace Rules

## Workspace

The active development workspace is:

`C:\workbench`

Codex may read, create, modify, rename, and delete files inside this workspace when required to complete the requested task.

Do not modify files outside `C:\workbench`.

## Allowed Work

Codex may:

- inspect existing source code
- create and modify source files
- create and modify tests
- create and modify SQL/DDL scripts
- update project configuration when required
- update project documentation
- run builds and tests related to the requested task

## Development Procedure

Before making changes:

1. Inspect the relevant existing implementation.
2. Identify the minimum set of affected files.
3. Follow the existing project architecture and conventions.
4. Reuse existing components and utilities where possible.
5. Avoid unrelated refactoring.
6. Run relevant validation after changes.

## Restrictions

Do not:

- modify files outside `C:\workbench`
- delete unrelated files
- modify credentials, secrets, or private keys
- change dependency versions unless required
- perform destructive database operations unless explicitly requested
- make unrelated architectural changes

## Verification Rules

After implementation:

- Do not run `npm run dev`.
- Do not start long-running development servers unless explicitly requested.
- Prefer non-interactive validation commands.
- For frontend changes, use commands such as:
  - `npm run build`
  - relevant unit tests
  - lint or type-check commands if configured
- For backend changes, use build and test commands only as needed.
- Report which validation commands were executed and their results.
- If validation cannot be completed without starting a development server, report that limitation instead of running `npm run dev`.
