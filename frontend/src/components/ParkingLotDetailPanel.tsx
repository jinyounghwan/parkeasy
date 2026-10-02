import type { ParkingLotDetail } from '../types/parking'
import { formatFee, formatReceivedAt, formatTimeRange, formatWon } from '../utils/format'

interface Props { detail: ParkingLotDetail | null; isLoading: boolean; error: string | null; onClose: () => void }
const dayRows = [
  ['월', 'mondayOpenTime', 'mondayCloseTime'], ['화', 'tuesdayOpenTime', 'tuesdayCloseTime'],
  ['수', 'wednesdayOpenTime', 'wednesdayCloseTime'], ['목', 'thursdayOpenTime', 'thursdayCloseTime'],
  ['금', 'fridayOpenTime', 'fridayCloseTime'], ['토', 'saturdayOpenTime', 'saturdayCloseTime'],
  ['일', 'sundayOpenTime', 'sundayCloseTime'], ['공휴일', 'holidayOpenTime', 'holidayCloseTime'],
] as const

export default function ParkingLotDetailPanel({ detail, isLoading, error, onClose }: Props) {
  return (
    <aside className="detail-panel" aria-label="주차장 상세정보" aria-live="polite">
      <div className="detail-panel__handle" />
      <button type="button" className="detail-panel__close" onClick={onClose} aria-label="상세정보 닫기">×</button>
      {isLoading && <div className="detail-state"><span className="spinner" />상세정보 불러오는 중</div>}
      {error && <div className="detail-state detail-state--error">{error}</div>}
      {!isLoading && !error && detail && (
        <div className="detail-content">
          <header><p className="eyebrow">PARKING DETAIL</p><h2>{detail.name}</h2><p>{detail.address || '주소 정보가 없습니다.'}</p></header>
          <section><h3>시설</h3><dl className="detail-list"><dt>총 주차면수</dt><dd>{detail.totalSpaces == null ? '정보 없음' : `${detail.totalSpaces}면`}</dd></dl></section>
          <section><h3>요금</h3>{detail.operation ? (
            <dl className="detail-list">
              {detail.operation.baseFreeTime != null && <><dt>기본 무료</dt><dd>{detail.operation.baseFreeTime}분</dd></>}
              <dt>기본</dt><dd>{formatFee(detail.operation.baseTime, detail.operation.baseFee)}</dd>
              <dt>추가</dt><dd>{formatFee(detail.operation.additionalTime, detail.operation.additionalFee)}</dd>
              <dt>일 최대</dt><dd>{formatWon(detail.operation.dailyMaxFee)}</dd>
              <dt>월 정기</dt><dd>{formatWon(detail.operation.monthlyFee)}</dd>
            </dl>
          ) : <p className="detail-empty">요금·운영정보가 없습니다.</p>}</section>
          <section><h3>운영시간</h3>{detail.operation ? <dl className="hours-list">{dayRows.map(([label, open, close]) => (
            <div key={label}><dt>{label}</dt><dd>{formatTimeRange(detail.operation![open], detail.operation![close])}</dd></div>
          ))}</dl> : <p className="detail-empty">운영시간 정보가 없습니다.</p>}</section>
          <section><h3>실시간</h3>{detail.realtime ? <dl className="detail-list">
            <dt>실시간 집계 기준</dt><dd>{detail.realtime.totalSpaces == null ? '정보 없음' : `${detail.realtime.totalSpaces}면`}</dd>
            <dt>잔여</dt><dd className={detail.realtime.availableSpaces === 0 ? 'text-full' : 'text-available'}>
              {detail.realtime.availableSpaces == null ? '정보 없음' : `${detail.realtime.availableSpaces}면`}
            </dd>
            <dt>업데이트</dt><dd>{formatReceivedAt(detail.realtime.receivedAt)}</dd>
            {detail.realtime.stale && <><dt>상태</dt><dd><span className="stale-badge">업데이트 지연</span></dd></>}
          </dl> : <p className="detail-empty">실시간 정보가 없습니다.</p>}</section>
        </div>
      )}
    </aside>
  )
}
