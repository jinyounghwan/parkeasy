/* oxlint-disable react/set-state-in-effect -- Effects synchronize browser location, URL and request lifecycle state. */
import { useCallback, useEffect, useRef, useState } from 'react'
import { getNearbyParkingLots, getParkingLotDetail } from './api/parkingApi'
import DestinationSearch from './components/DestinationSearch'
import Header from './components/Header'
import KakaoMap from './components/KakaoMap'
import ParkingLotDetailPanel from './components/ParkingLotDetailPanel'
import ParkingLotList from './components/ParkingLotList'
import SearchControls from './components/SearchControls'
import type { Coordinates, NearbyParkingLot, ParkingLotDetail } from './types/parking'
import type { NearbyFilters, ParkingSort, PlaceSearchResult, SearchOriginType } from './types/search'
import './App.css'

const FALLBACK_POSITION: Coordinates = { latitude: 37.2636, longitude: 127.0286 }
const DEFAULT_FILTERS: NearbyFilters = { parkingType: null, free: false, open24Hours: false, realtimeAvailable: false }
const mapKey = import.meta.env.VITE_KAKAO_MAP_KEY?.trim() ?? ''

function restoredSearch(): { position: Coordinates | null; radius: number } {
  const params = new URLSearchParams(window.location.search)
  const latitude = Number(params.get('lat')); const longitude = Number(params.get('lng')); const radius = Number(params.get('radius'))
  const validPosition = !!params.get('lat')?.trim() && !!params.get('lng')?.trim() && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90 && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
  return { position: validPosition ? { latitude, longitude } : null, radius: [500, 1000, 2000, 5000].includes(radius) ? radius : 2000 }
}

export default function App() {
  const [restored] = useState(restoredSearch)
  const locationRequestId = useRef(0); const detailRequest = useRef<AbortController | null>(null)
  const [origin, setOrigin] = useState<Coordinates | null>(restored.position)
  const [originType, setOriginType] = useState<SearchOriginType>(restored.position ? 'SEARCHED_PLACE' : 'CURRENT_LOCATION')
  const [originName, setOriginName] = useState<string | null>(restored.position ? '저장된 검색 위치' : null)
  const [radius, setRadius] = useState(restored.radius); const [filters, setFilters] = useState(DEFAULT_FILTERS); const [sort, setSort] = useState<ParkingSort>('DISTANCE')
  const [parkingLots, setParkingLots] = useState<NearbyParkingLot[]>([])
  const [isLocating, setIsLocating] = useState(!restored.position); const [isLoadingParking, setIsLoadingParking] = useState(false)
  const [locationNotice, setLocationNotice] = useState<string | null>(null); const [apiError, setApiError] = useState<string | null>(null); const [mapError, setMapError] = useState<string | null>(null)
  const [selectedParkingLotId, setSelectedParkingLotId] = useState<number | null>(null); const [detail, setDetail] = useState<ParkingLotDetail | null>(null)
  const [isDetailOpen, setIsDetailOpen] = useState(false); const [isLoadingDetail, setIsLoadingDetail] = useState(false); const [detailError, setDetailError] = useState<string | null>(null)

  const resetSelection = useCallback(() => { detailRequest.current?.abort(); setSelectedParkingLotId(null); setDetail(null); setIsDetailOpen(false); setIsLoadingDetail(false); setDetailError(null) }, [])
  const changeOrigin = useCallback((position: Coordinates, type: SearchOriginType, name: string | null) => { locationRequestId.current += 1; setOrigin(position); setOriginType(type); setOriginName(name); setLocationNotice(null); setIsLocating(false); resetSelection() }, [resetSelection])
  const selectTestLocation = useCallback(() => { changeOrigin(FALLBACK_POSITION, 'TEST_LOCATION', '수원시청 테스트 위치'); setLocationNotice('개발용 수원시청 위치를 기준으로 검색합니다.') }, [changeOrigin])
  const selectUserLocation = useCallback(() => {
    const requestId = ++locationRequestId.current; setOriginType('CURRENT_LOCATION'); setOriginName(null); setIsLocating(true); setLocationNotice(null); resetSelection()
    if (!navigator.geolocation) { changeOrigin(FALLBACK_POSITION, 'TEST_LOCATION', '수원시청 테스트 위치'); setLocationNotice('이 브라우저는 위치 기능을 지원하지 않아 테스트 위치를 사용합니다.'); return }
    navigator.geolocation.getCurrentPosition(({ coords }) => { if (locationRequestId.current === requestId) changeOrigin({ latitude: coords.latitude, longitude: coords.longitude }, 'CURRENT_LOCATION', '내 위치') }, () => { if (locationRequestId.current === requestId) { changeOrigin(FALLBACK_POSITION, 'TEST_LOCATION', '수원시청 테스트 위치'); setLocationNotice('위치 권한을 사용할 수 없어 테스트 위치를 사용합니다.') } }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 })
  }, [changeOrigin, resetSelection])
  useEffect(() => { if (!restored.position) selectUserLocation() }, [restored.position, selectUserLocation])

  useEffect(() => {
    if (!origin) return
    const controller = new AbortController(); setParkingLots([]); setIsLoadingParking(true); setApiError(null); resetSelection()
    getNearbyParkingLots(origin, radius, filters, sort, controller.signal).then(setParkingLots).catch((error: unknown) => { if (!(error instanceof DOMException && error.name === 'AbortError')) setApiError('주변 주차장 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.') }).finally(() => { if (!controller.signal.aborted) setIsLoadingParking(false) })
    const params = new URLSearchParams(window.location.search); params.set('lat', String(origin.latitude)); params.set('lng', String(origin.longitude)); params.set('radius', String(radius)); window.history.replaceState(null, '', `${window.location.pathname}?${params}`)
    return () => controller.abort()
  }, [filters, origin, radius, resetSelection, sort])


  const handlePlace = useCallback((place: PlaceSearchResult) => changeOrigin({ latitude: place.latitude, longitude: place.longitude }, 'SEARCHED_PLACE', place.name), [changeOrigin])
  const handleSelect = useCallback((parkingLot: NearbyParkingLot) => setSelectedParkingLotId(parkingLot.id), [])
  const handleShowDetail = useCallback((parkingLot: NearbyParkingLot) => { setSelectedParkingLotId(parkingLot.id); setIsDetailOpen(true); setDetail(null); setDetailError(null); setIsLoadingDetail(true); detailRequest.current?.abort(); const controller = new AbortController(); detailRequest.current = controller; getParkingLotDetail(parkingLot.id, controller.signal).then(setDetail).catch((error: unknown) => { if (!(error instanceof DOMException && error.name === 'AbortError')) setDetailError('상세 정보를 불러오지 못했습니다.') }).finally(() => { if (!controller.signal.aborted) setIsLoadingDetail(false) }) }, [])
  const closeDetail = useCallback(() => { detailRequest.current?.abort(); setIsDetailOpen(false); setDetail(null); setDetailError(null); setIsLoadingDetail(false) }, [])

  return <div className="app-shell">
    <Header originType={originType} isLocating={isLocating} onCurrentLocation={selectUserLocation} onTestLocation={selectTestLocation} />
    <div className="search-toolbar"><DestinationSearch mapKey={mapKey} destinationName={originType === 'SEARCHED_PLACE' ? originName : null} onSelect={handlePlace} /><SearchControls radius={radius} filters={filters} sort={sort} onRadius={setRadius} onFilters={setFilters} onSort={setSort} /></div>
    {(isLocating || locationNotice) && <div className={`notice ${locationNotice ? 'notice--warning' : ''}`} role="status">{isLocating ? '현재 위치를 확인하고 있습니다.' : locationNotice}</div>}
    <main className="content-grid">
      <ParkingLotList parkingLots={parkingLots} isLoading={isLocating || isLoadingParking} error={apiError} radius={radius} onWidenRadius={() => setRadius(2000)} selectedParkingLotId={selectedParkingLotId} onSelect={handleSelect} onShowDetail={handleShowDetail} />
      <section className="map-panel" aria-label="지도 영역">
        {!mapKey && <div className="map-message map-message--error">VITE_KAKAO_MAP_KEY가 설정되지 않았습니다.</div>}{mapError && <div className="map-message map-message--error">{mapError}</div>}
        {mapKey && origin && !mapError && <KakaoMap mapKey={mapKey} origin={origin} originType={originType} originName={originName} radius={radius} parkingLots={parkingLots} selectedParkingLotId={selectedParkingLotId} onSelect={handleSelect} onShowDetail={handleShowDetail} onError={setMapError} />}
        {mapKey && !origin && !mapError && <div className="map-message"><span className="spinner" />지도를 준비하고 있습니다.</div>}
        {isDetailOpen && <ParkingLotDetailPanel detail={detail} isLoading={isLoadingDetail} error={detailError} onClose={closeDetail} />}
      </section>
    </main>
  </div>
}
