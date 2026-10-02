import type { NearbyFilters, ParkingSort } from '../types/search'

interface Props { radius: number; filters: NearbyFilters; sort: ParkingSort; onRadius: (value: number) => void; onFilters: (value: NearbyFilters) => void; onSort: (value: ParkingSort) => void }

export default function SearchControls({ radius, filters, sort, onRadius, onFilters, onSort }: Props) {
  return <div className="search-controls-group"><section className="search-controls" aria-label="주차장 검색 조건">
    <label>반경<select value={radius} onChange={(event) => onRadius(Number(event.target.value))}>
      <option value={500}>500m</option><option value={1000}>1km</option><option value={2000}>2km</option><option value={5000}>5km</option>
    </select></label>
    <div className="filter-chips" aria-label="필터">
      <button type="button" className={!filters.parkingType && !filters.free && !filters.open24Hours && !filters.realtimeAvailable ? 'is-active' : ''} onClick={() => onFilters({ parkingType: null, free: false, open24Hours: false, realtimeAvailable: false })}>전체</button>
      {['공영', '민영', '무료', '24시간'].map(label => <button key={label} type="button" disabled aria-describedby="unsupported-filters">{label}</button>)}
      <button type="button" className={filters.realtimeAvailable ? 'is-active' : ''} onClick={() => onFilters({ ...filters, realtimeAvailable: !filters.realtimeAvailable })}>실시간 정보 있음</button>
    </div>
    <label>정렬<select value={sort} onChange={(event) => onSort(event.target.value as ParkingSort)}>
      <option value="DISTANCE">거리순</option><option value="FEE">기본요금 낮은순</option><option value="AVAILABLE">잔여면수 많은순</option>
    </select></label>
  </section><small id="unsupported-filters">공영·민영·무료·24시간: 분류 데이터 미지원</small></div>
}
