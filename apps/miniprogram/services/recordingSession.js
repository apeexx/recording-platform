const { pcmRms, durationMillis } = require('./pcm.js')
const { createAudioFileWriter } = require('./audioFileWriter.js')

function createRecordingSession({ recorder, fs, userDataPath, version, onLevel=()=>{}, onState=()=>{}, onComplete=()=>{}, onError=()=>{} }) {
  const sampleRate=Number((version.sampleRates||[16000])[0]),format=String(version.recordingFormat||'WAV').toUpperCase()
  let state='idle',sampleCount=0,writer,path,stopPromise,resolveStop,rejectStop,failure
  const stateTo=value=>{state=value;onState(value)}
  function fail(error){
    if(state==='error')return
    failure=error instanceof Error?error:new Error(String(error||'录音保存失败'))
    const failedWriter=writer;writer=null;onLevel(0);stateTo('error');onError(failure);rejectStop?.(failure)
    Promise.resolve(failedWriter?.abort()).catch(()=>{})
    try{recorder.stop()}catch{}
  }
  recorder.onFrameRecorded(({frameBuffer})=>{if(state!=='recording'||!writer)return;sampleCount+=frameBuffer.byteLength/2;onLevel(pcmRms(frameBuffer));Promise.resolve(writer.appendPcm(frameBuffer)).catch(fail)})
  recorder.onStop(async()=>{if(!writer||!stopPromise)return;try{await writer.finish();const result={filePath:path,format,durationMillis:durationMillis(sampleCount,sampleRate),sampleCount};stateTo('stopped');onComplete(result);resolveStop(result)}catch(error){fail(error)}})
  function start(){sampleCount=0;failure=null;path=`${userDataPath}/recording-${Date.now()}.${format.toLowerCase()}`;const lame=format==='MP3'?require('../vendor/lamejs.iife.js'):null;writer=createAudioFileWriter({fs,path,format,sampleRate,channels:1,bitRate:48,lame});stopPromise=new Promise((resolve,reject)=>{resolveStop=resolve;rejectStop=reject});stopPromise.catch(()=>{});stateTo('recording');recorder.start({duration:Math.min(Number(version.maxDurationMillis||600000),600000),sampleRate,numberOfChannels:1,encodeBitRate:48000,format:'PCM',frameSize:4})}
  function pause(){if(state==='recording'){recorder.pause();onLevel(0);stateTo('paused')}}
  function resume(){if(state==='paused'){recorder.resume();stateTo('recording')}}
  function stop(){if(state==='error')return Promise.reject(failure);if(!['recording','paused'].includes(state))return Promise.resolve(null);stateTo('stopping');recorder.stop();return stopPromise}
  async function dispose(){if(writer&&!['stopped','idle'].includes(state))await writer.abort()}
  return{start,pause,resume,stop,dispose,getState:()=>state}
}
module.exports={createRecordingSession}
