import { useEffect, useRef } from 'react'
import type { NearbyParkingLot } from '../types/parking'
import ParkingLotCard from './ParkingLotCard'

interface Props { parkingLots: NearbyParkingLot[]; isLoading: boolean; error: string | null; radius: number; onWidenRadius: () => void; selectedParkingLotId: number | null; onSelect: (lot: NearbyParkingLot) => void; onShowDetail: (lot: NearbyParkingLot) => void }
export default function ParkingLotList({ parkingLots, isLoading, error, radius, onWidenRadius, selectedParkingLotId, onSelect, onShowDetail }: Props) {
  const refs = useRef(new Map<number, HTMLDivElement>())
  useEffect(() => { if (selectedParkingLotId != null) refs.current.get(selectedParkingLotId)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }) }, [selectedParkingLotId])
  return <section className="parking-list-panel" aria-labelledby="parking-list-title">
    <div className="panel-heading"><div><p className="eyebrow">NEARBY PARKING</p><h2 id="parking-list-title">주변 주차장</h2></div>{!isLoading && !error && <span className="result-count">{parkingLots.length}곳</span>}</div>
    <div className="parking-list" aria-live="polite">
      {isLoading && <div className="state-card"><span className="spinner" />주변 주차장을 찾고 있습니다.</div>}{error && <div className="state-card state-card--error">{error}</div>}
      {!isLoading && !error && parkingLots.length === 0 && <div className="state-card state-card--empty"><span>선택한 반경 내 주차장이 없습니다.</span><small>검색 반경을 넓혀보세요.</small>{radius < 2000 && <button type="button" onClick={onWidenRadius}>2km로 넓히기</button>}</div>}
      {!isLoading && !error && parkingLots.map((lot) => <div key={lot.id} ref={(element) => { if (element) refs.current.set(lot.id, element); else refs.current.delete(lot.id) }}><ParkingLotCard parkingLot={lot} selected={lot.id === selectedParkingLotId} onSelect={() => onSelect(lot)} onShowDetail={() => onShowDetail(lot)} /></div>)}
    </div>
  </section>
}
