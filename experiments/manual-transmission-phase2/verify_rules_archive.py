#!/usr/bin/env python3
"""Archive pinned official rules bytes. This never admits a pilot or starts a game."""
from __future__ import annotations

import argparse
from calendar import month_name
from datetime import date, datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

MANIFEST = Path(__file__).with_name('rules-source-r2.json')


def verify(raw: bytes, manifest: dict, as_of: date) -> dict:
    effective = date.fromisoformat(manifest['effective_date'])
    expected_header = f'These rules are effective as of {month_name[effective.month]} {effective.day}, {effective.year}.'
    if manifest['effective_header'] != expected_header:
        raise ValueError('Effective-date header mismatch within manifest')
    if effective > as_of:
        raise ValueError('Rules release is future-effective for the admission date')
    if len(raw) != manifest['byte_length']:
        raise ValueError('Rules byte length mismatch')
    digest = hashlib.sha256(raw).hexdigest()
    if digest != manifest['sha256']:
        raise ValueError('Rules byte digest mismatch')
    header = raw.decode('utf-8-sig').splitlines()[:10]
    if manifest['effective_header'] not in header:
        raise ValueError('Effective-date header mismatch')
    if manifest['engine_semantics_qualified'] or manifest['official_gameplay_authorized']:
        raise ValueError('Source-only manifest cannot assert execution qualification')
    return {
        'schema': 'mt-phase2-rules-archive-audit-v1',
        'status': 'EXACT_OFFICIAL_SOURCE_ARCHIVED_NOT_ENGINE_QUALIFICATION',
        'ruleset_id': manifest['ruleset_id'],
        'effective_date': effective.isoformat(),
        'admission_date': as_of.isoformat(),
        'source_url': manifest['source_url'],
        'sha256': digest,
        'byte_length': len(raw),
        'engine_semantics_qualified': False,
        'execution_allowed': False,
        'seeds_generated': 0,
        'games_initialized': 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--as-of', type=date.fromisoformat, required=True)
    parser.add_argument('--archive', type=Path, help='Verify already-downloaded bytes offline')
    args = parser.parse_args()
    if args.as_of > datetime.now(timezone.utc).date():
        raise ValueError('Admission date must not be in the future')
    manifest_bytes = MANIFEST.read_bytes()
    manifest = json.loads(manifest_bytes)
    url = urlparse(manifest['source_url'])
    if url.scheme != 'https' or url.hostname != 'media.wizards.com':
        raise ValueError('Rules must originate at the pinned official HTTPS source')
    if args.archive:
        raw = args.archive.read_bytes()
    else:
        request = Request(manifest['source_url'], headers={'User-Agent': 'ArgentumResearch/1.0'})
        with urlopen(request, timeout=45) as response:
            raw = response.read(manifest['byte_length'] + 1)
    audit = verify(raw, manifest, args.as_of)
    audit['source_manifest_sha256'] = hashlib.sha256(manifest_bytes).hexdigest()
    filename = manifest['archive_filename']
    if Path(filename).name != filename:
        raise ValueError('Invalid archive filename')
    args.out.mkdir(parents=True, exist_ok=True)
    destination = args.out / filename
    if destination.exists() and destination.read_bytes() != raw:
        raise ValueError('Refusing to replace different archived rules bytes')
    destination.write_bytes(raw)
    (args.out / 'rules-source.json').write_bytes(manifest_bytes)
    (args.out / 'rules-audit.json').write_text(json.dumps(audit, indent=2, sort_keys=True) + '\n')
    print(json.dumps(audit, sort_keys=True))


if __name__ == '__main__':
    main()
