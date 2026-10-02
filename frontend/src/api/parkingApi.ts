import type { Coordinates, NearbyParkingLot, ParkingLotDetail } from '../types/parking'
import type { NearbyFilters } from '../types/search'
import type { ParkingSort } from '../types/search'

export async function getNearbyParkingLots(
  coordinates: Coordinates,
  radius = 2000,
  filters?: NearbyFilters,
  sort: ParkingSort = 'DISTANCE',
  signal?: AbortSignal,
): Promise<NearbyParkingLot[]> {
  const params = new URLSearchParams({
    latitude: String(coordinates.latitude),
    longitude: String(coordinates.longitude),
    radius: String(radius),
	limit: '100',
	sort,
  })
  if (filters?.parkingType) params.set('parkingType', filters.parkingType)
  if (filters?.free) params.set('free', 'true')
  if (filters?.open24Hours) params.set('open24Hours', 'true')
  if (filters?.realtimeAvailable) params.set('realtimeAvailable', 'true')
  const response = await fetch(`/api/parking-lots/nearby?${params}`, { signal })
  if (!response.ok) throw new Error(`주변 주차장 조회에 실패했습니다. (${response.status})`)
  return response.json() as Promise<NearbyParkingLot[]>
}

export async function getParkingLotDetail(id: number, signal?: AbortSignal): Promise<ParkingLotDetail> {
  const response = await fetch(`/api/parking-lots/${id}`, { signal })
  if (!response.ok) throw new Error(`주차장 상세 조회에 실패했습니다. (${response.status})`)
  return response.json() as Promise<ParkingLotDetail>
}
