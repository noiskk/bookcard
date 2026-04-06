const API_BASE_URL = 'http://localhost:8080/api';
const BACKEND_URL = 'http://localhost:8080';
const TOKEN_KEY = 'bookcard_token';

// 인증 헤더 반환
const getAuthHeader = () => {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// 이미지 URL을 전체 경로로 변환
const resolveImageUrl = (url) => {
  if (!url) return null;
  // 이미 전체 URL이면 그대로 반환
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  // 상대 경로면 백엔드 URL 붙이기
  return BACKEND_URL + url;
};

// 책 데이터의 이미지 URL들을 변환
const resolveBookImages = (book) => ({
  ...book,
  originalImage: resolveImageUrl(book.originalImage),
  generatedImage: resolveImageUrl(book.generatedImage),
  coverImage: resolveImageUrl(book.coverImage),
});

export const bookApi = {
  // Get all saved book cards from library
  async getAllBooks() {
    const response = await fetch(`${API_BASE_URL}/books`);
    if (!response.ok) throw new Error('Failed to fetch books');
    const books = await response.json();
    return books.map(resolveBookImages);
  },

  // Get a specific book card by ID
  async getBookById(id) {
    const response = await fetch(`${API_BASE_URL}/books/${id}`);
    if (!response.ok) throw new Error('Failed to fetch book');
    const book = await response.json();
    return resolveBookImages(book);
  },

  // Search books from Naver API (external search for generating new cards)
  async searchBooks(query, start = 1) {
    const response = await fetch(`${API_BASE_URL}/books/search?query=${encodeURIComponent(query)}&start=${start}`);
    if (!response.ok) throw new Error('Failed to search books');
    return response.json();
  },

  // Search within saved library
  async searchLibrary(query) {
    const response = await fetch(`${API_BASE_URL}/books/library/search?q=${encodeURIComponent(query)}`);
    if (!response.ok) throw new Error('Failed to search library');
    const books = await response.json();
    return books.map(resolveBookImages);
  },

  // Generate a new book card using AI
  async generateBook(bookData) {
    const response = await fetch(`${API_BASE_URL}/books/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...getAuthHeader(),
      },
      body: JSON.stringify({
        isbn: bookData.isbn,
        title: bookData.title,
        author: bookData.author,
        publisher: bookData.publisher,
        originalImage: bookData.image,
        description: bookData.description,
      }),
    });
    if (!response.ok) throw new Error('Failed to generate book card');
    const book = await response.json();
    return resolveBookImages(book);
  },

  // Generate a new book card with SSE progress updates
  generateBookWithProgress(bookData, onProgress, onComplete, onError) {
    const body = JSON.stringify({
      isbn: bookData.isbn,
      title: bookData.title,
      author: bookData.author,
      publisher: bookData.publisher,
      originalImage: bookData.image,
      description: bookData.description,
    });

    // SSE는 GET만 지원하므로 fetch로 POST SSE 구현
    fetch(`${API_BASE_URL}/books/generate/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        ...getAuthHeader(),
      },
      body: body,
    }).then(response => {
      if (!response.ok) {
        throw new Error('Failed to start generation');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let completed = false;

      const processStream = async () => {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                const data = JSON.parse(line.slice(5).trim());

                if (data.status === 'in_progress') {
                  onProgress?.(data);
                } else if (data.status === 'completed') {
                  completed = true;
                  const book = resolveBookImages(data.data);
                  onComplete?.(book);
                } else if (data.status === 'error') {
                  completed = true;
                  onError?.(new Error(data.message));
                }
              } catch (e) {
                // JSON 파싱 실패는 무시
              }
            }
          }
        }
      };

      processStream().catch((err) => {
        if (!completed) onError?.(err);
      });
    }).catch(onError);
  },

  // Get book recommendations by category
  async getRecommendations() {
    const response = await fetch(`${API_BASE_URL}/books/recommendations`);
    if (!response.ok) throw new Error('Failed to fetch recommendations');
    return response.json();
  },

  // Delete a book card from library
  async deleteBook(id) {
    const response = await fetch(`${API_BASE_URL}/books/${id}`, {
      method: 'DELETE',
      headers: {
        ...getAuthHeader(),
      },
    });
    if (!response.ok) throw new Error('Failed to delete book');
  },

  // Like a book card (increment like count)
  async likeBook(id) {
    const response = await fetch(`${API_BASE_URL}/books/${id}/like`, {
      method: 'POST',
      headers: {
        ...getAuthHeader(),
      },
    });
    if (!response.ok) throw new Error('Failed to like book');
    const book = await response.json();
    return resolveBookImages(book);
  },
};

export default bookApi;
