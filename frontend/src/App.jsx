import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Main from './pages/Main'
import Library from './pages/Library'
import Settings from './pages/Settings'
import BookShare from './pages/BookShare'
import Login from './pages/Login'
import Register from './pages/Register'

function App() {
  return (
    <Routes>
      {/* Auth pages - standalone without layout */}
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* Share page - standalone without layout */}
      <Route path="/book/:id" element={<BookShare />} />

      {/* Main app with layout */}
      <Route path="/" element={<Layout />}>
        <Route index element={<Main />} />
        <Route path="library" element={<Library />} />
        <Route path="settings" element={<Settings />} />
      </Route>
    </Routes>
  )
}

export default App
