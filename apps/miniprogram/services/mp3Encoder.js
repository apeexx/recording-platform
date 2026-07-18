function createMp3Encoder({ lame, sampleRate, bitRate = 48 }) {
  const encoder = new lame.Mp3Encoder(1, sampleRate, bitRate), carry = []
  let flushed = false
  function encode(buffer) {
    const input = new Int16Array(buffer), merged = new Int16Array(carry.length + input.length)
    merged.set(carry); merged.set(input, carry.length); carry.length = 0
    const chunks = []
    let offset = 0
    while (offset + 1152 <= merged.length) { const encoded = encoder.encodeBuffer(merged.subarray(offset, offset + 1152)); if (encoded.length) chunks.push(new Uint8Array(encoded).buffer); offset += 1152 }
    for (; offset < merged.length; offset++) carry.push(merged[offset])
    return chunks
  }
  function flush() { if (flushed) return []; flushed = true; const chunks=[]; if(carry.length){const encoded=encoder.encodeBuffer(Int16Array.from(carry));if(encoded.length)chunks.push(new Uint8Array(encoded).buffer)}const tail=encoder.flush();if(tail.length)chunks.push(new Uint8Array(tail).buffer);return chunks }
  return { encode, flush }
}
module.exports = { createMp3Encoder }
