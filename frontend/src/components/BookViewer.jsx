import { useState } from 'react'
import { X, ChevronLeft, ChevronRight, BookOpen, Quote, Heart, Share2, Check } from 'lucide-react'
import bookApi from '../api/bookApi'

function BookViewer({ book, onClose, onBookUpdate }) {
  const [currentPage, setCurrentPage] = useState(0)
  const [isTransitioning, setIsTransitioning] = useState(false)
  const [likeCount, setLikeCount] = useState(book?.likeCount || 0)
  const [isLiking, setIsLiking] = useState(false)
  const [copied, setCopied] = useState(false)

  if (!book) return null

  const handleLike = async () => {
    if (!book.id || isLiking) return
    setIsLiking(true)
    try {
      const updatedBook = await bookApi.likeBook(book.id)
      setLikeCount(updatedBook.likeCount)
      onBookUpdate?.(updatedBook)
    } catch (err) {
      console.error('Failed to like:', err)
    } finally {
      setIsLiking(false)
    }
  }

  const handleShare = async () => {
    if (!book.id) return
    const shareUrl = `${window.location.origin}/book/${book.id}`
    try {
      await navigator.clipboard.writeText(shareUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  // Get cover image (prefer generated, fallback to original)
  const coverImage = book.generatedImage || book.originalImage || book.coverImage

  // Build pages array: Cover page + Summary pages + Final image page
  const summaryPages = book.summary || []
  const totalPages = 1 + summaryPages.length + 1 // 표지 + 요약들 + 마지막 이미지
  const isLastPage = currentPage === totalPages - 1
  const isSummaryPage = currentPage > 0 && currentPage <= summaryPages.length

  // 쉼표, 온점 기준으로 줄바꿈 처리
  const formatText = (text) => {
    if (!text) return []
    // 쉼표나 온점 뒤에서 분리 (구분자는 유지)
    const segments = text.split(/(?<=[,.])\s*/).filter(s => s.trim())
    return segments
  }

  const goToNext = () => {
    if (currentPage < totalPages - 1 && !isTransitioning) {
      setIsTransitioning(true)
      setTimeout(() => {
        setCurrentPage(currentPage + 1)
        setIsTransitioning(false)
      }, 300)
    }
  }

  const goToPrev = () => {
    if (currentPage > 0 && !isTransitioning) {
      setIsTransitioning(true)
      setTimeout(() => {
        setCurrentPage(currentPage - 1)
        setIsTransitioning(false)
      }, 300)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowRight' || e.key === ' ') {
      goToNext()
    } else if (e.key === 'ArrowLeft') {
      goToPrev()
    } else if (e.key === 'Escape') {
      onClose()
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/95 flex items-center justify-center z-50"
      onKeyDown={handleKeyDown}
      tabIndex={0}
      autoFocus
    >
      {/* Ambient Background Particles */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="particle particle-1" />
        <div className="particle particle-2" />
        <div className="particle particle-3" />
        <div className="particle particle-4" />
        <div className="particle particle-5" />
      </div>

      {/* Main Container */}
      <div className="relative w-full max-w-2xl h-full max-h-[90vh] overflow-hidden rounded-3xl">
        {/* Background Image with Ken Burns effect */}
        {coverImage && (
          <div
            className={`absolute inset-0 bg-cover bg-center transition-all duration-700 ease-out ken-burns ${
              isSummaryPage ? 'blur-2xl scale-125 brightness-[0.3]' : 'scale-105'
            }`}
            style={{ backgroundImage: `url(${coverImage})` }}
          />
        )}

        {/* Gradient Overlays */}
        <div className="absolute inset-0 bg-gradient-to-b from-black/50 via-black/20 to-black/70" />
        <div className="absolute inset-0 bg-gradient-to-r from-black/30 via-transparent to-black/30" />

        {/* Progress Bar - Refined */}
        <div className="absolute top-6 left-6 right-6 z-20 flex gap-1.5">
          {Array.from({ length: totalPages }).map((_, index) => (
            <div
              key={index}
              className="flex-1 h-1 rounded-full overflow-hidden bg-white/20 backdrop-blur-sm"
            >
              <div
                className={`h-full bg-gradient-to-r from-amber-400 to-orange-400 transition-all duration-500 ease-out ${
                  index <= currentPage ? 'w-full' : 'w-0'
                }`}
              />
            </div>
          ))}
        </div>

        {/* Close Button - Glass style */}
        <button
          onClick={onClose}
          className="absolute top-14 right-6 z-20 p-2.5 bg-white/10 backdrop-blur-md rounded-full text-white/80 hover:text-white hover:bg-white/20 transition-all duration-300"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Page Content */}
        <div className={`relative h-full flex flex-col justify-center items-center p-8 transition-opacity duration-300 ${isTransitioning ? 'opacity-0' : 'opacity-100'}`}>
          {currentPage === 0 ? (
            // Cover Page
            <div className="text-center animate-fadeIn">
              {/* Book Cover with floating effect */}
              <div className="flex justify-center mb-10">
                <div className="relative group">
                  {/* Glow effect */}
                  <div className="absolute -inset-4 bg-gradient-to-r from-amber-500/30 to-orange-500/30 rounded-2xl blur-2xl opacity-60 group-hover:opacity-80 transition-opacity duration-500" />

                  {/* Book cover */}
                  <div className="relative w-56 h-80 rounded-xl overflow-hidden shadow-2xl ring-1 ring-white/20 transform hover:scale-[1.02] transition-transform duration-500 book-float">
                    {coverImage ? (
                      <img
                        src={coverImage}
                        alt={book.title}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full bg-gradient-to-br from-stone-700 to-stone-800 flex items-center justify-center">
                        <BookOpen className="w-16 h-16 text-stone-500" />
                      </div>
                    )}

                    {/* Shine effect */}
                    <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/10 to-transparent" />
                  </div>
                </div>
              </div>

              {/* Title & Author */}
              <h1 className="text-3xl md:text-4xl font-bold text-white mb-4 drop-shadow-lg tracking-tight">
                {book.title}
              </h1>
              <p className="text-lg text-white/70">
                {book.author}
              </p>
              {book.publisher && (
                <p className="text-sm text-white/50 mt-2">{book.publisher}</p>
              )}

              {/* Scroll hint */}
              <div className="mt-12 animate-bounce">
                <ChevronRight className="w-6 h-6 text-white/40 mx-auto rotate-90" />
              </div>
            </div>
          ) : isLastPage ? (
            // Final Image Page - Full screen image showcase
            <div className="text-center animate-fadeIn">
              {/* Large Image Display */}
              <div className="flex justify-center mb-8">
                <div className="relative group">
                  {/* Glow effect */}
                  <div className="absolute -inset-6 bg-gradient-to-r from-amber-500/40 to-orange-500/40 rounded-3xl blur-3xl opacity-70 group-hover:opacity-90 transition-opacity duration-500" />

                  {/* Full image */}
                  <div className="relative w-72 h-96 md:w-80 md:h-[28rem] rounded-2xl overflow-hidden shadow-2xl ring-1 ring-white/20">
                    {coverImage ? (
                      <img
                        src={coverImage}
                        alt={book.title}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full bg-gradient-to-br from-stone-700 to-stone-800 flex items-center justify-center">
                        <BookOpen className="w-20 h-20 text-stone-500" />
                      </div>
                    )}

                    {/* Shine effect */}
                    <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/5 to-transparent" />
                  </div>
                </div>
              </div>

              {/* Book title */}
              <p className="text-xl text-white/80 font-medium mb-2">{book.title}</p>
              <p className="text-sm text-white/50">{book.author}</p>
            </div>
          ) : (
            // Summary Pages - Enhanced Quote Style
            <div className="text-center animate-fadeIn w-full max-w-2xl mx-auto px-4">
              {/* Glass Card - Dynamic Size */}
              <div className="relative inline-block px-8 py-10 md:px-12 md:py-12 rounded-3xl bg-white/5 backdrop-blur-xl border border-white/10 shadow-2xl">
                {/* Decorative Quote Mark - Top */}
                <div className="absolute -top-3 left-6 text-amber-400/60">
                  <Quote className="w-8 h-8 rotate-180" fill="currentColor" />
                </div>

                {/* Summary Text - 쉼표/온점 기준 줄바꿈 */}
                <p className="text-xl md:text-2xl text-white leading-loose font-medium tracking-wide break-keep">
                  {formatText(summaryPages[currentPage - 1]).map((segment, idx) => (
                    <span key={idx} className="block break-keep">
                      {segment}
                    </span>
                  ))}
                </p>

                {/* Decorative Quote Mark - Bottom */}
                <div className="absolute -bottom-3 right-6 text-amber-400/60">
                  <Quote className="w-8 h-8" fill="currentColor" />
                </div>
              </div>

              {/* Page indicator */}
              <div className="mt-8 flex items-center justify-center gap-2">
                {summaryPages.map((_, index) => (
                  <div
                    key={index}
                    className={`w-2 h-2 rounded-full transition-all duration-300 ${
                      index === currentPage - 1
                        ? 'bg-amber-400 w-6'
                        : 'bg-white/30'
                    }`}
                  />
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Navigation Areas */}
        <div className="absolute inset-0 flex">
          <button
            onClick={goToPrev}
            className="w-1/3 h-full cursor-pointer focus:outline-none"
            disabled={currentPage === 0}
          />
          <div className="w-1/3" />
          <button
            onClick={goToNext}
            className="w-1/3 h-full cursor-pointer focus:outline-none"
            disabled={currentPage === totalPages - 1}
          />
        </div>

        {/* Navigation Arrows */}
        <div className="absolute inset-y-0 left-4 flex items-center">
          {currentPage > 0 && (
            <button
              onClick={goToPrev}
              className="p-3 bg-white/10 backdrop-blur-md rounded-full text-white opacity-0 hover:opacity-100 hover:bg-white/20 transition-all duration-300"
            >
              <ChevronLeft className="w-6 h-6" />
            </button>
          )}
        </div>
        <div className="absolute inset-y-0 right-4 flex items-center">
          {currentPage < totalPages - 1 && (
            <button
              onClick={goToNext}
              className="p-3 bg-white/10 backdrop-blur-md rounded-full text-white opacity-0 hover:opacity-100 hover:bg-white/20 transition-all duration-300"
            >
              <ChevronRight className="w-6 h-6" />
            </button>
          )}
        </div>

        {/* Bottom Info Bar */}
        <div className="absolute bottom-6 left-6 right-6 flex items-center justify-between">
          <div className="text-white/40 text-sm">
            {currentPage === 0 ? '표지' : isLastPage ? '아트워크' : `${currentPage} / ${summaryPages.length}`}
          </div>

          {/* Like & Share Buttons */}
          {book.id && (
            <div className="flex items-center gap-2">
              <button
                onClick={handleLike}
                disabled={isLiking}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 backdrop-blur-md rounded-full text-white/80 hover:text-white hover:bg-white/20 transition-all duration-300"
              >
                <Heart className={`w-4 h-4 ${isLiking ? 'animate-pulse' : ''}`} />
                <span className="text-sm">{likeCount}</span>
              </button>
              <button
                onClick={handleShare}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-white/10 backdrop-blur-md rounded-full text-white/80 hover:text-white hover:bg-white/20 transition-all duration-300"
              >
                {copied ? (
                  <>
                    <Check className="w-4 h-4 text-green-400" />
                    <span className="text-sm text-green-400">복사됨</span>
                  </>
                ) : (
                  <>
                    <Share2 className="w-4 h-4" />
                    <span className="text-sm">공유</span>
                  </>
                )}
              </button>
            </div>
          )}

          <div className="text-white/40 text-xs">
            {isLastPage ? '탭하여 닫기' : '← → 또는 탭하여 이동'}
          </div>
        </div>

        {/* Tap to close on last page */}
        {isLastPage && (
          <button
            onClick={onClose}
            className="absolute inset-0 w-full h-full"
          />
        )}
      </div>

      {/* Click outside to close */}
      <button
        onClick={onClose}
        className="absolute inset-0 -z-10"
      />

      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(20px) scale(0.98); }
          to { opacity: 1; transform: translateY(0) scale(1); }
        }
        .animate-fadeIn {
          animation: fadeIn 0.6s ease-out;
        }

        @keyframes float {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-10px); }
        }
        .book-float {
          animation: float 4s ease-in-out infinite;
        }

        @keyframes kenBurns {
          0% { transform: scale(1.05); }
          100% { transform: scale(1.15); }
        }
        .ken-burns {
          animation: kenBurns 20s ease-out forwards;
        }

        /* Floating particles */
        .particle {
          position: absolute;
          width: 4px;
          height: 4px;
          background: rgba(251, 191, 36, 0.3);
          border-radius: 50%;
          animation: floatParticle 15s infinite ease-in-out;
        }
        .particle-1 { left: 10%; top: 20%; animation-delay: 0s; }
        .particle-2 { left: 80%; top: 40%; animation-delay: 3s; }
        .particle-3 { left: 30%; top: 70%; animation-delay: 6s; }
        .particle-4 { left: 70%; top: 80%; animation-delay: 9s; }
        .particle-5 { left: 50%; top: 30%; animation-delay: 12s; }

        @keyframes floatParticle {
          0%, 100% {
            transform: translate(0, 0) scale(1);
            opacity: 0.3;
          }
          25% {
            transform: translate(20px, -30px) scale(1.5);
            opacity: 0.6;
          }
          50% {
            transform: translate(-10px, -60px) scale(1);
            opacity: 0.3;
          }
          75% {
            transform: translate(30px, -30px) scale(1.2);
            opacity: 0.5;
          }
        }
      `}</style>
    </div>
  )
}

export default BookViewer
