"""Local structural/preservation checks for the October 1 documentation refresh."""
from pathlib import Path
from html.parser import HTMLParser
import hashlib
import json
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
BASE = HERE / 'baseline'
checks = []


def check(name, passed, details=None):
    checks.append({'check': name, 'status': 'PASS' if passed else 'FAIL',
                   **({'details': details} if details is not None else {})})


def caps(text):
    return [(int(n), body) for n, body in re.findall(
        r'^- \*\*CAP-(\d+) — (.*?)(?=\n\n- \*\*CAP-|\n\n## |\Z)', text, re.M | re.S)]


spec = (ROOT / 'SPEC.md').read_text()
old_caps = dict(caps((BASE / 'SPEC.md').read_text()))
new_caps = caps(spec)
check('Stable unique capability IDs 1–26', [n for n, _ in new_caps] == list(range(1, 27)))
check('Each capability has intent and success', all(
    body.count('**intent:**') == body.count('**success:**') == 1 for _, body in new_caps))
changed = [n for n, body in new_caps if n in old_caps and body != old_caps[n]]
check('Only accepted existing capabilities amended', changed == [2, 3, 4, 6, 11, 12, 14, 17, 21, 22], changed)
for n in (21, 22):
    check(f'CAP-{n} complete prior contract preserved', dict(new_caps)[n].startswith(old_caps[n]))
check('Five kernel fields and open questions', re.findall(r'^## (.+)$', spec, re.M) ==
      ['Why', 'Capabilities', 'Constraints', 'Non-goals', 'Success signal', 'Open questions'])
check('No diagrams in kernel', not re.search(r'```|!\[', spec))
check('Current question IDs; retired IDs not reused', re.findall(r'^- \*\*OQ-(\d+)', spec, re.M) ==
      ['2', '4', '5', '6', '7', '8'])
header = spec.split('---', 2)[1]
companions = re.findall(r'^  - (.+)$', header, re.M)
check('All 14 mandatory companions exist', len(companions) == 14 and all(
    (ROOT / name).is_file() for name in companions), companions)
check('Companion list excludes process/wrapper files', all(
    name.endswith('.md') and Path(name).name not in ['.memlog.md', 'README.md'] for name in companions)
    and 'sources: []' in header)
mapping = (ROOT / 'contract-map.md').read_text()
check('All capabilities mapped once', [int(n) for n in re.findall(r'^\| CAP-(\d+) \|', mapping, re.M)] == list(range(1, 27)))
check('All eight NFRs retained', re.findall(r'^\| NFR-(\d+) ', mapping, re.M) == list('12345678'))
ux = (ROOT / 'ux-constraints.md').read_text()
check('UX IDs 1–14 preserved, 15–21 added', [int(n) for n in re.findall(r'^- \*\*UX-(\d+)', ux, re.M)] == list(range(1, 22)))
old_log = (BASE / '.memlog.md').read_text().split('---', 2)[2]
new_log = (ROOT / '.memlog.md').read_text().split('---', 2)[2]
check('Journal body is append-only; helper timestamp excluded', new_log.startswith(old_log))
broken = []
for name in ['SPEC.md', 'contract-map.md', 'ux-constraints.md']:
    text = (ROOT / name).read_text()
    for target in re.findall(r'\]\(([^)]+)\)', text):
        path = target.split('#', 1)[0]
        if path and '://' not in path and not (ROOT / path).exists():
            broken.append(f'{name}: {target}')
    check(f'{name}: no trailing whitespace', all(line == line.rstrip() for line in text.splitlines()))
check('Local contract links resolve', not broken, broken)


class ReviewHTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids, self.anchors, self.cards, self.pres = [], [], [], []
        self.in_pre = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.append(attrs['id'])
        if attrs.get('href', '').startswith('#'):
            self.anchors.append(attrs['href'][1:])
        if 'data-cap' in attrs:
            self.cards.append(attrs['data-cap'])
        if tag == 'pre':
            self.in_pre = True
            self.pres.append('')

    def handle_endtag(self, tag):
        if tag == 'pre':
            self.in_pre = False

    def handle_data(self, data):
        if self.in_pre:
            self.pres[-1] += data


page = ReviewHTML()
page.feed((ROOT / 'SPEC-REVIEW.html').read_text())
check('HTML unique anchors resolve', len(page.ids) == len(set(page.ids)) and all(a in page.ids for a in page.anchors))
check('HTML has 26 capability cards', len(page.cards) == 26)
check('HTML embeds current canonical SPEC exactly', any(p.strip('\n') == spec.strip('\n') for p in page.pres))
check('No unrequested story breakdown', not (ROOT / 'stories.yaml').exists())
files = ['SPEC.md', 'contract-map.md', 'ux-constraints.md', 'SPEC-REVIEW.html']
report = {'date': '2026-10-01', 'scope': 'documentation only',
          'status': 'PASS' if all(c['status'] == 'PASS' for c in checks) else 'FAIL',
          'checks': checks,
          'sha256': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in files},
          'application_runtime_checks': 'NOT RUN — no application code changed in this refresh'}
(HERE / 'document-checks.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(report, indent=2, ensure_ascii=False))
raise SystemExit(report['status'] != 'PASS')
