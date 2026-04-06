import { useState, useEffect } from 'react'
import { Search, Sparkles, BookOpen, Loader2, X, ArrowRight, TrendingUp, HelpCircle } from 'lucide-react'
import BookViewer from '../components/BookViewer'
import SearchModal from '../components/SearchModal'
import bookApi from '../api/bookApi'
import myBooks from '../utils/myBooks'

function Main() {
  const [isGenerating, setIsGenerating] = useState(false)
  const [selectedBook, setSelectedBook] = useState(null)
  const [generatedBook, setGeneratedBook] = useState(null)
  const [error, setError] = useState(null)
  const [progress, setProgress] = useState({ step: 0, totalSteps: 5, message: '' })
  const [recommendations, setRecommendations] = useState([])
  const [isLoadingRecs, setIsLoadingRecs] = useState(true)
  const [activeCategory, setActiveCategory] = useState(null)
  const [showSearchModal, setShowSearchModal] = useState(false)
  const [showHowTo, setShowHowTo] = useState(false)
  const [heroBooks, setHeroBooks] = useState([])

  // Fetch recommendations on mount
  useEffect(() => {
    bookApi.getRecommendations()
      .then((data) => {
        setRecommendations(data)
        if (data.length > 0) setActiveCategory(data[0].category)
      })
      .catch(() => {
        // Non-critical: silently ignore errors
      })
      .finally(() => {
        setIsLoadingRecs(false)
      })
  }, [])

  // Fetch hero book images from library
  useEffect(() => {
    bookApi.getAllBooks()
      .then((books) => {
        const withImage = books.filter((b) => b.generatedImage)
        setHeroBooks(withImage.slice(0, 3))
      })
      .catch(() => {
        // Non-critical: fall back to gradient placeholders
      })
  }, [])

  // Generate book card when user selects a book (with SSE progress)
  const handleSelectBook = (book) => {
    setSelectedBook(book)
    setIsGenerating(true)
    setError(null)
    setProgress({ step: 0, totalSteps: 5, message: '생성을 시작합니다...' })

    bookApi.generateBookWithProgress(
      book,
      // onProgress
      (progressData) => {
        setProgress({
          step: progressData.step,
          totalSteps: progressData.totalSteps || 5,
          message: progressData.message
        })
      },
      // onComplete
      (generatedBook) => {
        setIsGenerating(false)
        setGeneratedBook(generatedBook)
        setProgress({ step: 0, totalSteps: 5, message: '' })
        // 내가 만든 북카드로 로컬스토리지에 저장
        if (generatedBook.id) {
          myBooks.add(generatedBook.id)
        }
      },
      // onError
      async (err) => {
        setIsGenerating(false)
        setSelectedBook(null)
        setProgress({ step: 0, totalSteps: 5, message: '' })
        console.error(err)

        // 중복 북카드 에러인지 확인
        const duplicateMatch = err?.message?.match(/이미 생성된 북카드가 있습니다 \(ID: (\d+)\)/)
        if (duplicateMatch) {
          try {
            const existingBook = await bookApi.getBookById(Number(duplicateMatch[1]))
            setGeneratedBook(existingBook)
          } catch {
            setError('이미 생성된 북카드가 있습니다. 보관함에서 확인하세요.')
          }
        } else {
          setError('북카드 생성에 실패했습니다. 다시 시도해주세요.')
        }
      }
    )
  }

  // Close generation modal
  const handleCloseGenerate = () => {
    setSelectedBook(null)
    setIsGenerating(false)
  }

  // Close viewer
  const handleCloseViewer = () => {
    setGeneratedBook(null)
    setSelectedBook(null)
  }

  // Hero floating card configs
  const cardConfigs = [
    { wrapperClass: 'absolute w-40 h-56 rounded-xl shadow-2xl transform -rotate-12 translate-x-16 -translate-y-4 opacity-60 animate-float-slow', fallbackClass: 'bg-gradient-to-br from-rose-400 to-pink-500' },
    { wrapperClass: 'absolute w-44 h-60 rounded-xl shadow-2xl transform rotate-6 -translate-x-8 translate-y-4 opacity-80 animate-float-medium', fallbackClass: 'bg-gradient-to-br from-blue-400 to-indigo-500' },
    { wrapperClass: 'absolute w-48 h-64 rounded-xl shadow-2xl transform -rotate-3 animate-float-fast', fallbackClass: 'bg-gradient-to-br from-amber-400 to-orange-500' },
  ]

  return (
    <div className="space-y-6">
      {/* Hero Section */}
      <div className="relative overflow-hidden bg-gradient-to-br from-stone-50 via-white to-amber-50 -mx-6 -mt-8 px-6 pt-8 pb-16 rounded-b-3xl">
        {/* Background decoration */}
        <div className="absolute inset-0 overflow-hidden">
          <div className="absolute -top-40 -right-40 w-80 h-80 bg-wood/5 rounded-full blur-3xl" />
          <div className="absolute -bottom-40 -left-40 w-80 h-80 bg-amber-200/20 rounded-full blur-3xl" />
        </div>

        <div className="relative max-w-6xl mx-auto">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left: Text Content */}
            <div className="text-center lg:text-left">
              <div className="inline-flex items-center gap-2 px-4 py-2 bg-wood/10 rounded-full text-wood text-sm font-medium mb-6">
                <Sparkles className="w-4 h-4" />
                AI 기반 북카드 생성 서비스
              </div>

              <h1 className="font-serif text-4xl lg:text-5xl font-bold text-stone-800 mb-6 leading-tight">
                책을<br />
                나만의 <span className="text-wood">카드</span>로
              </h1>

              <p className="text-lg text-stone-600 mb-8 max-w-md mx-auto lg:mx-0">
                좋아하는 책을 검색하세요.<br />
                AI가 책을 요약하고 생생한 아트 커버를 만들어드립니다.<br />
                나만의 특별한 북카드를 소장하세요.
              </p>

              <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start flex-wrap">
                <button
                  onClick={() => setShowSearchModal(true)}
                  className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-wood text-white rounded-xl font-medium hover:bg-wood-dark transition-colors shadow-lg shadow-wood/25"
                >
                  북카드 만들기
                  <ArrowRight className="w-5 h-5" />
                </button>
                <a
                  href="/library"
                  className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-white text-stone-700 rounded-xl font-medium hover:bg-stone-50 transition-colors border border-stone-200"
                >
                  보관함 둘러보기
                </a>
                <button
                  onClick={() => setShowHowTo(true)}
                  className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-stone-100/80 text-stone-600 rounded-full text-sm font-medium hover:bg-stone-200/80 transition-colors border border-stone-200/60"
                >
                  <HelpCircle className="w-4 h-4" />
                  이용 방법 보기
                </button>
              </div>
            </div>

            {/* Right: Floating Book Cards Preview */}
            <div className="relative h-80 lg:h-96 hidden md:block">
              <div className="absolute inset-0 flex items-center justify-center">
                {cardConfigs.map((cfg, i) => {
                  const book = heroBooks[i]
                  return (
                    <div key={i} className={cfg.wrapperClass}>
                      {book?.generatedImage ? (
                        <img
                          src={book.generatedImage}
                          alt={book.title || '북카드'}
                          className="w-full h-full object-cover rounded-xl"
                        />
                      ) : (
                        <div className={`w-full h-full rounded-xl ${cfg.fallbackClass}`}>
                          <div className="absolute inset-0 rounded-xl bg-black/10" />
                          <div className="absolute bottom-4 left-4 right-4">
                            <div className="h-2 bg-white/30 rounded mb-2" />
                            <div className="h-2 bg-white/20 rounded w-2/3" />
                          </div>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>

              {/* Decorative elements */}
              <div className="absolute top-8 right-8 w-8 h-8 text-wood/30 animate-pulse">
                <Sparkles className="w-full h-full" />
              </div>
              <div className="absolute bottom-12 left-8 w-6 h-6 text-amber-400/40 animate-pulse delay-300">
                <BookOpen className="w-full h-full" />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Book Recommendations Section */}
      {(isLoadingRecs || recommendations.length > 0) && (
        <div className="py-8 px-4">
          <div className="max-w-6xl mx-auto">
            {/* Section Header */}
            <div className="flex items-center gap-2 mb-6">
              <TrendingUp className="w-5 h-5 text-wood" />
              <h2 className="font-serif text-2xl font-bold text-stone-800">
                이런 책은 어떠세요?
              </h2>
            </div>

            {isLoadingRecs ? (
              /* Loading Skeleton */
              <div>
                <div className="flex gap-2 mb-4">
                  {[1, 2, 3, 4].map((i) => (
                    <div key={i} className="h-8 w-20 bg-stone-200 rounded-full animate-pulse" />
                  ))}
                </div>
                <div className="flex gap-4 overflow-hidden">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <div key={i} className="flex-shrink-0 w-36 sm:w-40">
                      <div className="w-full h-52 bg-stone-200 rounded-xl animate-pulse mb-3" />
                      <div className="h-4 bg-stone-200 rounded animate-pulse mb-1.5" />
                      <div className="h-3 bg-stone-100 rounded animate-pulse w-3/4" />
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <>
                {/* Category Tabs */}
                <div className="flex gap-2 mb-5 flex-wrap">
                  {recommendations.map((cat) => (
                    <button
                      key={cat.category}
                      onClick={() => setActiveCategory(cat.category)}
                      className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                        activeCategory === cat.category
                          ? 'bg-wood text-white shadow-sm shadow-wood/30'
                          : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
                      }`}
                    >
                      {cat.displayName}
                    </button>
                  ))}
                </div>

                {/* Book Cards Row */}
                {recommendations
                  .filter((cat) => cat.category === activeCategory)
                  .map((cat) => (
                    <div
                      key={cat.category}
                      className="flex gap-4 overflow-x-auto pb-3 scrollbar-thin"
                      style={{ scrollbarWidth: 'thin' }}
                    >
                      {cat.books.map((book, idx) => (
                        <div
                          key={book.isbn || idx}
                          className="flex-shrink-0 w-36 sm:w-40 group"
                        >
                          {/* Cover Image */}
                          <div className="w-full h-52 rounded-xl overflow-hidden bg-stone-100 shadow-sm mb-3 relative">
                            {book.image ? (
                              <img
                                src={book.image}
                                alt={book.title}
                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                              />
                            ) : (
                              <div className="w-full h-full flex items-center justify-center">
                                <BookOpen className="w-8 h-8 text-stone-400" />
                              </div>
                            )}
                          </div>

                          {/* Book Info */}
                          <h3 className="font-serif text-sm font-semibold text-stone-800 line-clamp-2 leading-snug mb-1">
                            {book.title}
                          </h3>
                          <p className="text-xs text-stone-500 truncate mb-2">
                            {book.author}
                          </p>

                          {/* Generate Button */}
                          <button
                            onClick={() => handleSelectBook(book)}
                            className="w-full py-1.5 bg-wood/10 text-wood rounded-lg text-xs font-medium hover:bg-wood hover:text-white transition-colors flex items-center justify-center gap-1"
                          >
                            <Sparkles className="w-3 h-3" />
                            북카드 생성
                          </button>
                        </div>
                      ))}
                    </div>
                  ))}
              </>
            )}
          </div>
        </div>
      )}

      {/* Error Message (generation errors) */}
      {error && (
        <div className="max-w-2xl mx-auto px-4">
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl flex items-center justify-between">
            <span>{error}</span>
            <button onClick={() => setError(null)} className="ml-3 text-red-400 hover:text-red-600">
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Search Modal */}
      <SearchModal
        isOpen={showSearchModal}
        onClose={() => setShowSearchModal(false)}
        onSelectBook={handleSelectBook}
      />

      {/* How It Works Modal */}
      {showHowTo && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/50 backdrop-blur-sm"
          onClick={(e) => { if (e.target === e.currentTarget) setShowHowTo(false) }}
        >
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-7">
            {/* Modal Header */}
            <div className="flex items-center justify-between mb-6">
              <h2 className="font-serif text-xl font-bold text-stone-800">이용 방법</h2>
              <button
                onClick={() => setShowHowTo(false)}
                className="p-2 text-stone-400 hover:text-stone-600 hover:bg-stone-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Steps */}
            <div className="space-y-4">
              {/* Step 1 */}
              <div className="flex gap-4 items-start">
                <div className="relative flex-shrink-0">
                  <div className="w-12 h-12 bg-wood/10 rounded-xl flex items-center justify-center">
                    <Search className="w-6 h-6 text-wood" />
                  </div>
                  <div className="absolute -top-1.5 -left-1.5 w-6 h-6 bg-wood text-white rounded-full flex items-center justify-center text-xs font-bold">
                    1
                  </div>
                </div>
                <div>
                  <h3 className="font-semibold text-stone-800 mb-0.5">책 검색</h3>
                  <p className="text-sm text-stone-500">
                    읽은 책의 제목이나 저자를 검색하세요
                  </p>
                </div>
              </div>

              <div className="ml-6 w-0.5 h-4 bg-stone-200 rounded" />

              {/* Step 2 */}
              <div className="flex gap-4 items-start">
                <div className="relative flex-shrink-0">
                  <div className="w-12 h-12 bg-amber-100 rounded-xl flex items-center justify-center">
                    <BookOpen className="w-6 h-6 text-amber-600" />
                  </div>
                  <div className="absolute -top-1.5 -left-1.5 w-6 h-6 bg-wood text-white rounded-full flex items-center justify-center text-xs font-bold">
                    2
                  </div>
                </div>
                <div>
                  <h3 className="font-semibold text-stone-800 mb-0.5">책 선택</h3>
                  <p className="text-sm text-stone-500">
                    검색 결과에서 원하는 책을 선택하세요
                  </p>
                </div>
              </div>

              <div className="ml-6 w-0.5 h-4 bg-stone-200 rounded" />

              {/* Step 3 */}
              <div className="flex gap-4 items-start">
                <div className="relative flex-shrink-0">
                  <div className="w-12 h-12 bg-rose-100 rounded-xl flex items-center justify-center">
                    <Sparkles className="w-6 h-6 text-rose-500" />
                  </div>
                  <div className="absolute -top-1.5 -left-1.5 w-6 h-6 bg-wood text-white rounded-full flex items-center justify-center text-xs font-bold">
                    3
                  </div>
                </div>
                <div>
                  <h3 className="font-semibold text-stone-800 mb-0.5">AI 생성</h3>
                  <p className="text-sm text-stone-500">
                    AI가 요약문과 아트 커버를 만들어드립니다
                  </p>
                </div>
              </div>
            </div>

            <button
              onClick={() => {
                setShowHowTo(false)
                setShowSearchModal(true)
              }}
              className="mt-6 w-full py-3 bg-wood text-white rounded-xl font-medium hover:bg-wood-dark transition-colors flex items-center justify-center gap-2"
            >
              <ArrowRight className="w-4 h-4" />
              바로 시작하기
            </button>
          </div>
        </div>
      )}

      {/* Generating Modal */}
      {selectedBook && isGenerating && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4 modal-backdrop">
          <div className="bg-white rounded-2xl max-w-md w-full p-8 text-center modal-content">
            <div className="mb-6">
              <div className="inline-flex items-center justify-center w-16 h-16 bg-wood/10 rounded-full mb-4">
                <Sparkles className="w-8 h-8 text-wood animate-pulse" />
              </div>
              <h2 className="font-serif text-2xl font-semibold text-stone-800 mb-2">
                북카드 생성 중
              </h2>
              <p className="font-serif text-lg text-wood mt-2">
                "{selectedBook.title}"
              </p>
            </div>

            {/* Progress Bar */}
            <div className="mb-6">
              <div className="flex justify-between text-xs text-stone-500 mb-2">
                <span>진행률</span>
                <span>{progress.step}/{progress.totalSteps} 단계</span>
              </div>
              <div className="w-full bg-stone-200 rounded-full h-2">
                <div
                  className="bg-wood h-2 rounded-full transition-all duration-500"
                  style={{ width: `${(progress.step / progress.totalSteps) * 100}%` }}
                />
              </div>
            </div>

            {/* Current Step Message */}
            <div className="flex items-center justify-center gap-2 text-stone-600">
              <Loader2 className="w-5 h-5 animate-spin text-wood" />
              <span>{progress.message || '준비 중...'}</span>
            </div>

            <button
              onClick={handleCloseGenerate}
              className="mt-6 text-stone-500 hover:text-stone-700 text-sm"
            >
              취소
            </button>
          </div>
        </div>
      )}

      {/* Book Viewer */}
      {generatedBook && (
        <BookViewer book={generatedBook} onClose={handleCloseViewer} />
      )}
    </div>
  )
}

export default Main
