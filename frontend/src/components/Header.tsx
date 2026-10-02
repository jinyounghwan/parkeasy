import type { SearchOriginType } from '../types/search'

interface Props { originType: SearchOriginType; isLocating: boolean; onCurrentLocation: () => void; onTestLocation: () => void }

export default function Header({ originType, isLocating, onCurrentLocation, onTestLocation }: Props) {
  return <header className="app-header">
    <div className="brand-mark" aria-hidden="true">P</div>
    <div className="brand-copy"><h1>ParkEasy</h1><p>목적지 주변 주차장을 빠르게</p></div>
    <div className="location-switch" aria-label="검색 기준 위치 선택">
      <button type="button" className={originType === 'CURRENT_LOCATION' ? 'location-button is-active' : 'location-button'} onClick={onCurrentLocation} disabled={isLocating}>{isLocating ? '확인 중' : '내 위치'}</button>
      <button type="button" className={originType === 'TEST_LOCATION' ? 'location-button is-active location-button--test' : 'location-button location-button--test'} onClick={onTestLocation}>테스트</button>
    </div>
  </header>
}
