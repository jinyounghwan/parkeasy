import type { Coordinates } from './parking'

export type SearchOriginType = 'CURRENT_LOCATION' | 'SEARCHED_PLACE' | 'TEST_LOCATION'
export type ParkingSort = 'DISTANCE' | 'FEE' | 'AVAILABLE'

export interface PlaceSearchResult extends Coordinates {
  id: string
  name: string
  category: string
  address: string
}

export interface NearbyFilters {
  parkingType: 'PUBLIC' | 'PRIVATE' | null
  free: boolean
  open24Hours: boolean
  realtimeAvailable: boolean
}
