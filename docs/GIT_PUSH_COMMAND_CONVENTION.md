# Git Push Command Convention

Last updated: 2026-11-28 (Asia/Kolkata)

This file records the user's shorthand commands for Git work on this repository. Treat these as standing instructions unless the user explicitly changes them.

## Command meanings

The following phrases all mean **push the project files to Git**, not merely create Markdown documentation:

- "Push .md files"
- "Push md files"
- "Push files"
- "Push the files"
- "Push everything"
- "Push all helpful files"

Despite the wording ".md files", the command is not restricted to Markdown. It means commit and push all new or modified files that belong to the requested work, including (when applicable):

- Android source code and resources
- Gradle/build configuration
- SQL migrations and Supabase Edge Function source
- JSON, CSV, scripts, and other project assets
- Markdown documentation and handoff notes
- Tests and supporting configuration that is safe to commit

## Required workflow when the user gives the command

1. Inspect the current repository, branch, and working changes first.
2. Identify all new and modified files relevant to the task.
3. Include the useful documentation: implementation details, decisions, handoff notes, setup/rebuild instructions, and verification status.
4. Do not include secrets, passwords, service-role keys, private credentials, local machine files, generated build outputs, or unrelated changes.
5. Preserve existing user changes. Never reset, overwrite, or discard work just to make the push easier.
6. Run appropriate checks when possible and clearly report what was and was not verified.
7. Commit and push the complete relevant set of files to the agreed/current feature branch. Do not silently switch branches, merge, or push to `main`.
8. After pushing, verify the commit and list the files included.

## Important interpretation

"Push .md files" is historical shorthand. **Do not interpret it literally as "only push .md files".** Interpret it as "push all relevant project files, and make sure the Markdown documentation is also updated."

If the user explicitly says "only Markdown" or names a restricted file set, follow that narrower instruction instead.

## Safety boundaries

- Never commit secrets or real store passwords.
- Do not change live Supabase data, schema, policies, functions, or project configuration merely because the user asked to push files. Git changes and live backend changes are separate actions.
- Do not claim a remote push, successful build, APK installation, or device test unless it actually happened.
- Keep the repository's current branch/workflow guardrails and the user's explicit branch instructions.
