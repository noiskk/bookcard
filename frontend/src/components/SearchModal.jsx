import { useState, useEffect, useRef } from 'react'
import { Search, Sparkles, BookOpen, Loader2, X } from 'lucide-react'
import bookApi from '../api/bookApi'

function SearchModal({ isOpen, onClose, onSelectBook }) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState([])
  const [isSearching, setIsSearching] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [searchStart, setSearchStart] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState(null)

  const inputRef = useRef(null)

  // Auto-focus input when modal opens
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50)
    } else {
      // Reset state when closed
      setSearchQuery('')
      setSearchResults([])
      setError(null)
      setSearchStart(1)
      setHasMore(false)
    }
  }, [isOpen])

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  const handleSearch = async (e) => {
    e.preventDefault()
    if (!searchQuery.trim()) return

    try {
      setIsSearching(true)
      setError(null)
      setSearchResults([])
      setSearchStart(1)
      const results = await bookApi.searchBooks(searchQuery, 1)
      setSearchResults(results)
      setHasMore(results.length === 20)
      if (results.length === 0) {
        setError('검색 결과가 없습니다. 다른 검색어를 시도해보세요.')
      }
    } catch (err) {
      setError('책 검색에 실패했습니다. 백엔드 서버가 실행 중인지 확인해주세요.')
      console.error(err)
    } finally {
      setIsSearching(false)
    }
  }

  const handleLoadMore = async () => {
    if (!searchQuery.trim() || isLoadingMore) return

    try {
      setIsLoadingMore(true)
      const nextStart = searchStart + 20
      const results = await bookApi.searchBooks(searchQuery, nextStart)

      if (results.length > 0) {
        setSearchResults((prev) => [...prev, ...results])
        setSearchStart(nextStart)
        setHasMore(results.length === 20)
      } else {
        setHasMore(false)
      }
    } catch (err) {
      console.error(err)
    } finally {
      setIsLoadingMore(false)
    }
  }

  const clearSearch = () => {
    setSearchQuery('')
    setSearchResults([])
    setError(null)
    setSearchStart(1)
    setHasMore(false)
    inputRef.current?.focus()
  }

  const handleSelectBook = (book) => {
    onSelectBook(book)
    onClose()
  }

  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center pt-16 px-4 pb-4 bg-black/50 backdrop-blur-sm"
      onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl flex flex-col max-h-[80vh]">
        {/* Header */}
        <div className="p-5 border-b border-stone-100">
          <div className="flex items-center gap-3 mb-4">
            <h2 className="font-serif text-xl font-bold text-stone-800 flex-1">
              책 검색
            </h2>
            <button
              onClick={onClose}
              className="p-2 text-stone-400 hover:text-stone-600 hover:bg-stone-100 rounded-lg transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Search Form */}
          <form onSubmit={handleSearch}>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                <Search className="w-5 h-5 text-stone-400" />
              </div>
              <input
                ref={inputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="책 제목이나 저자를 입력하세요..."
                className="w-full px-4 py-3.5 pl-12 pr-28 bg-stone-50 border-2 border-stone-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-wood focus:border-transparent placeholder-stone-400 text-stone-800 text-base transition-all"
              />
              <div className="absolute inset-y-0 right-0 flex items-center gap-1 pr-2">
                {searchQuery && (
                  <button
                    type="button"
                    onClick={clearSearch}
                    className="p-2 text-stone-400 hover:text-stone-600 transition-colors"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
                <button
                  type="submit"
                  disabled={isSearching || !searchQuery.trim()}
                  className="px-4 py-2 bg-wood text-white rounded-lg hover:bg-wood-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium text-sm"
                >
                  {isSearching ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    '검색'
                  )}
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* Results Area */}
        <div className="flex-1 overflow-y-auto p-5">
          {/* Error Message */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl mb-4 text-sm">
              {error}
            </div>
          )}

          {/* Searching indicator */}
          {isSearching && (
            <div className="flex items-center justify-center py-12 text-stone-400">
              <Loader2 className="w-6 h-6 animate-spin mr-2" />
              <span>검색 중...</span>
            </div>
          )}

          {/* Search Results Grid */}
          {!isSearching && searchResults.length > 0 && (
            <>
              <div className="flex items-center justify-between mb-4">
                <p className="text-sm text-stone-500">
                  검색 결과 <span className="font-semibold text-stone-700">{searchResults.length}</span>건 · 책을 선택하면 북카드를 생성합니다
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {searchResults.map((book, index) => (
                  <div
                    key={`${book.isbn}-${index}`}
                    className="bg-stone-50 rounded-xl border border-stone-200 p-3 hover:shadow-md hover:border-wood/30 transition-all duration-200 group cursor-pointer"
                    onClick={() => handleSelectBook(book)}
                  >
                    <div className="flex gap-3">
                      {/* Book Cover */}
                      <div className="flex-shrink-0 w-16 h-22 rounded-lg overflow-hidden bg-stone-200 shadow-sm" style={{ height: '88px' }}>
                        {book.image ? (
                          <img
                            src={book.image}
                            alt={book.title}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center">
                            <BookOpen className="w-5 h-5 text-stone-400" />
                          </div>
                        )}
                      </div>

                      {/* Book Info */}
                      <div className="flex-1 min-w-0 flex flex-col">
                        <h3 className="font-serif font-semibold text-stone-800 line-clamp-2 text-sm leading-tight mb-1">
                          {book.title}
                        </h3>
                        <p className="text-xs text-stone-500 truncate">{book.author}</p>
                        {book.publisher && (
                          <p className="text-xs text-stone-400 truncate">{book.publisher}</p>
                        )}

                        {/* Generate hint */}
                        <div className="mt-auto pt-2 flex items-center gap-1 text-wood/70 group-hover:text-wood transition-colors">
                          <Sparkles className="w-3 h-3" />
                          <span className="text-xs font-medium">북카드 생성</span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* Load More */}
              {hasMore && (
                <div className="mt-5 text-center">
                  <button
                    onClick={handleLoadMore}
                    disabled={isLoadingMore}
                    className="px-6 py-2.5 bg-stone-100 text-stone-700 rounded-xl font-medium hover:bg-stone-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors inline-flex items-center gap-2 text-sm"
                  >
                    {isLoadingMore ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        불러오는 중...
                      </>
                    ) : (
                      <>
                        <Search className="w-4 h-4" />
                        더 보기
                      </>
                    )}
                  </button>
                  <p className="text-xs text-stone-400 mt-1.5">현재 {searchResults.length}개 표시 중</p>
                </div>
              )}
            </>
          )}

          {/* Empty state - before search */}
          {!isSearching && searchResults.length === 0 && !error && (
            <div className="flex flex-col items-center justify-center py-16 text-stone-400">
              <div className="w-16 h-16 rounded-full bg-stone-100 flex items-center justify-center mb-4">
                <Search className="w-7 h-7 text-stone-300" />
              </div>
              <p className="text-stone-500 font-medium mb-1">책을 검색해보세요</p>
              <p className="text-sm text-stone-400">제목, 저자, ISBN으로 검색할 수 있습니다</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default SearchModal
