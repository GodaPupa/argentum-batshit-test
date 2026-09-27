// Pure data helper: strict base64 decoding and SHA-256; no I/O or process operations.
// SHA-256 compression core/constants are unchanged from the earlier decoded-log hash.
const MAX_BYTE_INPUT = 33554432;
function utf8Bytes(s) {
  if (typeof s !== "string") throw new TypeError("UTF-8 input must be a string");
  const a = [];
  for (const ch of s) {
    const c = ch.codePointAt(0);
    if (c >= 0xd800 && c <= 0xdfff) throw new Error("Unpaired UTF-16 surrogate");
    if (c < 128) a.push(c);
    else if (c < 2048) a.push(192 | (c >> 6), 128 | (c & 63));
    else if (c < 65536) a.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63));
    else a.push(240 | (c >> 18), 128 | ((c >> 12) & 63), 128 | ((c >> 6) & 63), 128 | (c & 63));
  }
  if (a.length > MAX_BYTE_INPUT) throw new Error("UTF-8 byte input too large");
  return new Uint8Array(a);
}
function decodeBase64Exact(text, expectedBytes) {
  if (typeof text !== "string") throw new TypeError("Base64 input must be a string");
  if (!Number.isSafeInteger(expectedBytes) || expectedBytes < 0 || expectedBytes > MAX_BYTE_INPUT)
    throw new Error("Invalid expected byte count");
  if (text.length !== 4 * Math.ceil(expectedBytes / 3)) throw new Error("Encoded length mismatch");
  const out = new Uint8Array(expectedBytes);
  function digit(c) {
    if (c >= 65 && c <= 90) return c - 65;
    if (c >= 97 && c <= 122) return c - 71;
    if (c >= 48 && c <= 57) return c + 4;
    if (c === 43) return 62;
    if (c === 47) return 63;
    throw new Error("Invalid base64 character");
  }
  let at = 0;
  for (let i = 0; i < text.length; i += 4) {
    const a = digit(text.charCodeAt(i)), b = digit(text.charCodeAt(i + 1));
    const remaining = expectedBytes - at;
    if (remaining === 1) {
      if (text[i + 2] !== "=" || text[i + 3] !== "=" || (b & 15) !== 0)
        throw new Error("Invalid/noncanonical two-character padding");
      out[at++] = (a << 2) | (b >> 4);
    } else {
      const c = digit(text.charCodeAt(i + 2));
      if (remaining === 2) {
        if (text[i + 3] !== "=" || (c & 3) !== 0)
          throw new Error("Invalid/noncanonical one-character padding");
        out[at++] = (a << 2) | (b >> 4);
        out[at++] = ((b & 15) << 4) | (c >> 2);
      } else {
        const d = digit(text.charCodeAt(i + 3));
        out[at++] = (a << 2) | (b >> 4);
        out[at++] = ((b & 15) << 4) | (c >> 2);
        out[at++] = ((c & 3) << 6) | d;
      }
    }
  }
  if (at !== expectedBytes) throw new Error("Decoded length mismatch");
  return out;
}
function sha256Bytes(input) {
  if (!(input instanceof Uint8Array)) throw new TypeError("Hash input must be Uint8Array");
  const n = input.length;
  if (n > MAX_BYTE_INPUT) throw new Error("Hash input too large");
  const l = n * 8, b = new Uint8Array(Math.ceil((n + 9) / 64) * 64);
  b.set(input); b[n] = 128;
  const hi = Math.floor(l / 4294967296), lo = l >>> 0;
  for (let i = 0; i < 4; i++) b[b.length - 8 + i] = (hi >>> (24 - 8 * i)) & 255;
  for (let i = 0; i < 4; i++) b[b.length - 4 + i] = (lo >>> (24 - 8 * i)) & 255;
const k=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
let h=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];const rr=(x,n)=>(x>>>n)|(x<<(32-n));
for(let o=0;o<b.length;o+=64){let w=[];for(let t=0;t<16;t++)w[t]=((b[o+4*t]<<24)|(b[o+4*t+1]<<16)|(b[o+4*t+2]<<8)|b[o+4*t+3])>>>0;for(let t=16;t<64;t++){const x=w[t-15],y=w[t-2];w[t]=(w[t-16]+(rr(x,7)^rr(x,18)^(x>>>3))+w[t-7]+(rr(y,17)^rr(y,19)^(y>>>10)))>>>0;}let [a,c,d,e,f,g,i,j]=h;for(let t=0;t<64;t++){const t1=(j+(rr(f,6)^rr(f,11)^rr(f,25))+((f&g)^((~f)&i))+k[t]+w[t])>>>0;const t2=((rr(a,2)^rr(a,13)^rr(a,22))+((a&c)^(a&d)^(c&d)))>>>0;j=i;i=g;g=f;f=(e+t1)>>>0;e=d;d=c;c=a;a=(t1+t2)>>>0;}const v=[a,c,d,e,f,g,i,j];h=h.map((x,t)=>(x+v[t])>>>0);}return h.map(x=>x.toString(16).padStart(8,"0")).join("");}
function sha256Utf8(text) { return sha256Bytes(utf8Bytes(text)); }
