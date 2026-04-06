import { useState, useEffect } from 'react'
import { Settings as SettingsIcon, Save, RotateCcw, Sparkles } from 'lucide-react'

const DEFAULT_SETTINGS = {
  defaultPrompt: '책의 본질을 담아낸 깊이 있는 문학적 분석을 작성해주세요.',
  summaryStyle: 'literary',
  summaryLength: 'medium'
}

function Settings() {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    const storedSettings = localStorage.getItem('bookcard-settings')
    if (storedSettings) {
      setSettings(JSON.parse(storedSettings))
    }
  }, [])

  const handleSave = () => {
    localStorage.setItem('bookcard-settings', JSON.stringify(settings))
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  const handleReset = () => {
    setSettings(DEFAULT_SETTINGS)
    localStorage.removeItem('bookcard-settings')
  }

  const handleChange = (field) => (e) => {
    setSettings((prev) => ({ ...prev, [field]: e.target.value }))
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="p-3 bg-wood/10 rounded-xl">
          <SettingsIcon className="w-6 h-6 text-wood" />
        </div>
        <div>
          <h1 className="font-serif text-2xl font-bold text-stone-800">
            설정
          </h1>
          <p className="text-sm text-stone-500">
            AI 생성 환경을 설정하세요
          </p>
        </div>
      </div>

      {/* Settings Card */}
      <div className="card space-y-6">
        {/* Default Prompt */}
        <div>
          <label className="flex items-center gap-2 text-sm font-medium text-stone-700 mb-2">
            <Sparkles className="w-4 h-4 text-wood" />
            기본 AI 프롬프트
          </label>
          <textarea
            value={settings.defaultPrompt}
            onChange={handleChange('defaultPrompt')}
            placeholder="AI 생성을 위한 기본 프롬프트를 입력하세요..."
            className="input-field resize-none"
            rows={4}
          />
          <p className="mt-1 text-xs text-stone-500">
            새 북카드를 생성할 때 기본값으로 사용됩니다.
          </p>
        </div>

        {/* Summary Style */}
        <div>
          <label className="block text-sm font-medium text-stone-700 mb-2">
            요약 스타일
          </label>
          <select
            value={settings.summaryStyle}
            onChange={handleChange('summaryStyle')}
            className="input-field"
          >
            <option value="literary">문학적 분석</option>
            <option value="poetic">시적 & 예술적</option>
            <option value="concise">간결 & 직접적</option>
            <option value="storytelling">스토리텔링</option>
          </select>
          <p className="mt-1 text-xs text-stone-500">
            생성되는 요약의 톤과 스타일을 선택하세요.
          </p>
        </div>

        {/* Summary Length */}
        <div>
          <label className="block text-sm font-medium text-stone-700 mb-2">
            요약 길이
          </label>
          <div className="flex gap-3">
            {[
              { value: 'short', label: '짧게' },
              { value: 'medium', label: '보통' },
              { value: 'long', label: '길게' }
            ].map(({ value, label }) => (
              <button
                key={value}
                onClick={() => setSettings((prev) => ({ ...prev, summaryLength: value }))}
                className={`flex-1 py-2 px-4 rounded-lg font-medium transition-colors ${
                  settings.summaryLength === value
                    ? 'bg-wood text-white'
                    : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t border-stone-200">
          <button
            onClick={handleReset}
            className="btn-secondary flex items-center gap-2"
          >
            <RotateCcw className="w-4 h-4" />
            기본값으로 초기화
          </button>
          <button
            onClick={handleSave}
            className="btn-primary flex items-center gap-2 ml-auto"
          >
            <Save className="w-4 h-4" />
            {saved ? '저장됨!' : '설정 저장'}
          </button>
        </div>
      </div>

      {/* Info Card */}
      <div className="bg-wood/5 border border-wood/20 rounded-xl p-6">
        <h3 className="font-serif text-lg font-semibold text-stone-800 mb-2">
          AI 생성에 대하여
        </h3>
        <p className="text-sm text-stone-600 leading-relaxed">
          AI 북카드 서비스는 고급 언어 모델을 사용하여 책의 독특하고 예술적인 요약을 생성합니다.
          각 생성된 카드는 아름답고 이야기 같은 형식으로 책의 본질을 담아냅니다.
          여기서 설정한 옵션은 앞으로 생성되는 모든 북카드에 적용됩니다.
        </p>
      </div>
    </div>
  )
}

export default Settings
