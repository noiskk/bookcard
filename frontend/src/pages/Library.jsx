import { useState, useEffect, useCallback } from 'react'
import { Library as LibraryIcon, Loader2, BookOpen, Trash2, Search, X, Heart, Globe, User } from 'lucide-react'
import BookViewer from '../components/BookViewer'
import bookApi from '../api/bookApi'
import authApi from '../api/authApi'

function Library() {
  const [books, setBooks] = useState([])
  const [myBooks, setMyBooks] = useState([])
  const [searchQuery, setSearchQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [selectedBook, setSelectedBook] = useState(null)
  const [error, setError] = useState(null)
  const [viewMode, setViewMode] = useState('all') // 'all' | 'mine'

  // 현재 로그인한 사용자 이메일 (JWT 디코딩)
  const currentUserEmail = authApi.getCurrentUserEmail()
  const isLoggedIn = authApi.isLoggedIn()

  const fetchAllBooks = useCallback(async () => {
    try {
      setIsLoading(true)
      setError(null)
      const data = await bookApi.getAllBooks()
      setBooks(data)
    } catch (err) {
      setError('보관함을 불러오는데 실패했습니다. 백엔드 서버가 실행 중인지 확인해주세요.')
      console.error(err)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const fetchMyBooks = useCallback(async () => {
    if (!isLoggedIn) {
      setMyBooks([])
      return
    }
    try {
      setIsLoading(true)
      setError(null)
      const data = await bookApi.getMyBooks(0, 100)
      setMyBooks(data.content)
    } catch (err) {
      setError('내 북카드를 불러오는데 실패했습니다.')
      console.error(err)
    } finally {
      setIsLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    if (viewMode === 'all') {
      fetchAllBooks()
    } else {
      fetchMyBooks()
    }
  }, [viewMode, fetchAllBooks, fetchMyBooks])

  // 현재 뷰모드에 따라 표시할 책 목록
  const currentBooks = viewMode === 'mine' ? myBooks : books

  // Filter books based on search
  const filteredBooks = currentBooks.filter((book) => {
    if (!searchQuery.trim()) return true
    const query = searchQuery.toLowerCase()
    return (
      book.title?.toLowerCase().includes(query) ||
      book.author?.toLowerCase().includes(query)
    )
  })

  // Stats for current view
  const displayBooks = currentBooks

  const handleDelete = async (id, e) => {
    e.stopPropagation()
    if (!confirm('이 북카드를 삭제하시겠습니까?')) return
    try {
      await bookApi.deleteBook(id)
      setBooks((prev) => prev.filter((b) => b.id !== id))
      setMyBooks((prev) => prev.filter((b) => b.id !== id))
    } catch (err) {
      setError('북카드 삭제에 실패했습니다.')
      console.error(err)
    }
  }

  const getCoverImage = (book) => {
    return book.generatedImage || book.originalImage || book.coverImage
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-wood/10 rounded-xl">
            <LibraryIcon className="w-6 h-6 text-wood" />
          </div>
          <div>
            <h1 className="font-serif text-2xl font-bold text-stone-800">
              보관함
            </h1>
            <p className="text-sm text-stone-500">
              {viewMode === 'mine'
                ? `내가 만든 ${displayBooks.length}개`
                : `전체 ${books.length}개의 북카드`}
            </p>
          </div>
        </div>

        {/* View Mode Tabs */}
        <div className="flex bg-stone-100 rounded-lg p-1">
          <button
            onClick={() => setViewMode('all')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
              viewMode === 'all'
                ? 'bg-white text-stone-800 shadow-sm'
                : 'text-stone-500 hover:text-stone-700'
            }`}
          >
            <Globe className="w-4 h-4" />
            전체
          </button>
          <button
            onClick={() => {
              if (!isLoggedIn) {
                setError('내 북카드를 보려면 로그인이 필요합니다.')
                return
              }
              setViewMode('mine')
            }}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
              viewMode === 'mine'
                ? 'bg-white text-stone-800 shadow-sm'
                : 'text-stone-500 hover:text-stone-700'
            }`}
          >
            <User className="w-4 h-4" />
            내 북카드
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      {displayBooks.length > 0 && (
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-white rounded-xl p-4 border border-stone-200 shadow-sm">
            <div className="flex items-center gap-2 text-stone-500 mb-1">
              <BookOpen className="w-4 h-4" />
              <span className="text-xs">{viewMode === 'mine' ? '내 북카드' : '전체 북카드'}</span>
            </div>
            <p className="text-2xl font-bold text-stone-800">{displayBooks.length}</p>
          </div>
          <div className="bg-white rounded-xl p-4 border border-stone-200 shadow-sm">
            <div className="flex items-center gap-2 text-stone-500 mb-1">
              <Heart className="w-4 h-4" />
              <span className="text-xs">총 좋아요</span>
            </div>
            <p className="text-2xl font-bold text-stone-800">
              {displayBooks.reduce((sum, b) => sum + (b.likeCount || 0), 0)}
            </p>
          </div>
        </div>
      )}

      {/* Search */}
      <div className="relative max-w-md">
        <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
          <Search className="w-5 h-5 text-stone-400" />
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="보관함 검색..."
          className="input-field pl-12 pr-10"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute inset-y-0 right-0 pr-4 flex items-center text-stone-400 hover:text-stone-600"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* Grid View */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-8 h-8 animate-spin text-wood" />
        </div>
      ) : filteredBooks.length > 0 ? (
        <div className="grid gap-6 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
          {filteredBooks.map((book) => (
            <div
              key={book.id}
              onClick={() => setSelectedBook(book)}
              className="group cursor-pointer"
            >
              {/* Book Cover */}
              <div className="relative aspect-[2/3] rounded-lg overflow-hidden shadow-md group-hover:shadow-xl transition-shadow duration-300 bg-stone-200">
                {getCoverImage(book) ? (
                  <img
                    src={getCoverImage(book)}
                    alt={book.title}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <BookOpen className="w-12 h-12 text-stone-400" />
                  </div>
                )}

                {/* AI Generated badge */}
                {book.generatedImage && (
                  <div className="absolute top-2 left-2 px-2 py-1 bg-wood/90 text-white text-xs rounded-full">
                    AI 아트
                  </div>
                )}

                {/* Overlay with delete button - 본인이 만든 북카드만 표시 */}
                {book.createdBy === currentUserEmail && currentUserEmail && (
                  <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-colors duration-300">
                    <button
                      onClick={(e) => handleDelete(book.id, e)}
                      className="absolute top-2 right-2 p-2 bg-white/90 rounded-full opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:bg-red-50"
                    >
                      <Trash2 className="w-4 h-4 text-red-500" />
                    </button>
                  </div>
                )}
              </div>

              {/* Book Info */}
              <div className="mt-3">
                <h3 className="font-serif font-semibold text-stone-800 truncate">
                  {book.title}
                </h3>
                <p className="text-sm text-stone-500 truncate">{book.author}</p>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-center py-16">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-stone-200 rounded-full mb-4">
            {viewMode === 'mine' ? (
              <User className="w-8 h-8 text-stone-400" />
            ) : (
              <LibraryIcon className="w-8 h-8 text-stone-400" />
            )}
          </div>
          <h3 className="font-serif text-xl text-stone-700 mb-2">
            {searchQuery
              ? '검색 결과가 없습니다'
              : viewMode === 'mine'
              ? '내가 만든 북카드가 없습니다'
              : '보관함이 비어있습니다'}
          </h3>
          <p className="text-stone-500">
            {searchQuery
              ? '다른 검색어를 시도해보세요'
              : '홈에서 북카드를 생성해보세요'}
          </p>
        </div>
      )}

      {/* Book Viewer */}
      {selectedBook && (
        <BookViewer book={selectedBook} onClose={() => setSelectedBook(null)} />
      )}
    </div>
  )
}

export default Library
