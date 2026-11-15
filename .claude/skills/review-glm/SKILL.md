---
name: review-glm
description: Run a strict code review of a git diff with the GLM model (glm-5.3) via the z.ai API. Use when the user asks to review changes/slices/a range with GLM, e.g. "/review-glm HEAD~3..HEAD". The script sends the diff + commit log to the model; Claude then verifies every finding against the code before acting on it.
---

# Review a diff with GLM (z.ai)

`scripts/glm_review.py` sends `git diff` + `git log --oneline` to z.ai's
OpenAI-compatible endpoint (`/chat/completions`, model `glm-5.3`) with a strict
reviewer system prompt and prints the model's report.

## Prerequisites

- API key in `ZAI_API_KEY` or in `~/.config/zai/token` (single line, chmod 600).
- Optional env overrides: `ZAI_BASE_URL` (default `https://api.z.ai/api/paas/v4`),
  `ZAI_MODEL` (default `glm-5.3`).

## Run

```bash
python3 scripts/glm_review.py                       # last commit (HEAD~1..HEAD)
python3 scripts/glm_review.py main..HEAD            # a range
python3 scripts/glm_review.py --staged              # staged changes
python3 scripts/glm_review.py HEAD~5..HEAD --brief docs/review-notes.md  # add requirements
```

`--brief FILE` prepends a feature-intent document so the reviewer checks the diff
against the requirements, not only against itself.

## After the report arrives — triage, do not swallow

The model output is a proposal, not truth. Before acting on any finding:

1. Open the cited file and verify the claim against the actual code.
2. Construct the failure scenario yourself; if you cannot, mark it UNVERIFIED and say so.
3. Fix confirmed findings as separate small commits (one concern per commit), re-run
   the affected test level from the verification ladder, and report which findings were
   rejected and why.

Never repeat a finding to the user as fact before it has been verified against the code.
