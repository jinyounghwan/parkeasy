export function formatDistance(distanceMeters: number) {
  return distanceMeters < 1000 ? `${distanceMeters}m` : `${(distanceMeters / 1000).toFixed(1)}km`
}

export function formatFee(baseTime: number | null, baseFee: number | null, isFree: boolean | null = null) {
  if (isFree) return '무료'
  if (baseTime == null || baseFee == null) return '요금정보 없음'
  return `${baseTime}분 ${baseFee.toLocaleString('ko-KR')}원`
}

export function formatWon(value: number | null) {
  return value == null ? '정보 없음' : `${value.toLocaleString('ko-KR')}원`
}

export function formatTime(value: string | null) {
  if (!value) return null
  return value.slice(0, 5)
}

export function formatTimeRange(open: string | null, close: string | null) {
  const formattedOpen = formatTime(open)
  const formattedClose = formatTime(close)
  return formattedOpen && formattedClose ? `${formattedOpen} ~ ${formattedClose}` : '정보 없음'
}

export function formatReceivedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '시간 정보 없음'
  return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(date)
}
