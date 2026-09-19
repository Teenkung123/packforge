"""Exact FO runtime-metadata output normalization; never rewrites input files."""
import copy
import difflib
import hashlib
import json
from pathlib import Path
import re

POLICY = 'fo-runtime-metadata-v1'
RULES = {
    'config/iris.properties':'java-properties-date-comment',
    'shaderpacks/ComplementaryUnbound_r5.7.1.zip.txt':'java-properties-date-comment',
    'config/sodium-fingerprint.json':'sodium-installation-fingerprint-v1',
    'config/sodium-options.json':'sodium-donation-prompt-state',
}
DATE = re.compile(rb'#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) '
                  rb'[ 0-9][0-9]? [0-9]{2}:[0-9]{2}:[0-9]{2} [A-Za-z][A-Za-z0-9:+_-]{0,20} [0-9]{4}(?:\r?\n)?\Z')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def parse_json(data):
    def pairs(items):
        value = {}
        for key,item in items:
            if key in value: raise ValueError('Duplicate runtime metadata JSON key')
            value[key] = item
        return value
    return json.loads(data.decode('utf-8-sig'), object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Invalid runtime metadata number')))


def canonical(relative, data):
    if relative not in RULES: raise ValueError('Unreviewed runtime normalization path')
    if len(data) > 1024 * 1024: raise ValueError('Runtime metadata exceeds reviewed file size')
    rule = RULES[relative]
    if rule == 'java-properties-date-comment':
        lines = data.splitlines(keepends=True)
        indexes = [i for i,line in enumerate(lines) if DATE.fullmatch(line)]
        if len(indexes) > 1 or any(i > 1 or any(not line.startswith(b'#') for line in lines[:i]) for i in indexes):
            raise ValueError('Unreviewed Java Properties date position')
        return b''.join(line for i,line in enumerate(lines) if i not in indexes)
    value = parse_json(data)
    if rule == 'sodium-installation-fingerprint-v1':
        if (not isinstance(value, dict) or set(value) != {'v','s','u','p','t'}
                or type(value['v']) is not int or value['v'] != 1
                or type(value['t']) is not int or value['t'] <= 0
                or any(not isinstance(value[k], str) or not re.fullmatch('[a-f0-9]{128}', value[k]) for k in ('s','u','p'))):
            raise ValueError('Unreviewed Sodium fingerprint schema')
        value = {'v':1}
    else:
        if (not isinstance(value, dict) or not isinstance(value.get('notifications'), dict)
                or type(value['notifications'].get('has_seen_donation_prompt')) is not bool):
            raise ValueError('Unreviewed Sodium notification schema')
        value = copy.deepcopy(value)
        del value['notifications']['has_seen_donation_prompt']
    return json.dumps(value, sort_keys=True, separators=(',',':'), allow_nan=False).encode('utf-8')


def capture(game, controlled, policy):
    if policy is None: return {}
    if policy != POLICY: raise ValueError('Unknown runtime normalization policy')
    game = Path(game).resolve()
    snapshots = {}
    for path in controlled:
        path = Path(path).resolve()
        relative = path.relative_to(game).as_posix()
        if relative in RULES:
            raw = path.read_bytes()
            snapshots[relative] = {'path':str(path), 'raw':raw, 'canonicalSha256':sha256(canonical(relative,raw))}
    if set(snapshots) != set(RULES):
        raise ValueError('Reviewed FO normalization requires all four pinned metadata files')
    return snapshots


def audit(snapshots):
    """Report all raw diffs and permit only canonically identical reviewed output."""
    records, accepted_changes = [], set()
    fingerprint_changed = False
    fingerprint = snapshots.get('config/sodium-fingerprint.json')
    if fingerprint and Path(fingerprint['path']).is_file():
        fingerprint_changed = fingerprint['raw'] != Path(fingerprint['path']).read_bytes()
    for relative,before in snapshots.items():
        record = {'id':relative, 'rule':RULES[relative], 'beforeSha256':sha256(before['raw']),
                  'canonicalBeforeSha256':before['canonicalSha256'], 'accepted':False}
        records.append(record)
        try:
            after = Path(before['path']).read_bytes()
            record.update(afterSha256=sha256(after), rawChanged=before['raw'] != after,
                          canonicalAfterSha256=sha256(canonical(relative,after)),
                          rawDiff=''.join(difflib.unified_diff(before['raw'].decode('utf-8-sig').splitlines(keepends=True),
                                                            after.decode('utf-8-sig').splitlines(keepends=True),
                                                            fromfile=relative+' (frozen input)', tofile=relative+' (runtime output)')))
            prompt_reset = True
            if relative == 'config/sodium-options.json':
                old = parse_json(before['raw'])['notifications']['has_seen_donation_prompt']
                new = parse_json(after)['notifications']['has_seen_donation_prompt']
                prompt_reset = old == new or (old is True and new is False and fingerprint_changed)
                record['notificationPromptChange'] = {'before':old,'after':new,'fingerprintChanged':fingerprint_changed}
            record['accepted'] = record['canonicalBeforeSha256'] == record['canonicalAfterSha256'] and prompt_reset
            if record['accepted'] and record['rawChanged']: accepted_changes.add(before['path'])
        except (ValueError, OSError, UnicodeError) as error:
            record['error'] = type(error).__name__ + ': ' + str(error)
    return records,accepted_changes
