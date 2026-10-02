import { useEffect, useRef } from 'react'
import { loadKakaoSdk, type KakaoClusterer, type KakaoMarker, type KakaoInfoWindow, type KakaoLatLng, type KakaoMapInstance } from '../kakaoSdk'
import type { Coordinates, NearbyParkingLot } from '../types/parking'
import type { SearchOriginType } from '../types/search'
import { formatDistance, formatFee } from '../utils/format'

interface Props {
  mapKey: string; origin: Coordinates; originType: SearchOriginType; originName: string | null; radius: number
  parkingLots: NearbyParkingLot[]; selectedParkingLotId: number | null
  onSelect: (parkingLot: NearbyParkingLot) => void; onShowDetail: (parkingLot: NearbyParkingLot) => void
  onError: (message: string) => void
}
interface MarkerRecord { marker: KakaoMarker; infoWindow: KakaoInfoWindow; position: KakaoLatLng }

function createInfoContent(parkingLot: NearbyParkingLot, onShowDetail: () => void) {
  const container = document.createElement('div'); container.className = 'map-info-window'
  const name = document.createElement('strong'); name.textContent = parkingLot.name
  const distance = document.createElement('span'); distance.textContent = formatDistance(parkingLot.distanceMeters)
  const fee = document.createElement('span'); fee.textContent = formatFee(parkingLot.baseTime, parkingLot.baseFee, parkingLot.isFree)
  const realtime = document.createElement('span'); realtime.className = parkingLot.availableSpaces === 0 ? 'map-info-window__full' : 'map-info-window__available'
  realtime.textContent = parkingLot.availableSpaces == null ? '실시간 정보 없음' : `잔여 ${parkingLot.availableSpaces}면`
  container.append(name, distance, fee, realtime)
  if (parkingLot.stale === true) { const stale = document.createElement('span'); stale.className = 'map-info-window__stale'; stale.textContent = '업데이트 지연'; container.append(stale) }
  const detailButton = document.createElement('button'); detailButton.type = 'button'; detailButton.textContent = '상세보기'; detailButton.addEventListener('click', onShowDetail); container.append(detailButton)
  return container
}

export default function KakaoMap({ mapKey, origin, originType, originName, radius, parkingLots, selectedParkingLotId, onSelect, onShowDetail, onError }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<KakaoMapInstance | null>(null)
  const markersRef = useRef(new Map<number, MarkerRecord>())
  const openedInfoWindowRef = useRef<KakaoInfoWindow | null>(null)
  const clustererRef = useRef<KakaoClusterer | null>(null)
  const selectedMarkerRef = useRef<KakaoMarker | null>(null)

  useEffect(() => {
    let disposed = false
    const cleanups: Array<() => void> = []
    loadKakaoSdk(mapKey).then((maps) => {
      if (disposed || !containerRef.current) return
      const center = new maps.LatLng(origin.latitude, origin.longitude)
      const map = new maps.Map(containerRef.current, { center, level: radius >= 5000 ? 8 : radius >= 2000 ? 6 : radius >= 1000 ? 5 : 4 }); mapRef.current = map
      const resizeObserver = new ResizeObserver(() => { const center = map.getCenter(); map.relayout(); map.setCenter(center) })
      resizeObserver.observe(containerRef.current); cleanups.push(() => resizeObserver.disconnect())
      // Cluster numbers represent lot counts only, never realtime availability.
      // The SDK's default cluster click zooms into that cluster.
      const clusterer = new maps.MarkerClusterer({ map, averageCenter: true, minLevel: 4, gridSize: 60,
        styles: [{ width: '40px', height: '40px', background: '#334155', color: '#fff', textAlign: 'center', lineHeight: '40px', borderRadius: '50%', fontWeight: '700' }],
      }); clustererRef.current = clusterer
      cleanups.push(() => clusterer.clear())
      const originMarker = new maps.Marker({ map, position: center, title: originName ?? (originType === 'CURRENT_LOCATION' ? '내 위치' : '검색 기준 위치') })
      const color = originType === 'SEARCHED_PLACE' ? '#805ad5' : '#1677ff'
      const searchCircle = new maps.Circle({ center, radius, strokeWeight: 2, strokeColor: color, strokeOpacity: 0.8, fillColor: color, fillOpacity: 0.08 })
      searchCircle.setMap(map); cleanups.push(() => originMarker.setMap(null), () => searchCircle.setMap(null))
      parkingLots.forEach((parkingLot) => {
        const position = new maps.LatLng(parkingLot.latitude, parkingLot.longitude)
        const marker = new maps.Marker({ position, title: parkingLot.name })
        const infoWindow = new maps.InfoWindow({ content: createInfoContent(parkingLot, () => onShowDetail(parkingLot)), removable: true })
        markersRef.current.set(parkingLot.id, { marker, infoWindow, position })
        maps.event.addListener(marker, 'click', () => { openedInfoWindowRef.current?.close(); infoWindow.open(map, marker); openedInfoWindowRef.current = infoWindow; onSelect(parkingLot) })
        cleanups.push(() => { marker.setMap(null); infoWindow.close(); markersRef.current.delete(parkingLot.id) })
      })
      clusterer.addMarkers([...markersRef.current.values()].map(({ marker }) => marker))
    }).catch(() => { if (!disposed) onError('지도를 불러오지 못했습니다. Kakao Map 설정을 확인해 주세요.') })
    return () => { disposed = true; cleanups.forEach((cleanup) => cleanup()); mapRef.current = null; openedInfoWindowRef.current = null; clustererRef.current = null; selectedMarkerRef.current = null }
  }, [mapKey, onError, onSelect, onShowDetail, origin, originName, originType, parkingLots, radius])

  useEffect(() => {
    if (selectedMarkerRef.current) {
      selectedMarkerRef.current.setZIndex(0); clustererRef.current?.addMarker(selectedMarkerRef.current); selectedMarkerRef.current = null
    }
    if (selectedParkingLotId == null || !mapRef.current) { openedInfoWindowRef.current?.close(); openedInfoWindowRef.current = null; return }
    const selected = markersRef.current.get(selectedParkingLotId); if (!selected) return
    clustererRef.current?.removeMarker(selected.marker); selected.marker.setMap(mapRef.current); selected.marker.setZIndex(10); selectedMarkerRef.current = selected.marker
    openedInfoWindowRef.current?.close(); mapRef.current.setCenter(selected.position); selected.infoWindow.open(mapRef.current, selected.marker); openedInfoWindowRef.current = selected.infoWindow
  }, [selectedParkingLotId])
  return <div ref={containerRef} className="kakao-map" aria-label="주변 주차장 지도" />
}
