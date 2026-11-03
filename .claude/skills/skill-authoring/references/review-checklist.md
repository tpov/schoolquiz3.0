# Skill Review Checklist

Use this checklist before finalizing a new skill or when reviewing an existing one.

## Triggering

- Does `description` explain both what the skill does and when to use it?
- Does `description` contain realistic user phrasing, including indirect requests?
- Is the description model-facing rather than a human marketing summary?

## Pattern Fit

- Should this content be a `.claude/rules/` file instead of a skill?
- Is one primary pattern explicit?
- Are secondary patterns justified by real failure modes?
- Would a simpler pattern make the skill easier to use and maintain?

## Content Quality

- Does `SKILL.md` focus on workflow and decision-making rather than dumping reference material?
- Does the skill avoid stating the obvious?
- Is there a `## Gotchas` section with real failure modes or footguns? (Not required for Domain Knowledge pattern — RIGHT/WRONG examples serve the same purpose.)
- Does the skill explain why key rules matter instead of only issuing rigid commands?
- Does the skill avoid unnecessary railroading?

## Name Validation (agentskills.io spec)

- Is `name` lowercase a-z and hyphens only?
- No consecutive hyphens (`--`)?
- No leading or trailing hyphen?
- Max 64 characters?
- Matches the directory name?

## Progressive Disclosure

- Are bulky details moved into `references/`?
- Are templates or output skeletons stored in `assets/`?
- Are deterministic repeated operations moved into `scripts/` only when justified?
- Does `SKILL.md` tell the model when to load each supporting file?

## Runtime Design

- Are runtime/frontmatter options minimal and intentional?
- Does the skill avoid holding always-on project standards that belong in `.claude/rules/`?
- If the skill has side effects, is manual invocation or an approval gate considered?
- If the skill needs config, does it know how to detect missing config and ask for it?
- If the skill stores data, is the location stable across upgrades?
- Are hooks included only when they are truly session-specific and valuable?

## Validation

- Are there 2-3 realistic test prompts?
- Would those prompts exercise the main trigger phrases and edge cases?
- If this is a migration or update, is there a clear note about what changed and what should be re-tested?

## Common Blockers

Treat these as high-risk failures:
- `description` is too generic, so the skill will undertrigger
- no clear primary pattern
- dangerous workflow with no approval checkpoint
- giant `SKILL.md` that duplicates reference docs
- mutable memory stored in the skill folder without a stability plan

## Final Question

If another engineer found this skill in six months, would they immediately understand:
- when it should trigger
- why its structure is shaped this way
- where to update detailed rules without bloating `SKILL.md`
