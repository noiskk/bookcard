const STORAGE_KEY = 'bookcard_my_books'

export const myBooks = {
  getAll() {
    try {
      const data = localStorage.getItem(STORAGE_KEY)
      return data ? JSON.parse(data) : []
    } catch {
      return []
    }
  },

  add(bookId) {
    const ids = this.getAll()
    if (!ids.includes(bookId)) {
      ids.push(bookId)
      localStorage.setItem(STORAGE_KEY, JSON.stringify(ids))
    }
  },

  remove(bookId) {
    const ids = this.getAll().filter(id => id !== bookId)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(ids))
  },

  has(bookId) {
    return this.getAll().includes(bookId)
  }
}

export default myBooks
