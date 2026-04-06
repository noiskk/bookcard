const API_BASE_URL = 'http://localhost:8080/api';

const TOKEN_KEY = 'bookcard_token';

export const authApi = {
  async register({ email, password, nickname }) {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password, nickname }),
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const message = errorData.message || Object.values(errorData)[0] || '회원가입에 실패했습니다.';
      throw new Error(message);
    }

    const data = await response.json();
    localStorage.setItem(TOKEN_KEY, data.token);
    return data;
  },

  async login({ email, password }) {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || '이메일 또는 비밀번호가 올바르지 않습니다.');
    }

    const data = await response.json();
    localStorage.setItem(TOKEN_KEY, data.token);
    return data;
  },

  logout() {
    localStorage.removeItem(TOKEN_KEY);
  },

  getToken() {
    return localStorage.getItem(TOKEN_KEY);
  },

  isLoggedIn() {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  },

  // JWT payload를 디코딩해서 현재 로그인한 사용자의 이메일 반환
  getCurrentUserEmail() {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub || null;
    } catch {
      return null;
    }
  },
};

export default authApi;
