import { useState } from 'react'
import { X, Sparkles, Loader2 } from 'lucide-react'

function GenerateModal({ onClose, onGenerate, isGenerating }) {
  const [formData, setFormData] = useState({
    title: '',
    author: '',
    prompt: ''
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    if (formData.title && formData.author) {
      onGenerate(formData)
    }
  }

  const handleChange = (field) => (e) => {
    setFormData((prev) => ({ ...prev, [field]: e.target.value }))
  }

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4 modal-backdrop"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-md w-full shadow-2xl modal-content"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-stone-200">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-wood/10 rounded-lg">
              <Sparkles className="w-5 h-5 text-wood" />
            </div>
            <h2 className="font-serif text-xl font-semibold text-stone-800">
              북카드 생성
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-stone-400 hover:text-stone-600 hover:bg-stone-100 rounded-full transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-stone-700 mb-2">
              책 제목 *
            </label>
            <input
              type="text"
              value={formData.title}
              onChange={handleChange('title')}
              placeholder="책 제목을 입력하세요"
              className="input-field"
              required
              disabled={isGenerating}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-stone-700 mb-2">
              저자 *
            </label>
            <input
              type="text"
              value={formData.author}
              onChange={handleChange('author')}
              placeholder="저자명을 입력하세요"
              className="input-field"
              required
              disabled={isGenerating}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-stone-700 mb-2">
              커스텀 프롬프트 (선택사항)
            </label>
            <textarea
              value={formData.prompt}
              onChange={handleChange('prompt')}
              placeholder="AI 요약을 위한 추가 지시사항을 입력하세요..."
              className="input-field resize-none"
              rows={3}
              disabled={isGenerating}
            />
            <p className="mt-1 text-xs text-stone-500">
              예: "사랑과 상실의 주제에 초점을 맞춰주세요" 또는 "시적인 스타일로 작성해주세요"
            </p>
          </div>

          {/* Actions */}
          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="btn-secondary flex-1"
              disabled={isGenerating}
            >
              취소
            </button>
            <button
              type="submit"
              className="btn-primary flex-1 flex items-center justify-center gap-2"
              disabled={isGenerating || !formData.title || !formData.author}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  생성 중...
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  생성하기
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default GenerateModal
