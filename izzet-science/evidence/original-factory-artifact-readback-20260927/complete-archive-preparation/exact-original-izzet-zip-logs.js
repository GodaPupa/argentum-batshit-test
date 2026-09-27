// Exact-original, data-only ZIP log extraction. Requires reviewed SHA helpers200cb09b.
// No filesystem, module, eval, network, process, or execution of archive contents.
function crc32Hex(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length > 8388608) throw new Error("CRC input limit");
  var crc = 0xffffffff;
  for (var i = 0; i < bytes.length; i++) {
    crc ^= bytes[i];
    for (var bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return ((crc ^ 0xffffffff) >>> 0).toString(16).padStart(8, "0");
}
function parseBoundedZipIndex(zip, expected) {
  if (!(zip instanceof Uint8Array) || zip.length > 1945147 || !Array.isArray(expected) || expected.length !== 63)
    throw new Error("ZIP index input bounds");
  function range(p, n) {
    if (!Number.isSafeInteger(p) || !Number.isSafeInteger(n) || p < 0 || n < 0 || p + n > zip.length)
      throw new Error("ZIP range overflow");
  }
  function u16(p) { range(p, 2); return zip[p] | (zip[p + 1] << 8); }
  function u32(p) { range(p, 4); return (zip[p] | (zip[p + 1] << 8) | (zip[p + 2] << 16) | (zip[p + 3] << 24)) >>> 0; }
  function name(p, n) {
    range(p, n); if (n < 1 || n > 4096) throw new Error("ZIP name length");
    var out = "";
    for (var i = 0; i < n; i++) {
      if (zip[p + i] < 32 || zip[p + i] > 126) throw new Error("Only exact ASCII entry names are admitted");
      out += String.fromCharCode(zip[p + i]);
    }
    if (out.startsWith("/") || out.includes("\\") || out.includes(":") ||
        out.split("/").some(function(s) { return s === "" || s === "." || s === ".."; }))
      throw new Error("Unsafe or unsupported ZIP path");
    return out;
  }
  function extras(p, n) {
    range(p, n); var end = p + n;
    while (p < end) {
      if (p + 4 > end) throw new Error("Truncated ZIP extra field");
      var id = u16(p), len = u16(p + 2); p += 4;
      if (id === 1 || id === 0x9901) throw new Error("ZIP64/AES extra not admitted");
      if (p + len > end) throw new Error("ZIP extra range overflow");
      p += len;
    }
  }
  var end = -1;
  for (var p = zip.length - 22; p >= Math.max(0, zip.length - 65557); p--) {
    if (u32(p) === 0x06054b50 && p + 22 + u16(p + 20) === zip.length) {
      if (end !== -1) throw new Error("Ambiguous ZIP end record");
      end = p;
    }
  }
  if (end < 0 || u16(end + 4) !== 0 || u16(end + 6) !== 0 ||
      u16(end + 8) !== 63 || u16(end + 10) !== 63) throw new Error("ZIP end/count/multidisk mismatch");
  var cdSize = u32(end + 12), cdStart = u32(end + 16);
  if (cdStart + cdSize !== end || cdStart === 0xffffffff || cdSize === 0xffffffff)
    throw new Error("ZIP central directory bounds/ZIP64");
  range(cdStart, cdSize);
  var trusted = new Map();
  expected.forEach(function(row) {
    if (!row || typeof row.path !== "string" || trusted.has(row.path) || row.is_directory !== false || typeof row.raw_text_relayed !== "boolean" ||
        !Number.isSafeInteger(row.bytes) || row.bytes < 0 || row.bytes > 8388608 ||
        !Number.isSafeInteger(row.compressed_bytes) || row.compressed_bytes < 0 ||
        row.compressed_bytes > zip.length || !/^[0-9a-f]{8}$/.test(row.crc32) ||
        !/^[0-9a-f]{64}$/.test(row.sha256)) throw new Error("Invalid trusted member metadata");
    trusted.set(row.path, row);
  });
  var entries = [], seen = new Set(); p = cdStart;
  for (var entry = 0; entry < 63; entry++) {
    if (p + 46 > end || u32(p) !== 0x02014b50) throw new Error("ZIP central header mismatch");
    var needed = u16(p + 6), flags = u16(p + 8), method = u16(p + 10);
    var crc = u32(p + 16), compressed = u32(p + 20), bytes = u32(p + 24);
    var nameLen = u16(p + 28), extraLen = u16(p + 30), commentLen = u16(p + 32);
    var local = u32(p + 42), next = p + 46 + nameLen + extraLen + commentLen;
    if (next > end || needed > 20 || (flags & ~0x080e) !== 0 || (method !== 0 && method !== 8) ||
        (method === 0 && (flags & 6) !== 0) || u16(p + 34) !== 0 ||
        compressed === 0xffffffff || bytes === 0xffffffff || local === 0xffffffff)
      throw new Error("Unsupported ZIP feature/encryption/ZIP64");
    var path = name(p + 46, nameLen), row = trusted.get(path);
    extras(p + 46 + nameLen, extraLen);
    if (!row || seen.has(path) || row.bytes !== bytes || row.compressed_bytes !== compressed ||
        row.crc32 !== crc.toString(16).padStart(8, "0")) throw new Error("ZIP trusted member identity mismatch");
    seen.add(path);
    if (local + 30 > cdStart || u32(local) !== 0x04034b50 ||
        u16(local + 4) !== needed || u16(local + 6) !== flags || u16(local + 8) !== method)
      throw new Error("ZIP local header mismatch");
    var localName = u16(local + 26), localExtra = u16(local + 28);
    var dataStart = local + 30 + localName + localExtra, dataEnd = dataStart + compressed;
    if (dataStart > cdStart || dataEnd > cdStart || name(local + 30, localName) !== path)
      throw new Error("ZIP local name/data bounds");
    extras(local + 30 + localName, localExtra);
    var lcrc = u32(local + 14), lcompressed = u32(local + 18), lbytes = u32(local + 22), recordEnd = dataEnd;
    if ((flags & 8) !== 0) {
      if ((lcrc !== 0 && lcrc !== crc) || (lcompressed !== 0 && lcompressed !== compressed) ||
          (lbytes !== 0 && lbytes !== bytes)) throw new Error("ZIP descriptor local values mismatch");
      var q = dataEnd;
      if (u32(q) === 0x08074b50) q += 4;
      if (q + 12 > cdStart || u32(q) !== crc || u32(q + 4) !== compressed || u32(q + 8) !== bytes)
        throw new Error("ZIP data descriptor mismatch");
      recordEnd = q + 12;
    } else if (lcrc !== crc || lcompressed !== compressed || lbytes !== bytes) {
      throw new Error("ZIP local sizes/CRC mismatch");
    }
    if (method === 0 && compressed !== bytes) throw new Error("Stored ZIP size mismatch");
    entries.push({path:path, method:method, flags:flags, bytes:bytes, compressed_bytes:compressed,
      crc32:row.crc32, sha256:row.sha256, raw_text_relayed:row.raw_text_relayed, local_start:local, data_start:dataStart, data_end:dataEnd, record_end:recordEnd});
    p = next;
  }
  if (p !== end || seen.size !== 63) throw new Error("ZIP central directory trailing/count mismatch");
  var sorted = entries.slice().sort(function(a,b) { return a.local_start - b.local_start; }), offset = 0;
  sorted.forEach(function(e) {
    if (e.local_start !== offset || e.record_end <= e.local_start) throw new Error("ZIP local overlap/gap");
    offset = e.record_end;
  });
  if (offset !== cdStart) throw new Error("ZIP local/central boundary mismatch");
  return entries;
}
function extractOriginalIzzetLogs(zip, manifestText, hashes) {
  var report = {status:"INCOMPLETE", whole_zip_sha256:null, manifest_sha256:null,
    central_members:0, selected_members:[], verified_output_bytes:0, returned_log_bytes:0, verified_package_members:0, errors:[]};
  var output = [];
  try {
    if (!(zip instanceof Uint8Array) || zip.length !== 1945147) throw new Error("Wrong original ZIP byte count");
    if (!hashes || typeof hashes.sha256Bytes !== "function" || typeof hashes.sha256Utf8 !== "function")
      throw new Error("Reviewed SHA helper required");
    report.whole_zip_sha256 = hashes.sha256Bytes(zip);
    if (report.whole_zip_sha256 !== "a8ea41fbe787b944d584fca6a33b78a6d88a01244fb1487c9f7d6f2224d2d1b5")
      throw new Error("Wrong original ZIP SHA256");
    if (typeof manifestText !== "string" || manifestText.length > 65536) throw new Error("Manifest text bound");
    report.manifest_sha256 = hashes.sha256Utf8(manifestText);
    if (report.manifest_sha256 !== "a13b89824b25598d250fd8fd811e8cddef917cd15cf1fc07e3bb8c18c497b97f")
      throw new Error("Wrong reviewed manifest SHA256");
    var manifest = JSON.parse(manifestText), entries = parseBoundedZipIndex(zip, manifest);
    report.central_members = entries.length;
    var selected = entries.filter(function(e) { return e.raw_text_relayed === false; }), total = 0, logTotal = 0, logCount = 0;
    var packageNames = new Set([
      "build-semaphore/packages/inn2_2.7.2~20240212-1build3_amd64.deb",
      "build-semaphore/packages/inn2-inews_2.7.2~20240212-1build3_amd64.deb"
    ]);
    if (selected.length !== 19) throw new Error("Exact omitted-member count mismatch");
    selected.forEach(function(e) {
      total += e.bytes;
      if (e.bytes > 8388608) throw new Error("Per-member size limit");
      if (e.path.endsWith(".log")) { logTotal += e.bytes; logCount++; }
      else if (!packageNames.has(e.path)) throw new Error("Unexpected omitted non-log member");
    });
    if (total !== 8548021 || total > 16777216 || logCount !== 17 || logTotal !== 7145607)
      throw new Error("Aggregate omitted-member size/count identity");
    for (var i = 0; i < selected.length; i++) {
      var e = selected[i], row = {path:e.path, bytes:e.bytes, compressed_bytes:e.compressed_bytes,
        status:"INCOMPLETE", expected_crc32:e.crc32, expected_sha256:e.sha256};
      report.selected_members.push(row);
      var compressed = zip.subarray(e.data_start, e.data_end);
      var decoded = e.method === 0 ? new Uint8Array(compressed) : inflateRawBounded(compressed, e.bytes);
      row.actual_bytes = decoded.length; row.actual_crc32 = crc32Hex(decoded); row.actual_sha256 = hashes.sha256Bytes(decoded);
      if (decoded.length !== e.bytes || row.actual_crc32 !== e.crc32 || row.actual_sha256 !== e.sha256)
        throw new Error("Decoded member identity mismatch: " + e.path);
      row.status = "VERIFIED_EXACT_RAW_BYTES"; report.verified_output_bytes += decoded.length;
      if (e.path.endsWith(".log")) { output.push({path:e.path, bytes:decoded}); report.returned_log_bytes += decoded.length; }
      else { row.content_use = "HASH_ONLY_NO_PACKAGE_INTERPRETATION"; report.verified_package_members++; }
    }
    if (output.length !== 17 || report.verified_package_members !== 2 || report.verified_output_bytes !== 8548021 ||
        report.returned_log_bytes !== 7145607) throw new Error("Final exact output accounting mismatch");
    report.status = "COMPLETE_EXACT_19_MEMBERS_17_RAW_LOGS_REQUIRES_INDEPENDENT_AUDIT";
    return {report:report, logs:output};
  } catch (error) {
    report.errors.push(error.name + ": " + error.message); report.status = "FAILED_NO_LOGS_ADMITTED";
    return {report:report, logs:[]};
  }
}
