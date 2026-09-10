#!/usr/bin/env python3
"""
Cross-layer symbol check for the Kotlin sources.

The UI, the engine and the renderer were written as three separate layers, so the
riskiest class of bug is a call to a method that does not exist (or exists with a
different name) in one of the other layers. Without a compiler in this environment
this script stands in for the resolver: it parses every file, records the types and
their members, and then verifies that

  * every `import com.infinitehits...` resolves to something that exists;
  * every `Type.member` / `Type.CONSTANT` reference (type names are Capitalised,
    instance variables are not, so this is unambiguous) resolves in the type;
  * every custom view used in a layout is a real Kotlin class;
  * no import is left unused.

Run:  python3 tools/check_symbols.py
Exit code 0 = every symbol resolves.
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app", "src", "main", "java")
TEST_JAVA = os.path.join(ROOT, "app", "src", "test", "java")
LAYOUTS = os.path.join(ROOT, "app", "src", "main", "res", "layout")
PACKAGE_ROOT = "com.infinitehits.bubbleblast"

TYPE_DECL = re.compile(
    r"^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |open |sealed |data |enum |annotation )*"
    r"(class|interface|object)\s+([A-Z]\w*)"
)
FUN_DECL = re.compile(r"^\s*(?:@\w+\s+)*(?:public |internal |private |protected |override |open |abstract |"
                      r"suspend |inline |operator |infix |external )*fun\s+(?:<[^>]+>\s*)?(?:[\w?.<>]+\.)?(\w+)\s*\(")
VAL_DECL = re.compile(r"^\s*(?:@\w+\s+)*(?:public |internal |private |protected |override |open |abstract |"
                      r"const |lateinit |vararg )*(?:val|var)\s+(\w+)")
ENUM_ENTRY = re.compile(r"^\s{4,}([A-Z]\w*)\s*(?:\(|,|;|$)")
COMPANION = re.compile(r"^\s*(?:public |internal |private )*companion object")
SKIP_MEMBER_NAMES = {"constructor", "init", "equals", "hashCode", "toString", "copy"}

# Members every Kotlin type (or enum) has without declaring them.
BUILTINS = {
    "Companion", "entries", "values", "valueOf", "name", "ordinal", "compareTo", "clone", "copy",
    "javaClass", "let", "run", "apply", "also", "with", "takeIf", "takeUnless",
}


def strip_comments_and_strings(text):
    """Removes comments and string literals so that braces and names stay clean."""
    out = []
    index = 0
    length = len(text)
    while index < length:
        char = text[index]
        if text.startswith('"""', index):
            end = text.find('"""', index + 3)
            end = length if end < 0 else end + 3
            out.append(" " * (end - index))
            index = end
        elif char == '"':
            index += 1
            while index < length and text[index] != '"':
                index += 2 if text[index] == "\\" else 1
            out.append('""')
            index += 1
        elif char == "'":
            index += 1
            while index < length and text[index] != "'":
                index += 2 if text[index] == "\\" else 1
            out.append("''")
            index += 1
        elif text.startswith("//", index):
            end = text.find("\n", index)
            end = length if end < 0 else end
            out.append(" " * (end - index))
            index = end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            end = length if end < 0 else end + 2
            out.append(" " * (end - index))
            index = end
        else:
            out.append(char)
            index += 1
    return "".join(out)


class KotlinFile:
    def __init__(self, path):
        self.path = path
        with open(path, encoding="utf-8") as handle:
            self.raw = handle.read()
        self.text = strip_comments_and_strings(self.raw)
        package = re.search(r"^package\s+([\w.]+)", self.text, re.M)
        self.package = package.group(1) if package else ""
        self.imports = re.findall(r"^import\s+([\w.*]+)", self.text, re.M)
        self.types = {}              # type name -> set of member names
        self.type_depth = {}         # type name -> depth of its declaration line
        self.enum_entries = defaultdict(set)
        self._parse()

    def _parse(self):
        depth = 0
        # Stack entries: [type name, depth of the enclosing body, body entered, pending parens]
        stack = []
        companion_owner = None

        for line in self.text.split("\n"):
            declared = TYPE_DECL.match(line)
            own_parens = line.count("(") - line.count(")")

            if COMPANION.match(line) and stack:
                companion_owner = stack[-1][0]
            elif declared:
                name = declared.group(2)
                self.types.setdefault(name, set())
                if stack:
                    # A nested type is a member of the type that owns it.
                    self.types.setdefault(stack[-1][0], set()).add(name)
                stack.append([name, depth, False, 0])
                # A declaration that closes on the same line without a body
                # ("data class Point(x, y) : Base") owns no members.
                if "{" not in line and own_parens <= 0 and not line.rstrip().endswith((":", ",")):
                    stack.pop()

            if stack:
                owner = stack[-1][0]
                member = FUN_DECL.match(line) or VAL_DECL.match(line)
                if member and not declared:
                    member_name = member.group(1)
                    if member_name not in SKIP_MEMBER_NAMES:
                        self.types.setdefault(owner, set()).add(member_name)

                if declared and "(" in line and "fun" not in line:
                    # Constructor properties, including the one line forms:
                    #   enum class PowerUp(val price: Int)
                    #   data class Cell(val row: Int, val col: Int)
                    inside = line[line.index("(") + 1: line.rindex(")") if ")" in line else len(line)]
                    for parameter in re.findall(r"\b(?:val|var)\s+(\w+)\s*:", inside):
                        self.types.setdefault(owner, set()).add(parameter)

                if "enum" in self._enum_marker(owner):
                    entry = ENUM_ENTRY.match(line)
                    if entry:
                        self.enum_entries[owner].add(entry.group(1))
                        self.types.setdefault(owner, set()).add(entry.group(1))
                    if declared and "enum class" in line and "{" in line:
                        inline = line[line.index("{") + 1:
                                      line.rindex("}") if "}" in line else len(line)]
                        for inline_name in re.findall(r"\b([A-Z]\w*)\b", inline):
                            if inline_name != owner:
                                self.enum_entries[owner].add(inline_name)
                                self.types.setdefault(owner, set()).add(inline_name)

            depth += line.count("{") - line.count("}")

            # A type body can open on a later line than its declaration
            # ("enum class X(" ... ") {"), so only pop once the body was entered.
            if stack and not stack[-1][2]:
                stack[-1][3] += own_parens
                if depth > stack[-1][1]:
                    stack[-1][2] = True
                elif "{" not in line and stack[-1][3] <= 0 and not line.rstrip().endswith((":", ",")):
                    stack.pop()
            while stack and stack[-1][2] and depth <= stack[-1][1]:
                popped = stack.pop()
                if companion_owner == popped[0]:
                    companion_owner = None

    def _enum_marker(self, owner):
        match = re.search(rf"enum class {owner}\b", self.text)
        return "enum" if match else ""


def collect():
    files = []
    for root in (JAVA, TEST_JAVA):
        if not os.path.isdir(root):
            continue
        for base, _, names in os.walk(root):
            for name in sorted(names):
                if name.endswith(".kt"):
                    files.append(KotlinFile(os.path.join(base, name)))
    types = {}           # simple name -> (file, package)
    members = defaultdict(set)
    enums = defaultdict(set)
    return_types = defaultdict(set)   # function name -> declared return types
    for file in files:
        for type_name, member_names in file.types.items():
            types[type_name] = file
            members[type_name] |= member_names
        for type_name, entries in file.enum_entries.items():
            enums[type_name] |= entries
        # `fun name(...): ReturnType` inside a type body
        owner = None
        for line in file.text.split("\n"):
            declared = TYPE_DECL.match(line)
            if declared:
                owner = declared.group(2)
            match = re.search(r"fun\s+(\w+)\s*\([^)]*\)\s*:\s*([A-Za-z_][\w.<>?]*)\s*[{=]", line)
            if match:
                simple = re.sub(r"<.*", "", match.group(2)).strip().split(".")[-1]
                return_types[match.group(1)].add(simple)
    return files, types, members, enums, {
        name: next(iter(found)) for name, found in return_types.items() if len(found) == 1
    }


def split_params(text: str):
    """Splits a parameter list on commas that are not inside generics."""
    parts, depth, current = [], 0, ""
    for char in text:
        if char in "<([":
            depth += 1
        elif char in ">)]":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    parts.append(current)
    return [part for part in parts if part.strip()]


def normalise(parameter: str) -> str:
    """`val x: Float = 0f` -> `Float` so two signatures can be compared."""
    text = parameter.split("=")[0].strip()
    text = re.sub(r"^(val|var|vararg|crossinline|noinline)\s+", "", text)
    if ":" in text:
        text = text.split(":", 1)[1]
    text = text.replace("?", "")
    # `AdManager.Placement` and `Placement` are the same type.
    text = re.sub(r"\b\w+\.", "", text)
    return re.sub(r"\s+", "", text)


def main() -> int:
    files, types, members, enums, return_types = collect()
    errors = []
    notes = []

    # ---- 1. imports resolve ----
    for file in files:
        for imported in file.imports:
            if not imported.startswith(PACKAGE_ROOT):
                continue
            simple = imported.split(".")[-1]
            if simple == "*" or simple == "R":
                continue
            if simple not in types and simple not in enums:
                errors.append(f"{os.path.basename(file.path)}: import {imported} does not resolve")

    # ---- 1b. numeric literals must be lexically valid ----
    # `0x5EEDBUBBLE` is not a hex literal: A-F only. Kotlin would refuse to compile.
    numeric = re.compile(r"\b0([xXbB])([0-9A-Za-z_]+)")
    for file in files:
        for line_number, line in enumerate(file.raw.split("\n"), start=1):
            code = line.split("//")[0]
            for match in numeric.finditer(code):
                base, body = match.group(1).lower(), match.group(2)
                # Strip a legal type suffix: L, u, U, uL, UL, f/F for hex doubles.
                core = re.sub(r"(?:[uU][lL]?|[lL])$", "", body)
                allowed = "0123456789abcdefABCDEF_" if base == "x" else "01_"
                if any(char not in allowed for char in core):
                    errors.append(
                        f"{os.path.basename(file.path)}:{line_number}: invalid "
                        f"{'hex' if base == 'x' else 'binary'} literal '{match.group(0)}'"
                    )

    # ---- 1c. balanced delimiters and unique type names per package ----
    for file in files:
        text = file.text
        for open_char, close_char, label in (("{", "}", "braces"), ("(", ")", "parentheses"),
                                             ("[", "]", "brackets")):
            if text.count(open_char) != text.count(close_char):
                errors.append(
                    f"{os.path.basename(file.path)}: unbalanced {label} "
                    f"({text.count(open_char)} vs {text.count(close_char)})"
                )

    declared_in = defaultdict(list)
    for file in files:
        for type_name in file.types:
            declared_in[(file.package, type_name)].append(os.path.basename(file.path))
    for (package, type_name), where in sorted(declared_in.items()):
        if len(where) > 1:
            errors.append(f"{package}.{type_name} is declared in {', '.join(sorted(where))}")

    # ---- 2. Type.member references ----
    reference = re.compile(r"\b([A-Z]\w*)\.([A-Za-z_]\w*)\b")
    for file in files:
        for line_number, line in enumerate(file.text.split("\n"), start=1):
            if line.strip().startswith(("import ", "package ", "*")):
                continue
            for match in reference.finditer(line):
                type_name, member = match.group(1), match.group(2)
                if type_name not in types:
                    continue
                if type_name == member:
                    continue
                known = members[type_name] | enums[type_name] | BUILTINS
                if member not in known:
                    errors.append(
                        f"{os.path.basename(file.path)}:{line_number}: "
                        f"{type_name}.{member} not found on {types[type_name].package}.{type_name}"
                    )

    # ---- 3. unused imports ----
    for file in files:
        body = file.text
        for imported in file.imports:
            if not imported.startswith(PACKAGE_ROOT):
                continue
            simple = imported.split(".")[-1]
            if simple == "*":
                continue
            without_imports = re.sub(rf"^import\s+{re.escape(imported)}\s*$", "", body, flags=re.M)
            if not re.search(rf"\b{simple}\b", without_imports):
                notes.append(f"{os.path.basename(file.path)}: unused import {imported}")

    # ---- inheritance: members inherited from a project base class count too ----
    supertypes = defaultdict(set)
    for file in files:
        for match in re.finditer(r"\b(?:class|object)\s+([A-Z]\w*)[^\n(]*?(?:\([^)]*\))?\s*:\s*([^\n{]*)", file.text):
            for candidate in re.findall(r"((?:[A-Z]\w*\.)*[A-Z]\w*)", match.group(2)):
                simple = candidate.split(".")[-1]
                if simple in types and simple != match.group(1):
                    supertypes[match.group(1)].add(simple)

    def all_members(type_name, seen=None):
        """Members of a type plus everything it inherits inside this project."""
        seen = seen or set()
        if type_name in seen:
            return set()
        seen.add(type_name)
        found = set(members.get(type_name, set())) | set(enums.get(type_name, set()))
        for base in supertypes.get(type_name, ()):
            found |= all_members(base, seen)
        return found

    # ---- 3b. calls and property access on typed receivers ----
    # The type of a receiver is inferred per function scope, so a parameter called
    # `bubble` in one function can never be confused with a `bubble` of another
    # type in the next one. Names with more than one candidate type are skipped.
    param_patterns = (
        re.compile(r"\b(?:val|var)\s+([a-z]\w*)\s*:\s*([A-Z]\w*)"),
        re.compile(r"\b(?:val|var)\s+([a-z]\w*)\s*=\s*([A-Z]\w*)\s*\("),
        re.compile(r"[,\s(]([a-z]\w*)\s*:\s*([A-Z]\w*)\b"),
    )
    call = re.compile(r"\b([a-z]\w*)\.([a-zA-Z]\w*)\b(?!\s*\()")   # property access
    method_call = re.compile(r"\b([a-z]\w*)\.([a-z]\w*)\s*\(")      # method call

    # Class level properties, per declared type: `private val board: Board`.
    type_properties = defaultdict(lambda: defaultdict(set))
    for file in files:
        owner = None
        for line in file.text.split("\n"):
            declared = TYPE_DECL.match(line)
            if declared:
                owner = declared.group(2)
            if owner is None:
                continue
            # Exactly four spaces: a class body member, not a local inside a function.
            match = re.match(r"\s{4}(?!\s)(?:public |internal |private |protected )*(?:override )*"
                             r"(?:val|var)\s+([a-z]\w*)\s*(?::\s*([A-Z]\w*)|=\s*([A-Z]\w*)\s*\()", line)
            if match:
                for group in (match.group(2), match.group(3)):
                    if group:
                        type_properties[owner][match.group(1)].add(group)

    def properties_of(type_name, seen=None):
        seen = seen or set()
        if type_name in seen:
            return {}
        seen.add(type_name)
        found = {name: set(types_) for name, types_ in type_properties.get(type_name, {}).items()}
        for base in supertypes.get(type_name, ()):
            for name, candidates_ in properties_of(base, seen).items():
                found.setdefault(name, set()).update(candidates_)
        return found

    def candidates(name, scope, class_level):
        for source in (scope, class_level):
            found = source.get(name)
            if found:
                if len(found) != 1:
                    return None            # ambiguous: skip rather than guess
                return next(iter(found))
        return None

    def resolve_call_type(line):
        """`val board = LevelGenerator.build(def)` -> Generated"""
        match = re.search(r"\b(?:val|var)\s+([a-z]\w*)\s*=\s*([A-Z]\w*)\.(\w+)\s*\(", line)
        if not match:
            return None
        return return_types.get(match.group(3))

    for file in files:
        class_level = defaultdict(set)
        for type_name in file.types:
            for name, candidates_ in properties_of(type_name).items():
                class_level[name].update(candidates_)

        scope = defaultdict(set)
        for line_number, line in enumerate(file.text.split("\n"), start=1):
            if re.match(r"\s*(?:@\w+\s+)*(?:public |internal |private |protected |override |open |"
                        r"abstract |suspend |inline |operator |infix |external )*fun\s", line):
                scope = defaultdict(set)   # a new function body gets a fresh scope
            if line.strip().startswith(("import ", "package ")):
                continue
            for pattern in param_patterns:
                for match in pattern.finditer(line):
                    scope[match.group(1)].add(match.group(2))
            constructed = resolve_call_type(line)
            if constructed:
                built = re.search(r"\b(?:val|var)\s+([a-z]\w*)\s*=", line)
                if built:
                    scope[built.group(1)].add(constructed)

            # `val active = engine` inherits the type of the source variable.
            alias = re.search(r"\b(?:val|var)\s+([a-z]\w*)\s*=\s*([a-z]\w*)\s*$", line)
            if alias:
                source_type = candidates(alias.group(2), scope, class_level)
                if source_type is not None:
                    scope[alias.group(1)].add(source_type)

            for regex, is_call in ((method_call, True), (call, False)):
                for match in regex.finditer(line):
                    receiver, member = match.group(1), match.group(2)
                    type_name = candidates(receiver, scope, class_level)
                    if type_name is None or type_name not in types or type_name == member:
                        continue
                    if member in all_members(type_name) | BUILTINS:
                        continue
                    kind = "method" if is_call else "property"
                    errors.append(
                        f"{os.path.basename(file.path)}:{line_number}: "
                        f"{receiver}.{member} - no such {kind} on {type_name}"
                    )

    # ---- 3c. overrides must match the interface signature ----
    signature = re.compile(r"fun\s+(\w+)\s*\(([^)]*)\)")
    decl_line = re.compile(r"^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |open |sealed |data |enum )*"
                           r"(?:class|object)\s+([A-Z]\w*)")
    interfaces = {}
    for file in files:
        for type_name in list(file.types):
            body = file.text
            if not re.search(rf"interface\s+{type_name}\b", body):
                continue
            block = re.search(rf"interface\s+{type_name}\b([^{{]*)\{{", body)
            if not block:
                continue
            depth = 0
            start = body.index("{", block.start())
            collected = {}
            for line in body[start:].split("\n"):
                depth += line.count("{") - line.count("}")
                match = signature.search(line)
                if match and depth <= 1:
                    params = [normalise(p) for p in split_params(match.group(2))]
                    collected[match.group(1)] = params
                if depth <= 0:
                    break
            interfaces[type_name] = collected

    for file in files:
        current = None
        implemented = []
        for line_number, line in enumerate(file.text.split("\n"), start=1):
            match = decl_line.match(line)
            if match:
                current = match.group(1)
                implemented = []
                head = line
                if ":" in head:
                    for candidate in re.findall(r"[,\s:]((?:[A-Z]\w*\.)*[A-Z]\w*)", head[head.index(":"):]):
                        implemented.append(candidate.split(".")[-1])
                continue
            if current is None:
                continue
            # a class header can span several lines
            if ":" in line and not implemented:
                for candidate in re.findall(r"[,\s:]((?:[A-Z]\w*\.)*[A-Z]\w*)", line[line.index(":"):]):
                    implemented.append(candidate.split(".")[-1])
            override = re.search(r"override\s+fun\s+(\w+)\s*\(([^)]*)\)", line)
            if override:
                name = override.group(1)
                params = [normalise(p) for p in split_params(override.group(2))]
                for interface_name in implemented:
                    if interface_name not in interfaces:
                        continue
                    expected = interfaces[interface_name].get(name)
                    if expected is not None and expected != params:
                        errors.append(
                            f"{os.path.basename(file.path)}:{line_number}: {current}.{name}() parameters "
                            f"{params} do not match {interface_name}.{name}({expected})"
                        )

    # ---- 3d. an interface implementation must cover every interface member ----
    for file in files:
        text = file.text
        for class_match in re.finditer(r"(?:class|object)\s+([A-Z]\w*)[^\n]*?(:[^\n{]*)", text):
            class_name, header = class_match.group(1), class_match.group(2)
            names = [candidate.split(".")[-1]
                     for candidate in re.findall(r"[,\s:]((?:[A-Z]\w*\.)*[A-Z]\w*)", header)]
            body_start = text.find("{", class_match.end() - len(header))
            if body_start < 0:
                continue
            depth, block = 0, []
            for line in text[body_start:].split("\n"):
                depth += line.count("{") - line.count("}")
                block.append(line)
                if depth <= 0:
                    break
            overridden = set(re.findall(r"override\s+fun\s+(\w+)", "\n".join(block)))
            for interface_name in names:
                if interface_name not in interfaces:
                    continue
                missing = sorted(set(interfaces[interface_name]) - overridden)
                if missing:
                    notes.append(
                        f"{os.path.basename(file.path)}: {class_name} implements {interface_name} "
                        f"but the body does not override {', '.join(missing)} "
                        f"(inherited from a base class?)"
                    )

    # ---- 4. custom views in layouts ----
    for name in sorted(os.listdir(LAYOUTS)):
        with open(os.path.join(LAYOUTS, name), encoding="utf-8") as handle:
            content = handle.read()
        for match in re.finditer(r"<(" + PACKAGE_ROOT + r"[\w.]+)", content):
            simple = match.group(1).split(".")[-1]
            if simple not in types:
                errors.append(f"layout/{name}: custom view {match.group(1)} is not a Kotlin class")

    print(f"files: {len(files)}  types: {len(types)}  enum sets: {len(enums)}")
    if errors:
        print(f"\nUNRESOLVED SYMBOLS ({len(errors)}):")
        for error in errors:
            print("  " + error)
    if notes:
        print(f"\nnotes ({len(notes)}):")
        for note in notes:
            print("  " + note)
    if errors:
        return 1
    print("\nALL SYMBOLS RESOLVE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
