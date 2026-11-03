#!/usr/bin/env python3

"""Query a Z.AI / GLM chat-completions endpoint from the local terminal."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_BASE_URL = "https://api.z.ai/api/paas/v4"
DEFAULT_MODEL = "glm-5"
PROFILE_PRESETS: dict[str, dict[str, Any]] = {
    "research": {
        "model": "glm-5",
        "thinking": "on",
        "max_tokens": 6000,
        "temperature": 0.2,
    },
    "debug": {
        "model": "glm-5",
        "thinking": "on",
        "max_tokens": 5000,
        "temperature": 0.1,
    },
}


def load_claude_env_from_settings() -> dict[str, str]:
    settings_paths = [
        Path.cwd() / ".claude/settings.secrets.json",
        Path.cwd() / ".claude/settings.local.json",
        Path.cwd() / ".claude/settings.json",
        Path.home() / ".claude/settings.secrets.json",
        Path.home() / ".claude/settings.local.json",
        Path.home() / ".claude/settings.json",
    ]
    merged: dict[str, str] = {}
    for path in settings_paths:
        if not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        env = data.get("env")
        if not isinstance(env, dict):
            continue
        for key, value in env.items():
            if isinstance(value, str) and value.strip() and key not in merged:
                merged[key] = value
    return merged


def resolve_env_value(name: str) -> str | None:
    direct = os.getenv(name, "").strip()
    if direct:
        return direct
    return load_claude_env_from_settings().get(name)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Send a prompt to Z.AI / GLM using a local CLI wrapper.",
        epilog=(
            "Examples:\n"
            "  .claude/skills/glm/scripts/glm_query.py --prompt \"Summarize this stack trace\"\n"
            "  .claude/skills/glm/scripts/glm_query.py --profile research --json --prompt \"Map entry points for this feature\"\n"
            "  .claude/skills/glm/scripts/glm_query.py --profile debug --prompt \"Analyze these logs and suggest root causes\"\n"
            "  .claude/skills/glm/scripts/glm_query.py --model glm-5-air --prompt \"Draft 3 fix ideas\"\n"
            "  .claude/skills/glm/scripts/glm_query.py --prompt-file notes/prompt.txt --json\n"
            "  printf '%s' 'Explain this error' | .claude/skills/glm/scripts/glm_query.py --stdin\n"
            "  .claude/skills/glm/scripts/glm_query.py --dry-run --prompt \"Check payload shape\"\n"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "prompt_parts",
        nargs="*",
        help="Prompt text as positional arguments. Ignored when --prompt/--prompt-file/--stdin is used.",
    )
    parser.add_argument(
        "--prompt",
        help="Prompt text to send as the user message.",
    )
    parser.add_argument(
        "--prompt-file",
        help="Read the prompt body from a file.",
    )
    parser.add_argument(
        "--stdin",
        action="store_true",
        help="Read the prompt body from stdin.",
    )
    parser.add_argument(
        "--system",
        help="Optional system message.",
    )
    parser.add_argument(
        "--profile",
        choices=sorted(PROFILE_PRESETS),
        help="Apply a pipeline preset such as research or debug.",
    )
    parser.add_argument(
        "--model",
        help=f"Model id to use. Default: $GLM_MODEL or {DEFAULT_MODEL}; profiles may override the default.",
    )
    parser.add_argument(
        "--base-url",
        help=(
            "Base URL for the Z.AI-compatible API. "
            f"Default: $ZAI_BASE_URL or {DEFAULT_BASE_URL}."
        ),
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=180,
        help="HTTP timeout in seconds. Default: 180.",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        help="Optional max_tokens value for the API request.",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        help="Optional temperature value for the API request.",
    )
    parser.add_argument(
        "--thinking",
        choices=("auto", "on", "off"),
        default="auto",
        help="Explicitly control deep thinking. Profiles research/debug force this to on unless overridden.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print a normalized JSON object instead of plain text.",
    )
    parser.add_argument(
        "--raw-json",
        action="store_true",
        help="Print the raw API response JSON.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the prepared request payload without making a network call.",
    )
    return parser


def read_text_file(path: str) -> str:
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def resolve_prompt(args: argparse.Namespace) -> str:
    sources = [
        bool(args.prompt),
        bool(args.prompt_file),
        bool(args.stdin),
        bool(args.prompt_parts),
    ]
    if sum(sources) == 0:
        raise ValueError("Provide a prompt via --prompt, --prompt-file, --stdin, or positional text.")
    if sum(sources) > 1 and not (args.prompt_parts and not any([args.prompt, args.prompt_file, args.stdin])):
        raise ValueError("Use only one prompt source at a time.")

    if args.prompt is not None:
        prompt = args.prompt
    elif args.prompt_file is not None:
        prompt = read_text_file(args.prompt_file)
    elif args.stdin:
        prompt = sys.stdin.read()
    else:
        prompt = " ".join(args.prompt_parts)

    prompt = prompt.strip()
    if not prompt:
        raise ValueError("Prompt is empty after reading the selected input source.")
    return prompt


def resolve_api_key() -> str:
    for env_name in ("ZAI_API_KEY", "GLM_API_KEY"):
        value = resolve_env_value(env_name)
        if value:
            return value
    raise RuntimeError(
        "Missing API key. Set ZAI_API_KEY or GLM_API_KEY before running this script."
    )


def apply_profile_defaults(args: argparse.Namespace) -> None:
    if not args.profile:
        return
    preset = PROFILE_PRESETS[args.profile]
    if args.model is None:
        args.model = preset["model"]
    if args.max_tokens is None:
        args.max_tokens = preset["max_tokens"]
    if args.temperature is None:
        args.temperature = preset["temperature"]
    if args.thinking == "auto":
        args.thinking = preset["thinking"]


def build_payload(args: argparse.Namespace, prompt: str) -> dict[str, Any]:
    messages: list[dict[str, str]] = []
    if args.system:
        messages.append({"role": "system", "content": args.system})
    messages.append({"role": "user", "content": prompt})

    payload: dict[str, Any] = {
        "model": args.model,
        "messages": messages,
        "stream": False,
    }
    if args.max_tokens is not None:
        payload["max_tokens"] = args.max_tokens
    if args.temperature is not None:
        payload["temperature"] = args.temperature
    if args.thinking == "on":
        payload["thinking"] = {"type": "enabled"}
    elif args.thinking == "off":
        payload["thinking"] = {"type": "disabled"}
    return payload


def flatten_content(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                if item.strip():
                    parts.append(item.strip())
                continue
            if not isinstance(item, dict):
                continue
            if isinstance(item.get("text"), str) and item["text"].strip():
                parts.append(item["text"].strip())
                continue
            for key in ("content", "value"):
                value = item.get(key)
                if isinstance(value, str) and value.strip():
                    parts.append(value.strip())
                    break
        return "\n\n".join(parts).strip()
    return str(content).strip()


def extract_text(response: dict[str, Any]) -> str:
    choices = response.get("choices")
    if isinstance(choices, list) and choices:
        first_choice = choices[0]
        message = first_choice.get("message", {})
        if isinstance(message, dict):
            text = flatten_content(message.get("content"))
            if text:
                return text
            reasoning = flatten_content(message.get("reasoning_content"))
            if reasoning:
                finish_reason = first_choice.get("finish_reason")
                if finish_reason == "length":
                    raise RuntimeError(
                        "The model spent the token budget on reasoning and emitted no final answer. "
                        "Retry with a larger --max-tokens value."
                    )
                raise RuntimeError(
                    "The model returned reasoning_content but no final answer. Retry with a larger "
                    "--max-tokens value or a simpler prompt."
                )

    output = response.get("output")
    if isinstance(output, list):
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            text = flatten_content(content)
            if text:
                return text

    for key in ("content", "text", "response"):
        text = flatten_content(response.get(key))
        if text:
            return text

    raise RuntimeError("The API response did not contain a readable text message.")


def request_completion(
    *,
    base_url: str,
    api_key: str,
    payload: dict[str, Any],
    timeout: int,
) -> dict[str, Any]:
    url = f"{base_url.rstrip('/')}/chat/completions"
    request = urllib.request.Request(
        url=url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from GLM endpoint: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Failed to reach GLM endpoint: {exc.reason}") from exc

    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"GLM endpoint returned non-JSON output: {raw}") from exc


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        apply_profile_defaults(args)
        if args.model is None:
            args.model = resolve_env_value("GLM_MODEL") or DEFAULT_MODEL
        if args.base_url is None:
            args.base_url = resolve_env_value("ZAI_BASE_URL") or DEFAULT_BASE_URL
        prompt = resolve_prompt(args)
        payload = build_payload(args, prompt)
        if args.dry_run:
            print(
                json.dumps(
                    {
                        "base_url": args.base_url.rstrip("/"),
                        "endpoint": f"{args.base_url.rstrip('/')}/chat/completions",
                        "payload": payload,
                    },
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        api_key = resolve_api_key()
        response = request_completion(
            base_url=args.base_url,
            api_key=api_key,
            payload=payload,
            timeout=args.timeout,
        )

        if args.raw_json:
            print(json.dumps(response, ensure_ascii=False, indent=2))
            return 0

        text = extract_text(response)
        if args.json:
            print(
                json.dumps(
                    {
                        "model": response.get("model", args.model),
                        "text": text,
                        "usage": response.get("usage"),
                        "id": response.get("id"),
                    },
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        print(text)
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"glm_query.py: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
