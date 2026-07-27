export function referenceMediaUrl(item, type) {
  const prefix = type === 'video' ? 'Video' : 'Audio'
  const direct = item?.[`reference${prefix}Url`]
  if (direct) return direct
  const mediaId = item?.[`reference${prefix}MediaId`]
  return mediaId ? `/api/media/public/reference/${encodeURIComponent(mediaId)}` : ''
}
