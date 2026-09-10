#!/usr/bin/env python3
"""
Verifies that every resource referenced from Kotlin, XML or the manifest exists,
and lists resources that are declared but never used.

Without an Android SDK in this environment this static pass is the only way to
catch a missing string / drawable / id before the build fails on a real machine.

Run:  python3 tools/check_resources.py
Exit code 0 = every reference resolves.
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
JAVA = os.path.join(MAIN, "java")
RES = os.path.join(MAIN, "res")

TYPES = ["string", "drawable", "mipmap", "layout", "id", "raw", "anim", "color", "dimen",
         "style", "integer", "bool", "array", "attr", "font"]

# Resources that are referenced by the framework, the launcher or the store listing
# and therefore legitimately "unused" from our own code.
IGNORE_UNUSED = {
    "drawable": {"ic_launcher_background", "ic_launcher_foreground", "ic_launcher_monochrome"},
    "mipmap": {"ic_launcher", "ic_launcher_round"},
    "layout": {"item_level", "item_daily_day", "view_powerup_row"},  # used via the adapter / dialogs
}


def parse_values(directory):
    """Collects <type name="..."> entries from every values/*.xml file."""
    declared = defaultdict(set)
    files = defaultdict(list)
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".xml"):
            continue
        path = os.path.join(directory, name)
        with open(path, encoding="utf-8") as handle:
            content = handle.read()
        for match in re.finditer(r"<(\w+)[^>]*\bname=\"([^\"]+)\"", content):
            kind, res_name = match.group(1), match.group(2)
            # <item> entries inside a <style> are attributes, not resources.
            if kind in ("string", "color", "dimen", "style", "integer", "bool", "array"):
                declared[kind].add(res_name)
                files[(kind, res_name)].append(name)
    return declared, files


def parse_res_dirs():
    declared = defaultdict(set)
    for entry in sorted(os.listdir(RES)):
        folder = os.path.join(RES, entry)
        if not os.path.isdir(folder):
            continue
        kind = entry.split("-")[0]
        if kind not in TYPES:
            continue
        for name in sorted(os.listdir(folder)):
            if name == "values":
                continue
            if kind in ("drawable", "layout", "anim", "color", "menu", "xml", "font"):
                if not name.endswith(".xml"):
                    continue
            elif kind == "mipmap":
                if not (name.endswith(".png") or name.endswith(".xml")):
                    continue
            if kind == "mipmap" and name.startswith("ic_launcher"):
                declared["mipmap"].add(name[:-4])
                continue
            declared[kind].add(name.rsplit(".", 1)[0])
    return declared


def kotlin_references():
    refs = defaultdict(set)
    for base, _, names in os.walk(JAVA):
        for name in names:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as handle:
                content = handle.read()
            for kind in TYPES:
                for match in re.finditer(rf"\bR\.{kind}\.(\w+)", content):
                    refs[kind].add(match.group(1))
    return refs


def xml_references():
    """Every @type/name reference made by layouts, drawables and the manifest."""
    refs = defaultdict(set)
    targets = [os.path.join(folder, name)
               for folder, _, names in os.walk(RES)
               for name in names if name.endswith(".xml")]
    targets.append(os.path.join(MAIN, "AndroidManifest.xml"))
    for path in targets:
        with open(path, encoding="utf-8") as handle:
            content = handle.read()
        # Style parents may point at framework styles, so they are not references.
        content_without_parents = re.sub(r'parent="[^"]*"', "", content)
        for kind in TYPES:
            # Style names contain dots: R.style.Dialog_BubbleBlast <-> Dialog.BubbleBlast
            pattern = rf"@{kind}/([\w.]+)" if kind == "style" else rf"@{kind}/(\w+)"
            for match in re.finditer(pattern, content_without_parents):
                name = match.group(1).replace(".", "_") if kind == "style" else match.group(1)
                refs[kind].add(name)
            for match in re.finditer(rf"@\+{kind}/(\w+)", content_without_parents):
                refs["declared_id"].add(match.group(1))
                refs[kind].add(match.group(1))
    return refs


def structural_checks():
    """XML well-formedness, duplicate declarations and resource file naming rules."""
    import xml.etree.ElementTree as ET
    from collections import Counter

    problems = []

    for base, _, names in os.walk(os.path.dirname(RES)):
        for name in names:
            if not name.endswith(".xml"):
                continue
            path = os.path.join(base, name)
            try:
                ET.parse(path)
            except ET.ParseError as error:
                problems.append(f"{os.path.relpath(path, MAIN)}: malformed XML ({error})")

    seen = Counter()
    for folder in ("values",):
        directory = os.path.join(RES, folder)
        for name in sorted(os.listdir(directory)):
            if not name.endswith(".xml"):
                continue
            with open(os.path.join(directory, name), encoding="utf-8") as handle:
                content = handle.read()
            for match in re.finditer(r"<(string|color|dimen|style|integer|bool|array)[^>]*\bname=\"([^\"]+)\"", content):
                seen[(match.group(1), match.group(2), name)] += 1
    for (kind, res_name, file_name), count in sorted(seen.items()):
        if count > 1:
            problems.append(f"duplicate <{kind} name=\"{res_name}\"> declared {count} times (last in {file_name})")

    for base, _, names in os.walk(RES):
        for name in names:
            if name == ".DS_Store":
                continue
            stem = name.rsplit(".", 1)[0]
            if stem != stem.lower() or "-" in stem:
                problems.append(f"{os.path.relpath(os.path.join(base, name), MAIN)}: "
                                f"resource file names must be lowercase (aapt2 requirement)")
    return problems


def main() -> int:
    values_dir = os.path.join(RES, "values")
    declared_values, value_files = parse_values(values_dir)
    declared = parse_res_dirs()
    for kind, names in declared_values.items():
        declared[kind] |= names

    # ids declared inline in layouts belong to the layout namespace
    kotlin = kotlin_references()
    xml = xml_references()
    # "@+id/foo" in a layout *declares* foo; everything else only references it.
    declared["id"] |= xml["declared_id"]
    declared["id"] |= {name for name in re.findall(r'android:id="@\+?id/(\w+)"',
                                                   "".join(open(os.path.join(folder, name), encoding="utf-8").read()
                                                            for folder, _, names in os.walk(os.path.join(RES, "layout"))
                                                            for name in names))}

    errors = structural_checks()
    for kind in TYPES:
        wanted = kotlin.get(kind, set()) | xml.get(kind, set())
        if kind == "attr":
            continue  # framework attributes only
        available = declared.get(kind, set())
        if kind in ("string", "color", "dimen", "integer", "bool", "style", "array"):
            available = available | declared_values.get(kind, set())
        if kind == "style":
            # "Dialog.BubbleBlast" in XML is R.style.Dialog_BubbleBlast in code.
            available = {name.replace(".", "_") for name in available}
        for name in sorted(wanted - available):
            errors.append(f"missing @{kind}/{name}")

    # Layouts referenced from Kotlin as R.layout.* also cover adapter rows.
    layout_refs = set()
    for base, _, names in os.walk(JAVA):
        for name in names:
            if name.endswith(".kt"):
                with open(os.path.join(base, name), encoding="utf-8") as handle:
                    layout_refs |= set(re.findall(r"R\.layout\.(\w+)", handle.read()))
    for name in sorted(layout_refs - declared["layout"]):
        errors.append(f"missing @layout/{name}")

    unused = []
    for kind, names in sorted(declared.items()):
        used = kotlin.get(kind, set()) | xml.get(kind, set()) | declared.get("declared_id", set())
        for name in sorted(names):
            if name in used:
                continue
            if name in IGNORE_UNUSED.get(kind, set()):
                continue
            if kind in ("id", "color", "style"):
                # ids live and die inside their own layout; colours and styles form
                # the design palette and are intentionally broader than today's use.
                continue
            unused.append(f"{kind}/{name}")

    print(f"strings: {len(declared_values['string'])}  colors: {len(declared_values['color'])}  "
          f"dimens: {len(declared_values['dimen'])}  drawables: {len(declared['drawable'])}  "
          f"layouts: {len(declared['layout'])}  raw: {len(declared['raw'])}  anim: {len(declared['anim'])}")
    if errors:
        print(f"\nMISSING RESOURCES ({len(errors)}):")
        for error in errors:
            print("  " + error)
    if unused:
        print(f"\ndeclared but unused ({len(unused)}):")
        for name in unused:
            print("  " + name)
    if errors:
        return 1
    print("\nALL RESOURCE REFERENCES RESOLVE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
