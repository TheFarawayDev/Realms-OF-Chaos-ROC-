"""Looks through the Java sources for translation keys and reports any the language file does not have.

It finds whole keys written out, and keys built from a prefix constant and a literal ending. Keys built from an
enum's name at run time are checked by the self-test instead, which opens the menus and reads what is on them.
"""
import json, re, glob, os
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..') + os.sep
lang = json.load(open(R + 'common/src/main/resources/assets/realms_of_chaos/lang/en_us.json'))
MOD = 'realms_of_chaos'
missing = {}
for f in glob.glob(R + 'common/src/**/*.java', recursive=True):
    if f.endswith('SelfTest.java'):
        continue
    s = open(f).read()
    s = s.replace('" + RealmsOfChaos.MOD_ID + "', MOD).replace('" + com.thefarawaydev.realmsofchaos.RealmsOfChaos.MOD_ID + "', MOD)
    s = re.sub(r'"\s*\+\s*MOD_ID\s*\+\s*"', MOD, s)
    consts = {}
    for name, value in re.findall(r'String (\w+) = "([a-z_.0-9]*' + MOD + r'[a-z_.0-9]*)";', s):
        consts.setdefault(name, []).append(value)      # (a name may be used for different prefixes in different methods)
    keys = set()
    # whole literals
    for m in re.finditer(r'"((?:[a-z_0-9]+\.)+' + MOD + r'(?:\.[a-z_0-9]+)+)"', s):
        tail = s[m.end():m.end() + 4]
        if not tail.lstrip().startswith('+'):
            keys.add(m.group(1))
    # CONST + "suffix"
    for k in keys:
        if k not in lang and not k.endswith('.'):
            missing.setdefault(os.path.basename(f), []).append(k)
    for name, values in consts.items():
        for m in re.finditer(r'\b' + name + r' \+ "([a-z_0-9.]+)"', s):
            tail = s[m.end():m.end() + 4]
            if not tail.lstrip().startswith('+') and not any(v + m.group(1) in lang for v in values):
                missing.setdefault(os.path.basename(f), []).append(values[0] + m.group(1))
for f, ks in sorted(missing.items()):
    print(f, sorted(ks))
print('files with missing keys:', len(missing))
raise SystemExit(1 if missing else 0)
