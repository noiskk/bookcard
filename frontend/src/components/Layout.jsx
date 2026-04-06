import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { BookOpen, Library, Settings, Sparkles, LogIn, LogOut } from 'lucide-react'
import authApi from '../api/authApi'

function Layout() {
  const navigate = useNavigate()
  const [isLoggedIn, setIsLoggedIn] = useState(authApi.isLoggedIn())

  const handleLogout = () => {
    authApi.logout()
    setIsLoggedIn(false)
    navigate('/')
  }

  const navLinkClass = ({ isActive }) =>
    `flex items-center gap-2 px-4 py-2 rounded-lg transition-colors duration-200 ${
      isActive
        ? 'bg-wood text-white'
        : 'text-stone-600 hover:bg-stone-200 hover:text-stone-800'
    }`

  return (
    <div className="min-h-screen bg-stone-100">
      {/* Header */}
      <header className="bg-white border-b border-stone-200 sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <NavLink to="/" className="flex items-center gap-3">
              <div className="p-2 bg-wood rounded-lg">
                <BookOpen className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-xl font-serif font-bold text-stone-800">BookCard</h1>
                <p className="text-xs text-stone-500">AI 책 요약 서비스</p>
              </div>
            </NavLink>

            <nav className="flex items-center gap-2">
              <NavLink to="/" className={navLinkClass} end>
                <Sparkles className="w-4 h-4" />
                <span className="hidden sm:inline">생성</span>
              </NavLink>
              <NavLink to="/library" className={navLinkClass}>
                <Library className="w-4 h-4" />
                <span className="hidden sm:inline">보관함</span>
              </NavLink>
              <NavLink to="/settings" className={navLinkClass}>
                <Settings className="w-4 h-4" />
                <span className="hidden sm:inline">설정</span>
              </NavLink>
              {isLoggedIn ? (
                <div className="flex items-center gap-1">
                  <span className="hidden sm:inline text-sm text-stone-600 px-2">
                    {authApi.getNickname() || authApi.getCurrentUserEmail()}님
                  </span>
                  <button onClick={handleLogout} className="flex items-center gap-2 px-4 py-2 rounded-lg text-stone-600 hover:bg-stone-200 hover:text-stone-800 transition-colors duration-200">
                    <LogOut className="w-4 h-4" />
                    <span className="hidden sm:inline">로그아웃</span>
                  </button>
                </div>
              ) : (
                <NavLink to="/login" className={navLinkClass}>
                  <LogIn className="w-4 h-4" />
                  <span className="hidden sm:inline">로그인</span>
                </NavLink>
              )}
            </nav>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="border-t border-stone-200 bg-white mt-auto">
        <div className="max-w-6xl mx-auto px-6 py-4">
          <p className="text-center text-sm text-stone-500">
            북카드 서비스 - AI 기반
          </p>
        </div>
      </footer>
    </div>
  )
}

export default Layout
