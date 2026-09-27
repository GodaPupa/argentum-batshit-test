// Guarded adaptation of tiny-inflate1.0.3 by Devon Govett, MIT; see retained upstream LICENSE.
// Original immutable source: foliojs/tiny-inflate@f4999b9c77263a97eb3a6942be247ece85a3d457/index.js.
// This adaptation changes bit reads, Huffman validation, bounds and exact-length return.
// All upstream tables/scratch state are private to this call; no module/global/I/O access.
function inflateRawBounded(source, expectedBytes) {
  if (!(source instanceof Uint8Array) || source.length > 1945147) throw new Error("Invalid compressed byte input");
  if (!Number.isSafeInteger(expectedBytes) || expectedBytes < 0 || expectedBytes > 8388608) throw new Error("Invalid destination byte count");
var TINF_OK = 0;
var TINF_DATA_ERROR = -3;

function Tree() {
  this.table = new Uint16Array(16);   /* table of code length counts */
  this.trans = new Uint16Array(288);  /* code -> symbol translation table */
}

function Data(source, dest) {
  this.source = source;
  this.sourceIndex = 0;
  this.tag = 0;
  this.bitcount = 0;
  
  this.dest = dest;
  this.destLen = 0;
  
  this.ltree = new Tree();  /* dynamic length/symbol tree */
  this.dtree = new Tree();  /* dynamic distance tree */
}

/* --------------------------------------------------- *
 * -- uninitialized global data (static structures) -- *
 * --------------------------------------------------- */

var sltree = new Tree();
var sdtree = new Tree();

/* extra bits and base tables for length codes */
var length_bits = new Uint8Array(30);
var length_base = new Uint16Array(30);

/* extra bits and base tables for distance codes */
var dist_bits = new Uint8Array(30);
var dist_base = new Uint16Array(30);

/* special ordering of code length codes */
var clcidx = new Uint8Array([
  16, 17, 18, 0, 8, 7, 9, 6,
  10, 5, 11, 4, 12, 3, 13, 2,
  14, 1, 15
]);

/* used by tinf_decode_trees, avoids allocations every call */
var code_tree = new Tree();
var lengths = new Uint8Array(288 + 32);

/* ----------------------- *
 * -- utility functions -- *
 * ----------------------- */

/* build extra bits and base tables */
function tinf_build_bits_base(bits, base, delta, first) {
  var i, sum;

  /* build bits table */
  for (i = 0; i < delta; ++i) bits[i] = 0;
  for (i = 0; i < 30 - delta; ++i) bits[i + delta] = i / delta | 0;

  /* build base table */
  for (sum = first, i = 0; i < 30; ++i) {
    base[i] = sum;
    sum += 1 << bits[i];
  }
}

/* build the fixed huffman trees */
function tinf_build_fixed_trees(lt, dt) {
  var i;

  /* build fixed length tree */
  for (i = 0; i < 7; ++i) lt.table[i] = 0;

  lt.table[7] = 24;
  lt.table[8] = 152;
  lt.table[9] = 112;

  for (i = 0; i < 24; ++i) lt.trans[i] = 256 + i;
  for (i = 0; i < 144; ++i) lt.trans[24 + i] = i;
  for (i = 0; i < 8; ++i) lt.trans[24 + 144 + i] = 280 + i;
  for (i = 0; i < 112; ++i) lt.trans[24 + 144 + 8 + i] = 144 + i;

  /* build fixed distance tree */
  for (i = 0; i < 5; ++i) dt.table[i] = 0;

  dt.table[5] = 32;

  for (i = 0; i < 32; ++i) dt.trans[i] = i;
}

/* given an array of code lengths, build a tree */
var offs = new Uint16Array(16);

function tinf_build_tree(t, lengths, off, num, kind) {
  var i, sum, max = 0, left = 1;
  if (!Number.isSafeInteger(num) || num < 1 || num > 288 || off < 0 || off + num > lengths.length)
    throw new Error("Invalid Huffman alphabet range");
  t.table.fill(0); t.trans.fill(0);
  for (i = 0; i < num; i++) {
    var n = lengths[off + i];
    if (n > 15) throw new Error("Invalid Huffman code length");
    t.table[n]++; if (n > max) max = n;
  }
  t.table[0] = 0;
  for (i = 1; i <= 15; i++) {
    left = 2 * left - t.table[i];
    if (left < 0) throw new Error("Oversubscribed Huffman tree");
  }
  if (max === 0) {
    if (kind !== "distance") throw new Error("Empty Huffman tree");
  } else if (left > 0 && (kind === "codes" || max !== 1)) {
    throw new Error("Incomplete Huffman tree");
  }
  for (sum = 0, i = 0; i < 16; i++) { offs[i] = sum; sum += t.table[i]; }
  for (i = 0; i < num; i++) if (lengths[off + i]) t.trans[offs[lengths[off + i]]++] = i;
}

/* ---------------------- *
 * -- decode functions -- *
 * ---------------------- */

/* get one bit from source stream */
function tinf_getbit(d) {
  if (d.bitcount === 0) {
    if (d.sourceIndex >= d.source.length) throw new Error("Truncated DEFLATE bit stream");
    d.tag = d.source[d.sourceIndex++]; d.bitcount = 8;
  }
  var bit = d.tag & 1; d.tag >>>= 1; d.bitcount--; return bit;
}

/* read a num bit value from a stream and add base */
function tinf_read_bits(d, num, base) {
  if (!Number.isInteger(num) || num < 0 || num > 15 || !Number.isInteger(base))
    throw new Error("Invalid DEFLATE bit request");
  var value = 0;
  for (var i = 0; i < num; i++) value |= tinf_getbit(d) << i;
  return value + base;
}

/* given a data stream and a tree, decode a symbol */
function tinf_decode_symbol(d, t) {
  var sum = 0, cur = 0;
  for (var len = 1; len <= 15; len++) {
    cur = 2 * cur + tinf_getbit(d); sum += t.table[len]; cur -= t.table[len];
    if (cur < 0) {
      var index = sum + cur;
      if (index < 0 || index >= t.trans.length) throw new Error("Invalid Huffman symbol index");
      return t.trans[index];
    }
  }
  throw new Error("Invalid or empty Huffman code");
}

/* given a data stream, decode dynamic trees from it */
function tinf_decode_trees(d, lt, dt) {
  var hlit = tinf_read_bits(d, 5, 257), hdist = tinf_read_bits(d, 5, 1);
  var hclen = tinf_read_bits(d, 4, 4);
  if (hlit > 286 || hdist > 32) throw new Error("Reserved dynamic alphabet size");
  lengths.fill(0);
  for (var i = 0; i < hclen; i++) lengths[clcidx[i]] = tinf_read_bits(d, 3, 0);
  tinf_build_tree(code_tree, lengths, 0, 19, "codes");
  var num = 0, total = hlit + hdist;
  while (num < total) {
    var sym = tinf_decode_symbol(d, code_tree), value, repeat;
    if (sym <= 15) { value = sym; repeat = 1; }
    else if (sym === 16) {
      if (num === 0) throw new Error("Repeat before first code length");
      value = lengths[num - 1]; repeat = tinf_read_bits(d, 2, 3);
    } else if (sym === 17) { value = 0; repeat = tinf_read_bits(d, 3, 3); }
    else if (sym === 18) { value = 0; repeat = tinf_read_bits(d, 7, 11); }
    else throw new Error("Invalid code-length symbol");
    if (num + repeat > total) throw new Error("Code-length repeat overflow");
    for (i = 0; i < repeat; i++) lengths[num++] = value;
  }
  if (lengths[256] === 0) throw new Error("Missing DEFLATE end-of-block symbol");
  tinf_build_tree(lt, lengths, 0, hlit, "literal");
  tinf_build_tree(dt, lengths, hlit, hdist, "distance");
}

/* ----------------------------- *
 * -- block inflate functions -- *
 * ----------------------------- */

/* given a stream and two trees, inflate a block of data */
function tinf_inflate_block_data(d, lt, dt) {
  while (true) {
    var sym = tinf_decode_symbol(d, lt);
    if (sym === 256) return TINF_OK;
    if (sym < 256) {
      if (d.destLen >= d.dest.length) throw new Error("DEFLATE output exceeds supplied length");
      d.dest[d.destLen++] = sym;
    } else {
      if (sym < 257 || sym > 285) throw new Error("Reserved DEFLATE length code");
      sym -= 257;
      var length = tinf_read_bits(d, length_bits[sym], length_base[sym]);
      var dist = tinf_decode_symbol(d, dt);
      if (dist > 29) throw new Error("Reserved DEFLATE distance code");
      var distance = tinf_read_bits(d, dist_bits[dist], dist_base[dist]);
      if (distance < 1 || distance > 32768 || distance > d.destLen) throw new Error("Invalid DEFLATE backreference");
      if (length < 3 || length > 258 || d.destLen + length > d.dest.length) throw new Error("DEFLATE match output overflow");
      for (var i = 0; i < length; i++) { d.dest[d.destLen] = d.dest[d.destLen - distance]; d.destLen++; }
    }
  }
}

/* inflate an uncompressed block of data */
function tinf_inflate_uncompressed_block(d) {
  d.bitcount = 0; d.tag = 0;
  if (d.sourceIndex + 4 > d.source.length) throw new Error("Truncated stored DEFLATE header");
  var p = d.sourceIndex, length = d.source[p] | (d.source[p + 1] << 8);
  var inverse = d.source[p + 2] | (d.source[p + 3] << 8);
  if (length !== ((~inverse) & 65535)) throw new Error("Stored DEFLATE length mismatch");
  d.sourceIndex += 4;
  if (d.sourceIndex + length > d.source.length || d.destLen + length > d.dest.length)
    throw new Error("Stored DEFLATE range overflow");
  for (var i = 0; i < length; i++) d.dest[d.destLen++] = d.source[d.sourceIndex++];
  return TINF_OK;
}

/* inflate stream from source to dest */
function tinf_uncompress(source, dest) {
  var d = new Data(source, dest), final, type, result;
  do {
    final = tinf_getbit(d); type = tinf_read_bits(d, 2, 0);
    if (type === 0) result = tinf_inflate_uncompressed_block(d);
    else if (type === 1) result = tinf_inflate_block_data(d, sltree, sdtree);
    else if (type === 2) {
      tinf_decode_trees(d, d.ltree, d.dtree); result = tinf_inflate_block_data(d, d.ltree, d.dtree);
    } else throw new Error("Reserved DEFLATE block type");
    if (result !== TINF_OK) throw new Error("DEFLATE data error");
  } while (!final);
  if (d.destLen !== dest.length) throw new Error("DEFLATE output size mismatch");
  if (d.sourceIndex !== source.length) throw new Error("Trailing compressed bytes");
  return dest;
}

/* -------------------- *
 * -- initialization -- *
 * -------------------- */

/* build fixed huffman trees */
tinf_build_fixed_trees(sltree, sdtree);

/* build extra bits and base tables */
tinf_build_bits_base(length_bits, length_base, 4, 3);
tinf_build_bits_base(dist_bits, dist_base, 2, 1);

/* fix a special case */
length_bits[28] = 0;
length_base[28] = 258;

return tinf_uncompress(source, new Uint8Array(expectedBytes));
}
