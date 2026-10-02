export type ParkingType = 'PUBLIC' | 'PRIVATE'

export interface NearbyParkingLot {
  id: number
  name: string
  address: string | null
  latitude: number
  longitude: number
  distanceMeters: number
  parkingType: ParkingType | null
  totalSpaces: number | null
  baseTime: number | null
  baseFee: number | null
  isFree: boolean | null
  is24Hours: boolean | null
  availableSpaces: number | null
  stale: boolean | null
}

export interface ParkingOperation {
  baseFreeTime: number | null
  baseTime: number | null
  baseFee: number | null
  additionalTime: number | null
  additionalFee: number | null
  dailyMaxFee: number | null
  monthlyFee: number | null
  mondayOpenTime: string | null
  mondayCloseTime: string | null
  tuesdayOpenTime: string | null
  tuesdayCloseTime: string | null
  wednesdayOpenTime: string | null
  wednesdayCloseTime: string | null
  thursdayOpenTime: string | null
  thursdayCloseTime: string | null
  fridayOpenTime: string | null
  fridayCloseTime: string | null
  saturdayOpenTime: string | null
  saturdayCloseTime: string | null
  sundayOpenTime: string | null
  sundayCloseTime: string | null
  holidayOpenTime: string | null
  holidayCloseTime: string | null
}

export interface ParkingRealtime {
  totalSpaces: number | null
  availableSpaces: number | null
  observedAt: string | null
  receivedAt: string
  stale: boolean
}

export interface ParkingLotDetail {
  id: number
  name: string
  address: string | null
  latitude: number
  longitude: number
  parkingType: ParkingType | null
  totalSpaces: number | null
  baseTime: number | null
  baseFee: number | null
  isFree: boolean | null
  is24Hours: boolean | null
  additionalTime: number | null
  additionalFee: number | null
  dailyMaxFee: number | null
  weekdayOpenTime: string | null
  weekdayCloseTime: string | null
  dataSource: 'PUBLIC_DATA_PORTAL' | 'MANUAL' | null
  operation: ParkingOperation | null
  realtime: ParkingRealtime | null
}

export interface Coordinates {
  latitude: number
  longitude: number
}
