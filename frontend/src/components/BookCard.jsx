import { Eye, Trash2 } from 'lucide-react'

function BookCard({ book, onView, onDelete }) {
  return (
    <div className="card group hover:shadow-lg transition-shadow duration-300">
      <div className="flex gap-4">
        {/* Book Cover */}
        <div className="flex-shrink-0 w-24 h-36 rounded-lg overflow-hidden bg-stone-200">
          {book.coverImage ? (
            <img
              src={book.coverImage}
              alt={book.title}
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-stone-400">
              이미지 없음
            </div>
          )}
        </div>

        {/* Book Info */}
        <div className="flex-1 min-w-0">
          <h3 className="font-serif text-lg font-semibold text-stone-800 truncate">
            {book.title}
          </h3>
          <p className="text-sm text-stone-500 mb-2">{book.author}</p>

          {book.summary && book.summary.length > 0 && (
            <p className="text-sm text-stone-600 line-clamp-2">
              {book.summary[0]}
            </p>
          )}

          {/* Actions */}
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => onView(book)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm bg-stone-100 text-stone-700 rounded-lg hover:bg-stone-200 transition-colors"
            >
              <Eye className="w-4 h-4" />
              보기
            </button>
            <button
              onClick={() => onDelete(book.id)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            >
              <Trash2 className="w-4 h-4" />
              삭제
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default BookCard
