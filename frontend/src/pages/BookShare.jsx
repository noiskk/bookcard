import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Loader2, AlertCircle, Home } from 'lucide-react'
import BookViewer from '../components/BookViewer'
import bookApi from '../api/bookApi'

function BookShare() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [book, setBook] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const fetchBook = async () => {
      try {
        setIsLoading(true)
        setError(null)
        const data = await bookApi.getBookById(id)
        setBook(data)
      } catch (err) {
        setError('북카드를 찾을 수 없습니다.')
        console.error(err)
      } finally {
        setIsLoading(false)
      }
    }

    if (id) {
      fetchBook()
    }
  }, [id])

  const handleClose = () => {
    navigate('/')
  }

  const handleBookUpdate = (updatedBook) => {
    setBook(updatedBook)
  }

  if (isLoading) {
    return (
      <div className="fixed inset-0 bg-black flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-12 h-12 animate-spin text-amber-400 mx-auto mb-4" />
          <p className="text-white/60">북카드를 불러오는 중...</p>
        </div>
      </div>
    )
  }

  if (error || !book) {
    return (
      <div className="fixed inset-0 bg-black flex items-center justify-center">
        <div className="text-center max-w-md px-6">
          <div className="w-16 h-16 bg-red-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="w-8 h-8 text-red-400" />
          </div>
          <h1 className="text-xl font-semibold text-white mb-2">
            {error || '북카드를 찾을 수 없습니다'}
          </h1>
          <p className="text-white/60 mb-6">
            요청하신 북카드가 존재하지 않거나 삭제되었을 수 있습니다.
          </p>
          <button
            onClick={() => navigate('/')}
            className="inline-flex items-center gap-2 px-6 py-3 bg-amber-500 text-black rounded-xl font-medium hover:bg-amber-400 transition-colors"
          >
            <Home className="w-5 h-5" />
            홈으로 이동
          </button>
        </div>
      </div>
    )
  }

  return (
    <BookViewer
      book={book}
      onClose={handleClose}
      onBookUpdate={handleBookUpdate}
    />
  )
}

export default BookShare
