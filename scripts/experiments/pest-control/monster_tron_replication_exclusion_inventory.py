"""Pure excluded-catalogue correspondence; no actual inventory or authority."""
import hashlib
import json
import re

REALM = "EXCLUDED_PEST_INVENTORY_V1"
ACK = "CHECK_EXCLUDED_INVENTORY_ONLY"
STATUSES = frozenset({"RETIRED", "RESERVED", "QUARANTINED", "ACCEPTED", "REJECTED", "FIXTURE"})
MAX_BYTES = 262144
MAX_ITEMS = 256


class Refused(ValueError):
    pass


def require(condition, reason):
    if not condition:
        raise Refused(reason)


def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True,
                       allow_nan=False) + "\n").encode("ascii")


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def _label(value):
    return type(value) is str and re.fullmatch(r"EXCLUDED_[A-Z0-9_]{1,64}", value) is not None


def _hash(value):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def _object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "duplicate JSON key")
        result[key] = value
    return result


def _parse(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, "document size/type")
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=_object)
        require(type(value) is dict and canonical(value) == raw, "noncanonical document")
        return value
    except (UnicodeError, ValueError, TypeError, RecursionError) as error:
        raise Refused("invalid document") from error


def _fields(value, fields):
    require(type(value) is dict and set(value) == set(fields), "exact fields required")


def _list(value):
    require(type(value) is list and 0 < len(value) <= MAX_ITEMS, "nonempty bounded list")


def verify_inventory(catalogue_raw, catalogue_sha256, source_pairs, ack):
    """Return canonical immutable bytes; all inputs supplied in memory.

    Caller-selected pins are comparison values, not authenticated credentials.
    No filesystem, network, registry, entropy, engine or official-loader path.
    """
    require(ack == ACK, "excluded ACK")
    require(_hash(catalogue_sha256), "external catalogue pin")
    require(type(catalogue_raw) is bytes and digest(catalogue_raw) == catalogue_sha256,
            "catalogue digest drift")
    catalogue = _parse(catalogue_raw)
    _fields(catalogue, {"realm", "sources", "official_bindings"})
    require(catalogue["realm"] == REALM and catalogue["official_bindings"] is None,
            "excluded realm/null official bindings")
    _list(catalogue["sources"])
    expected = {}
    for source in catalogue["sources"]:
        _fields(source, {"source_id", "sha256", "members"})
        sid = source["source_id"]
        require(_label(sid) and sid not in expected and _hash(source["sha256"]),
                "source identity/digest")
        _list(source["members"])
        members = {}
        for member in source["members"]:
            _fields(member, {"member_id", "sha256"})
            mid = member["member_id"]
            require(_label(mid) and mid not in members and _hash(member["sha256"]),
                    "member identity/digest")
            members[mid] = member["sha256"]
        expected[sid] = (source["sha256"], members)
    require(type(source_pairs) in (list, tuple) and len(source_pairs) == len(expected),
            "source coverage")
    supplied = {}
    for pair in source_pairs:
        require(type(pair) in (list, tuple) and len(pair) == 2, "source pair")
        sid, raw = pair
        require(_label(sid) and sid in expected and sid not in supplied, "source coverage/duplicate")
        require(type(raw) is bytes and 0 < len(raw) <= MAX_BYTES, "source bytes")
        supplied[sid] = raw
    require(set(supplied) == set(expected), "exact source coverage")
    require(sum(map(len, supplied.values())) <= 1048576, "aggregate size")
    union = {}
    source_pins = []
    for sid in sorted(expected):
        source_hash, members = expected[sid]
        require(digest(supplied[sid]) == source_hash, "source digest drift")
        source = _parse(supplied[sid])
        _fields(source, {"realm", "source_id", "members"})
        require(source["realm"] == REALM and source["source_id"] == sid, "source binding")
        _list(source["members"])
        seen_members, seen_rows = set(), set()
        for member in source["members"]:
            _fields(member, {"member_id", "rows"})
            mid = member["member_id"]
            require(_label(mid) and mid in members and mid not in seen_members, "member coverage")
            require(digest(canonical(member)) == members[mid], "member digest drift")
            seen_members.add(mid)
            _list(member["rows"])
            for row in member["rows"]:
                _fields(row, {"identity", "status", "reservation"})
                identity, status, reservation = row["identity"], row["status"], row["reservation"]
                require(_label(identity) and type(status) is str and status in STATUSES,
                        "excluded identity/status")
                require((_label(reservation) if status == "RESERVED" else reservation is None),
                        "reservation binding")
                # Identical rows repeated within one source violate its inventory,
                # even when placed in different members. Across sources they are retained.
                key = (identity, status, reservation)
                require(key not in seen_rows, "duplicate row within source")
                seen_rows.add(key)
                union.setdefault(identity, []).append(dict(source_id=sid, member_id=mid,
                    status=status, reservation=reservation))
        require(seen_members == set(members), "missing member")
        source_pins.append(dict(source_id=sid, sha256=source_hash,
            members=[dict(member_id=m, sha256=members[m]) for m in sorted(members)]))
    entries = []
    for identity in sorted(union):
        provenance = union[identity]
        reservations = {p["reservation"] for p in provenance if p["status"] == "RESERVED"}
        require(not reservations or (len(reservations) == 1 and
                all(p["status"] == "RESERVED" for p in provenance)), "conflicting reservations")
        entries.append(dict(identity=identity, provenance=sorted(provenance,
            key=lambda p: (p["source_id"], p["member_id"], p["status"], p["reservation"] or ""))))
    return canonical(dict(realm=REALM, catalogue_sha256=catalogue_sha256,
        sources=source_pins, entries=entries,
        completeness="RELATIVE_TO_SUPPLIED_CATALOGUE_ONLY",
        official_bindings=None, live_inventory_complete=False, draw_eligible=False,
        provenance_authenticated=False, historical_execution_proven=False,
        execution_authorized=False))
