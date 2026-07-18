const test=require('node:test'),assert=require('node:assert/strict');const{wavHeader}=require('../services/audioFileWriter.js')
test('WAV 头包含正确 RIFF 和数据长度',()=>{const header=wavHeader(32000,16000,1),view=new DataView(header);assert.equal(String.fromCharCode(...new Uint8Array(header,0,4)),'RIFF');assert.equal(view.getUint32(40,true),32000);assert.equal(view.getUint32(4,true),32036)})
