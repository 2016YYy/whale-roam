
import xml.etree.ElementTree as ET
files = [
 r"E:\AIDame\DeepSeekHarnessGithub\whale-roam\app\src\main\res\values\strings_disclaimer.xml",
 r"E:\AIDame\DeepSeekHarnessGithub\whale-roam\app\src\main\res\layout\dialog_disclaimer.xml",
]
ok = True
for f in files:
    try:
        t = ET.parse(f)
        print("OK   ", f, "->", t.getroot().tag, "children:", len(list(t.getroot())))
    except Exception as e:
        ok = False
        print("FAIL ", f, "->", e)
print("ALL_VALID" if ok else "HAS_ERRORS")
