import type { KeyboardEvent, MouseEvent } from 'react'
import type { NearbyParkingLot } from '../types/parking'
import { formatDistance, formatFee } from '../utils/format'

interface Props { parkingLot: NearbyParkingLot; selected: boolean; onSelect: () => void; onShowDetail: () => void }
export default function ParkingLotCard({ parkingLot, selected, onSelect, onShowDetail }: Props) {
  const realtime = parkingLot.availableSpaces == null ? '실시간 정보 없음' : `잔여 ${parkingLot.availableSpaces}면`
  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); onSelect() } }
  const showDetail = (event: MouseEvent<HTMLButtonElement>) => { event.stopPropagation(); onShowDetail() }
  return <article className={`parking-card ${selected ? 'parking-card--selected' : ''}`} role="button" tabIndex={0} aria-pressed={selected} onClick={onSelect} onKeyDown={onKeyDown}>
    <div className="parking-card__heading"><h3>{parkingLot.name}</h3><strong>{formatDistance(parkingLot.distanceMeters)}</strong></div>
    <p className="parking-card__address">{parkingLot.address || '주소 정보가 없습니다.'}</p>
    <div className="parking-card__badges"><span>{parkingLot.parkingType === 'PUBLIC' ? '공영' : parkingLot.parkingType === 'PRIVATE' ? '민영' : '유형 정보 없음'}</span><span className={parkingLot.isFree ? 'badge-free' : ''}>{parkingLot.isFree ? '무료' : '요금 확인'}</span><span>{parkingLot.is24Hours ? '24시간' : '운영시간 확인'}</span></div>
    <div className="parking-card__status"><span className={`fee-text ${parkingLot.isFree ? 'fee-text--free' : ''}`}>{formatFee(parkingLot.baseTime, parkingLot.baseFee, parkingLot.isFree)}</span><span className={`realtime-text ${parkingLot.availableSpaces === 0 ? 'realtime-text--full' : ''}`}>{realtime}</span>{parkingLot.stale === true && <span className="stale-badge">업데이트 지연</span>}</div>
    <button className="detail-button" type="button" onClick={showDetail}>상세보기</button>
  </article>
}
