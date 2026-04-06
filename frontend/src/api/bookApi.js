const API_BASE_URL = 'http://localhost:8080/api';
const BACKEND_URL = 'http://localhost:8080';
const TOKEN_KEY = 'bookcard_token';
const SETTINGS_KEY = 'bookcard-settings';

// localStorage에서 사용자 설정값 읽기
const getUserSettings = () => {
  try {
    const stored = localStorage.getItem(SETTINGS_KEY);
    if (stored) {
      const settings = JSON.parse(stored);
      return {
        summaryStyle: settings.summaryStyle || null,
        summaryLength: settings.summaryLength || null,
        defaultPrompt: settings.defaultPrompt || null,
      };
    }
  } catch (e) {
    // 파싱 실패 시 무시
  }
  return {};
};

// 인증 헤더 반환
const getAuthHeader = () => {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// 401 응답 처리 — 토큰 만료 시 자동 로그아웃
const handleUnauthorized = (response) => {
  if (response.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('bookcard_nickname');
    window.location.href = '/login';
    throw new Error('로그인이 만료되었습니다. 다시 로그인해주세요.');
  }
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

  // Get paged books from library
  async getPagedBooks(page = 0, size = 12) {
    const response = await fetch(`${API_BASE_URL}/books/paged?page=${page}&size=${size}`);
    if (!response.ok) throw new Error('Failed to fetch books');
    const data = await response.json();
    return {
      ...data,
      content: data.content.map(resolveBookImages),
    };
  },

  // Generate a new book card using AI
  async generateBook(bookData) {
    const settings = getUserSettings();
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
        ...settings,
      }),
    });
    handleUnauthorized(response);
    if (!response.ok) throw new Error('Failed to generate book card');
    const book = await response.json();
    return resolveBookImages(book);
  },

  // Generate a new book card with SSE progress updates
  generateBookWithProgress(bookData, onProgress, onComplete, onError) {
    const settings = getUserSettings();
    const body = JSON.stringify({
      isbn: bookData.isbn,
      title: bookData.title,
      author: bookData.author,
      publisher: bookData.publisher,
      originalImage: bookData.image,
      description: bookData.description,
      ...settings,
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
      if (response.status === 401) {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem('bookcard_nickname');
        window.location.href = '/login';
        throw new Error('로그인이 만료되었습니다.');
      }
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

  // Get current user's book cards (authentication required)
  async getMyBooks(page = 0, size = 12) {
    const response = await fetch(`${API_BASE_URL}/books/my?page=${page}&size=${size}`, {
      headers: {
        ...getAuthHeader(),
      },
    });
    handleUnauthorized(response);
    if (!response.ok) throw new Error('Failed to fetch my books');
    const data = await response.json();
    return {
      ...data,
      content: data.content.map(resolveBookImages),
    };
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
    handleUnauthorized(response);
    if (!response.ok) throw new Error('Failed to delete book');
  },

  // Toggle like on a book card (like/unlike)
  async likeBook(id) {
    const response = await fetch(`${API_BASE_URL}/books/${id}/like`, {
      method: 'POST',
      headers: {
        ...getAuthHeader(),
      },
    });
    handleUnauthorized(response);
    if (!response.ok) throw new Error('Failed to like book');
    return await response.json();
  },

  // Check if current user has liked a book
  async getLikeStatus(id) {
    const response = await fetch(`${API_BASE_URL}/books/${id}/like`, {
      headers: {
        ...getAuthHeader(),
      },
    });
    handleUnauthorized(response);
    if (!response.ok) throw new Error('Failed to get like status');
    return await response.json();
  },
};

export default bookApi;
