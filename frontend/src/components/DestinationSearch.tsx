import { useRef, useState, type FormEvent } from 'react'
import { loadKakaoSdk } from '../kakaoSdk'
import type { PlaceSearchResult } from '../types/search'

interface Props { mapKey: string; destinationName: string | null; onSelect: (place: PlaceSearchResult) => void }

export default function DestinationSearch({ mapKey, destinationName, onSelect }: Props) {
  const requestSequence = useRef(0)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const search = async (event: FormEvent) => {
    event.preventDefault()
    const keyword = query.trim()
    if (keyword.length < 2) { setMessage('두 글자 이상 입력해 주세요.'); setResults([]); return }
    const sequence = ++requestSequence.current
    setLoading(true); setMessage(null)
    try {
      const maps = await loadKakaoSdk(mapKey)
      const places = new maps.services.Places()
      places.keywordSearch(keyword, (documents, status) => {
        if (sequence !== requestSequence.current) return
        setLoading(false)
        if (status === maps.services.Status.ZERO_RESULT) { setResults([]); setMessage('검색 결과가 없습니다.'); return }
        if (status !== maps.services.Status.OK) { setResults([]); setMessage('장소 검색에 실패했습니다.'); return }
        setResults(documents.slice(0, 7).map((place) => ({
          id: place.id, name: place.place_name, category: place.category_name,
          address: place.road_address_name || place.address_name,
          latitude: Number(place.y), longitude: Number(place.x),
        })))
      }, { size: 7 })
    } catch { if (sequence === requestSequence.current) { setLoading(false); setMessage('장소 검색을 준비하지 못했습니다.') } }
  }

  return <div className="destination-search">
    <form onSubmit={search} className="destination-search__form" role="search">
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="목적지를 검색하세요" aria-label="목적지 검색" />
      <button type="submit" disabled={loading || !mapKey}>{loading ? '검색 중' : '검색'}</button>
    </form>
    {destinationName && <span className="destination-search__selected">목적지: {destinationName}</span>}
    {(results.length > 0 || message) && <div className="place-results" role="listbox">
      {message && <p>{message}</p>}
      {results.map((place) => <button key={place.id} type="button" role="option" onClick={() => { onSelect(place); setResults([]); setMessage(null); setQuery(place.name) }}>
        <strong>{place.name}</strong><span>{place.category || '장소'}</span><small>{place.address || '주소 정보 없음'}</small>
      </button>)}
    </div>}
  </div>
}
