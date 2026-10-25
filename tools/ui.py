#!/usr/bin/env python3
"""Find a view by text/description in a uiautomator dump and print the tap point."""
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
want = sys.argv[2]
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    t = re.search(r' text="([^"]*)"', node); d = re.search(r' content-desc="([^"]*)"', node)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
