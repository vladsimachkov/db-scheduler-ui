#!/usr/bin/env python3
# Review a git diff with a strict reviewer model served by z.ai's OpenAI-compatible API.
#
# Usage:
#   python3 scripts/glm_review.py [git diff args...] [--brief FILE] [--model NAME]
#   python3 scripts/glm_review.py HEAD~3..HEAD
#   python3 scripts/glm_review.py main..HEAD --brief docs/review-notes.md
#
# Auth: ZAI_API_KEY env var, or a file at ~/.config/zai/token (chmod 600).
# Optional env: ZAI_BASE_URL (default https://api.z.ai/api/paas/v4), ZAI_MODEL (default glm-5.3).
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

DEFAULT_BASE_URL = "https://api.z.ai/api/paas/v4"
DEFAULT_MODEL = "glm-5.3"
TOKEN_FILE = os.path.expanduser("~/.config/zai/token")
MAX_DIFF_CHARS = 600_000

SYSTEM_PROMPT = """\
You are a strict, adversarial code reviewer. You review the git diff you are given.

Rules:
- Report only defects you can substantiate from the diff and the context provided.
  If you suspect something but cannot construct a concrete failure scenario, label it UNVERIFIED.
- Never invent file paths, line numbers or symbols; quote the code you rely on.
- Rank every finding: BLOCKER (breaks build/data/security), MAJOR (wrong behavior in a
  realistic scenario), MINOR (edge case, small correctness/UX gap), NIT (style/consistency).
- For each finding give: rank, file:line, a one-sentence defect statement, a concrete
  failure scenario, and a suggested fix.
- Also list verified non-issues you checked, briefly, so the reader knows your coverage.
- End with a verdict line: VERDICT: SATISFIED or VERDICT: CHANGES REQUESTED.
- Be concise; no praise, no filler.
"""


def read_key() -> str:
    key = os.environ.get("ZAI_API_KEY", "").strip()
    if key:
        return key
    try:
        with open(TOKEN_FILE) as fh:
            return fh.read().strip()
    except OSError:
        sys.exit(
            "No API key: set ZAI_API_KEY or write the key to " + TOKEN_FILE + " (chmod 600)."
        )


def run_git(args: list[str]) -> str:
    proc = subprocess.run(["git"] + args, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.exit("git " + " ".join(args) + " failed: " + proc.stderr.strip())
    return proc.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description="Strict diff review via z.ai GLM")
    parser.add_argument("gitargs", nargs="*", default=["HEAD~1..HEAD"],
                        help="git diff arguments (default: HEAD~1..HEAD)")
    parser.add_argument("--brief", help="file describing the feature intent to review against")
    parser.add_argument("--model", default=os.environ.get("ZAI_MODEL", DEFAULT_MODEL))
    parser.add_argument("--base-url", default=os.environ.get("ZAI_BASE_URL", DEFAULT_BASE_URL))
    args = parser.parse_args()

    diff = run_git(["diff"] + args.gitargs)
    if not diff.strip():
        sys.exit("Empty diff for: git diff " + " ".join(args.gitargs))
    log = run_git(["log", "--oneline"] + (args.gitargs[:1] if args.gitargs else ["HEAD~1..HEAD"]))
    stat = run_git(["diff", "--stat"] + args.gitargs)

    brief = ""
    if args.brief:
        with open(args.brief) as fh:
            brief = "\n\nFeature intent / requirements:\n" + fh.read()

    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS] + "\n... [diff truncated]"

    user_prompt = (
        "Commits in range:\n" + log + "\nDiff stat:\n" + stat + brief +
        "\n\nFull diff:\n```diff\n" + diff + "\n```\n\nReview it."
    )

    body = json.dumps({
        "model": args.model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
    }).encode()

    request = urllib.request.Request(
        args.base_url.rstrip("/") + "/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + read_key(),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            payload = json.loads(response.read())
    except urllib.error.HTTPError as err:
        sys.exit("HTTP " + str(err.code) + ": " + err.read().decode(errors="replace")[:2000])
    except urllib.error.URLError as err:
        sys.exit("Network error: " + str(err.reason))

    try:
        print(payload["choices"][0]["message"]["content"])
    except (KeyError, IndexError):
        sys.exit("Unexpected response shape: " + json.dumps(payload)[:2000])


if __name__ == "__main__":
    main()
