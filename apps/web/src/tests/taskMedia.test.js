import { describe, expect, it } from 'vitest'
import { referenceMediaUrl } from '../lib/taskMedia.js'

describe('参考媒体地址', () => {
  it('优先使用 URL 并兼容旧媒体 ID', () => {
    expect(referenceMediaUrl({
      referenceVideoUrl: 'https://cdn.example.com/video.mp4',
      referenceVideoMediaId: 'old-video',
    }, 'video')).toBe('https://cdn.example.com/video.mp4')
    expect(referenceMediaUrl({ referenceAudioMediaId: 'old-audio' }, 'audio'))
      .toBe('/api/media/public/reference/old-audio')
    expect(referenceMediaUrl({}, 'audio')).toBe('')
  })
})
