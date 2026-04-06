import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookOpen, Loader2, Mail, Lock } from 'lucide-react'
import authApi from '../api/authApi'

function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.email.trim() || !form.password.trim()) return

    try {
      setIsLoading(true)
      setError(null)
      await authApi.login({ email: form.email, password: form.password })
      window.location.href = '/'
    } catch (err) {
      setError(err.message || '로그인에 실패했습니다.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-stone-100 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <Link to="/" className="inline-flex flex-col items-center gap-3">
            <div className="p-3 bg-wood rounded-xl shadow-md shadow-wood/20">
              <BookOpen className="w-8 h-8 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-serif font-bold text-stone-800">BookCard</h1>
              <p className="text-sm text-stone-500">AI 책 요약 서비스</p>
            </div>
          </Link>
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-lg shadow-stone-200/50 border border-stone-100 p-8">
          <h2 className="font-serif text-2xl font-bold text-stone-800 mb-1 text-center">
            로그인
          </h2>
          <p className="text-sm text-stone-500 text-center mb-8">
            계정에 로그인하여 북카드를 만들어보세요
          </p>

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-stone-700 mb-1.5">
                이메일
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <Mail className="w-4 h-4 text-stone-400" />
                </div>
                <input
                  id="email"
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="example@email.com"
                  required
                  className="input-field pl-10"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-stone-700 mb-1.5">
                비밀번호
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <Lock className="w-4 h-4 text-stone-400" />
                </div>
                <input
                  id="password"
                  type="password"
                  name="password"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="비밀번호를 입력하세요"
                  required
                  className="input-field pl-10"
                />
              </div>
            </div>

            {/* Error Message */}
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
                {error}
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={isLoading || !form.email.trim() || !form.password.trim()}
              className="w-full py-3 bg-wood text-white font-medium rounded-lg hover:bg-wood-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200 flex items-center justify-center gap-2 shadow-md shadow-wood/20"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  로그인 중...
                </>
              ) : (
                '로그인'
              )}
            </button>
          </form>
        </div>

        {/* Register Link */}
        <p className="text-center text-sm text-stone-500 mt-6">
          아직 계정이 없으신가요?{' '}
          <Link to="/register" className="text-wood font-medium hover:text-wood-dark transition-colors">
            회원가입
          </Link>
        </p>
      </div>
    </div>
  )
}

export default Login
