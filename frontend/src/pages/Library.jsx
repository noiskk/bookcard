import { useState, useEffect, useCallback } from 'react'
import { Library as LibraryIcon, Loader2, BookOpen, Trash2, Search, X, Heart, Globe, User, ChevronLeft, ChevronRight, AlertTriangle } from 'lucide-react'
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

  // 페이지네이션 상태
  const [currentPage, setCurrentPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const PAGE_SIZE = 12

  // 삭제 확인 모달 상태
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const currentUserEmail = authApi.getCurrentUserEmail()
  const isLoggedIn = authApi.isLoggedIn()

  const fetchPagedBooks = useCallback(async (page = 0) => {
    try {
      setIsLoading(true)
      setError(null)
      const data = await bookApi.getPagedBooks(page, PAGE_SIZE)
      setBooks(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
      setCurrentPage(page)
    } catch (err) {
      setError('보관함을 불러오는데 실패했습니다. 백엔드 서버가 실행 중인지 확인해주세요.')
      console.error(err)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const fetchMyBooks = useCallback(async (page = 0) => {
    if (!isLoggedIn) {
      setMyBooks([])
      return
    }
    try {
      setIsLoading(true)
      setError(null)
      const data = await bookApi.getMyBooks(page, PAGE_SIZE)
      setMyBooks(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
      setCurrentPage(page)
    } catch (err) {
      setError('내 북카드를 불러오는데 실패했습니다.')
      console.error(err)
    } finally {
      setIsLoading(false)
    }
  }, [isLoggedIn])

  useEffect(() => {
    setCurrentPage(0)
    if (viewMode === 'all') {
      fetchPagedBooks(0)
    } else {
      fetchMyBooks(0)
    }
  }, [viewMode, fetchPagedBooks, fetchMyBooks])

  const handlePageChange = (page) => {
    if (page < 0 || page >= totalPages) return
    window.scrollTo({ top: 0, behavior: 'smooth' })
    if (viewMode === 'all') {
      fetchPagedBooks(page)
    } else {
      fetchMyBooks(page)
    }
  }

  const currentBooks = viewMode === 'mine' ? myBooks : books

  const filteredBooks = currentBooks.filter((book) => {
    if (!searchQuery.trim()) return true
    const query = searchQuery.toLowerCase()
    return (
      book.title?.toLowerCase().includes(query) ||
      book.author?.toLowerCase().includes(query)
    )
  })

  // 삭제 확인 모달 열기
  const handleDeleteClick = (book, e) => {
    e.stopPropagation()
    setDeleteTarget(book)
  }

  // 삭제 실행
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return
    setIsDeleting(true)
    try {
      await bookApi.deleteBook(deleteTarget.id)
      setBooks((prev) => prev.filter((b) => b.id !== deleteTarget.id))
      setMyBooks((prev) => prev.filter((b) => b.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch (err) {
      setError('북카드 삭제에 실패했습니다.')
      console.error(err)
    } finally {
      setIsDeleting(false)
    }
  }

  const getCoverImage = (book) => {
    return book.generatedImage || book.originalImage || book.coverImage
  }

  // 페이지 번호 목록 생성
  const getPageNumbers = () => {
    const pages = []
    const maxVisible = 5
    let start = Math.max(0, currentPage - Math.floor(maxVisible / 2))
    let end = Math.min(totalPages, start + maxVisible)
    if (end - start < maxVisible) {
      start = Math.max(0, end - maxVisible)
    }
    for (let i = start; i < end; i++) {
      pages.push(i)
    }
    return pages
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
                ? `내가 만든 ${totalElements}개`
                : `전체 ${totalElements}개의 북카드`}
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
      {currentBooks.length > 0 && (
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-white rounded-xl p-4 border border-stone-200 shadow-sm">
            <div className="flex items-center gap-2 text-stone-500 mb-1">
              <BookOpen className="w-4 h-4" />
              <span className="text-xs">{viewMode === 'mine' ? '내 북카드' : '전체 북카드'}</span>
            </div>
            <p className="text-2xl font-bold text-stone-800">{totalElements}</p>
          </div>
          <div className="bg-white rounded-xl p-4 border border-stone-200 shadow-sm">
            <div className="flex items-center gap-2 text-stone-500 mb-1">
              <Heart className="w-4 h-4" />
              <span className="text-xs">총 좋아요</span>
            </div>
            <p className="text-2xl font-bold text-stone-800">
              {currentBooks.reduce((sum, b) => sum + (b.likeCount || 0), 0)}
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
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-3 text-red-400 hover:text-red-600">
            <X className="w-4 h-4" />
          </button>
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

                {/* Overlay with delete button */}
                {book.createdBy === currentUserEmail && currentUserEmail && (
                  <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-colors duration-300">
                    <button
                      onClick={(e) => handleDeleteClick(book, e)}
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

      {/* Pagination */}
      {totalPages > 1 && !searchQuery && (
        <div className="flex items-center justify-center gap-1 pt-4">
          <button
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 0}
            className="p-2 rounded-lg text-stone-500 hover:bg-stone-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>

          {getPageNumbers().map((page) => (
            <button
              key={page}
              onClick={() => handlePageChange(page)}
              className={`w-10 h-10 rounded-lg text-sm font-medium transition-colors ${
                page === currentPage
                  ? 'bg-wood text-white'
                  : 'text-stone-600 hover:bg-stone-200'
              }`}
            >
              {page + 1}
            </button>
          ))}

          <button
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage >= totalPages - 1}
            className="p-2 rounded-lg text-stone-500 hover:bg-stone-200 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Book Viewer */}
      {selectedBook && (
        <BookViewer book={selectedBook} onClose={() => setSelectedBook(null)} />
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/50 backdrop-blur-sm"
          onClick={(e) => { if (e.target === e.currentTarget) setDeleteTarget(null) }}
        >
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-red-100 rounded-full">
                <AlertTriangle className="w-5 h-5 text-red-500" />
              </div>
              <h3 className="font-serif text-lg font-bold text-stone-800">북카드 삭제</h3>
            </div>
            <p className="text-stone-600 mb-2">
              <span className="font-medium text-stone-800">"{deleteTarget.title}"</span>을(를) 삭제하시겠습니까?
            </p>
            <p className="text-sm text-stone-400 mb-6">삭제된 북카드는 복구할 수 없습니다.</p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={isDeleting}
                className="flex-1 py-2.5 bg-stone-100 text-stone-700 rounded-xl font-medium hover:bg-stone-200 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDeleteConfirm}
                disabled={isDeleting}
                className="flex-1 py-2.5 bg-red-500 text-white rounded-xl font-medium hover:bg-red-600 transition-colors flex items-center justify-center gap-2"
              >
                {isDeleting ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Trash2 className="w-4 h-4" />
                )}
                삭제
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default Library
