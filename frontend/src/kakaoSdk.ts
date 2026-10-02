export interface KakaoLatLng {}
export interface KakaoMapInstance { setCenter: (position: KakaoLatLng) => void; getCenter: () => KakaoLatLng; relayout: () => void }
export interface KakaoMapObject { setMap: (map: KakaoMapInstance | null) => void }
export interface KakaoMarker extends KakaoMapObject { setZIndex: (value: number) => void }
export interface KakaoClusterer { addMarkers: (markers: KakaoMarker[]) => void; addMarker: (marker: KakaoMarker) => void; removeMarker: (marker: KakaoMarker) => void; clear: () => void }
export interface KakaoInfoWindow { open: (map: KakaoMapInstance, marker: object) => void; close: () => void }
export interface KakaoPlaceDocument {
  id: string; place_name: string; category_name: string; address_name: string
  road_address_name: string; x: string; y: string
}
export interface KakaoMapsApi {
  load: (callback: () => void) => void
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMapInstance
  Marker: new (options: { map?: KakaoMapInstance; position: KakaoLatLng; title?: string }) => KakaoMarker
  MarkerClusterer: new (options: { map: KakaoMapInstance; averageCenter: boolean; minLevel: number; gridSize: number; styles: Array<Record<string, string>> }) => KakaoClusterer
  Circle: new (options: { center: KakaoLatLng; radius: number; strokeWeight: number; strokeColor: string; strokeOpacity: number; fillColor: string; fillOpacity: number }) => KakaoMapObject
  InfoWindow: new (options: { content: HTMLElement; removable: boolean }) => KakaoInfoWindow
  services: {
    Places: new () => { keywordSearch: (keyword: string, callback: (results: KakaoPlaceDocument[], status: string) => void, options?: { size?: number }) => void }
    Status: { OK: string; ZERO_RESULT: string }
  }
  event: { addListener: (target: object, event: string, callback: () => void) => void }
}

declare global { interface Window { kakao?: { maps: KakaoMapsApi } } }

let sdkPromise: Promise<KakaoMapsApi> | null = null

export function loadKakaoSdk(mapKey: string): Promise<KakaoMapsApi> {
  if (window.kakao?.maps) return new Promise((resolve) => window.kakao!.maps.load(() => resolve(window.kakao!.maps)))
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(mapKey)}&autoload=false&libraries=services,clusterer`
    script.async = true
    script.onload = () => {
      if (!window.kakao?.maps) return reject(new Error('Kakao Maps SDK 초기화 실패'))
      window.kakao.maps.load(() => resolve(window.kakao!.maps))
    }
    script.onerror = () => reject(new Error('Kakao Maps SDK 로드 실패'))
    document.head.appendChild(script)
  })
  return sdkPromise
}
