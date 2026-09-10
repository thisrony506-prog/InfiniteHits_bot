#!/usr/bin/env python3
"""
Turns a failed Gradle build log into check-run annotations and a job summary.

GitHub truncates a single annotation message to about 4 kB, so emitting the whole
compiler output as one annotation hides everything after the first few errors.
This script emits one annotation per problem instead, which is what made the very
first Kotlin compilation of this project debuggable from the run page alone.

Usage (as used by .github/workflows/android-debug-apk.yml):

    python3 tools/ci/build_diagnostics.py <gradle.log> <job-summary.md> [max]

`max` defaults to 50, the number of annotations GitHub keeps per check run.
Exit code is always 0: the surrounding build step has already failed.
"""
import re
import sys

KOTLIN_ERROR = [r"^\s*e: ", r"^\s*e:file"]
GRADLE_PROBLEM = [
    r"(?i)error:",
    r"(?i)^\s*FAILURE:",
    r"(?i)what went wrong",
    r"(?i)execution failed for task",
    r"(?i)caused by:",
    r"(?i)could not (resolve|find|determine|download|create|load|open)",
    r"(?i)^\s*\* try:",
    r"^\s*>\s.*FAILED",
    r"(?i)unsupported class file",
    r"(?i)incompatible with attribute",
]


def select(lines):
    """Compiler errors if there are any, otherwise Gradle's own explanation."""
    errors = [line for line in lines if any(re.search(p, line) for p in KOTLIN_ERROR)]
    if errors:
        return errors
    problems = [line for line in lines if any(re.search(p, line) for p in GRADLE_PROBLEM)]
    if problems:
        return problems
    return lines[-200:]


def dedupe(lines):
    seen = set()
    unique = []
    for line in lines:
        stripped = line.strip()
        if stripped and stripped not in seen:
            seen.add(stripped)
            unique.append(stripped)
    return unique


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 0

    log_path, summary_path = argv[1], argv[2]
    limit = int(argv[3]) if len(argv) > 3 else 50

    try:
        with open(log_path, errors="replace") as handle:
            lines = handle.read().splitlines()
    except OSError as error:
        print(f"::warning::Could not read the build log: {error}")
        return 0

    problems = dedupe(select(lines))
    if not problems:
        print("::error title=Build failed::Gradle failed without leaving any output.")
        return 0

    emitted = 0
    for line in problems:
        if emitted >= limit:
            break
        message = line.replace("%", "%25").replace("\r", "").replace("\n", "%0A")[:3000]
        print(f"::error title=Build error {emitted + 1}::{message}")
        emitted += 1

    if len(problems) > emitted:
        print(f"::warning::{len(problems) - emitted} more line(s) are in the job summary")

    try:
        with open(summary_path, "a") as summary:
            summary.write("### Debug APK build failed\n\n")
            summary.write(f"{len(problems)} relevant log line(s):\n\n```\n")
            summary.write("\n".join(problems)[:200000])
            summary.write("\n```\n")
    except OSError:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
