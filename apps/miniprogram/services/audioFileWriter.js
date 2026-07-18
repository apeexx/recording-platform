function wavHeader(dataBytes, sampleRate, channels = 1) {
  const buffer = new ArrayBuffer(44), view = new DataView(buffer)
  const text = (offset, value) => [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)))
  text(0, 'RIFF'); view.setUint32(4, 36 + dataBytes, true); text(8, 'WAVE'); text(12, 'fmt ')
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, channels, true)
  view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * channels * 2, true)
  view.setUint16(32, channels * 2, true); view.setUint16(34, 16, true); text(36, 'data'); view.setUint32(40, dataBytes, true)
  return buffer
}

function createWavWriter({ fs, path, sampleRate, channels = 1 }) {
  let fd, position = 44, queue = Promise.resolve(), closed = false
  const open = new Promise((resolve, reject) => fs.open({ filePath: path, flag: 'w+', success: result => { fd = result.fd; resolve() }, fail: reject }))
  const write = (data, at) => new Promise((resolve, reject) => fs.write({ fd, data, position: at, success: resolve, fail: reject }))
  function appendPcm(buffer) { if (closed) throw new Error('writer closed'); const at = position; position += buffer.byteLength; queue = queue.then(() => open).then(() => write(buffer, at)); return queue }
  async function finish() { closed = true; await queue; await open; await write(wavHeader(position - 44, sampleRate, channels), 0); await new Promise((resolve, reject) => fs.close({ fd, success: resolve, fail: reject })); return { path, bytes: position } }
  async function abort() { closed = true; try { await queue; if (fd) await new Promise(resolve => fs.close({ fd, success: resolve, fail: resolve })) } finally { try { fs.unlinkSync(path) } catch {} } }
  return { appendPcm, finish, abort }
}

function createMp3Writer({ fs, path, sampleRate, bitRate = 48, lame }) {
  const { createMp3Encoder } = require('./mp3Encoder.js'), encoder = createMp3Encoder({ lame, sampleRate, bitRate })
  let fd, position = 0, queue = Promise.resolve(), closed = false
  const open = new Promise((resolve, reject) => fs.open({ filePath: path, flag: 'w+', success: result => { fd = result.fd; resolve() }, fail: reject }))
  const write = data => { const at=position;position+=data.byteLength;return new Promise((resolve,reject)=>fs.write({fd,data,position:at,success:resolve,fail:reject})) }
  function appendPcm(buffer){if(closed)throw new Error('writer closed');for(const chunk of encoder.encode(buffer))queue=queue.then(()=>open).then(()=>write(chunk));return queue}
  async function finish(){closed=true;for(const chunk of encoder.flush())queue=queue.then(()=>open).then(()=>write(chunk));await queue;await open;await new Promise((resolve,reject)=>fs.close({fd,success:resolve,fail:reject}));return{path,bytes:position}}
  async function abort(){closed=true;try{await queue;if(fd)await new Promise(resolve=>fs.close({fd,success:resolve,fail:resolve}))}finally{try{fs.unlinkSync(path)}catch{}}}
  return{appendPcm,finish,abort}
}

function createAudioFileWriter(options){return String(options.format).toUpperCase()==='MP3'?createMp3Writer(options):createWavWriter(options)}
module.exports = { wavHeader, createWavWriter, createMp3Writer, createAudioFileWriter }
