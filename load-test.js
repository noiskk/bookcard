import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 10 },  // 10초 동안 10명으로 증가
        { duration: '20s', target: 50 },  // 20초 동안 50명 유지
        { duration: '10s', target: 0 },   // 10초 동안 종료
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95%의 요청이 500ms 이내
        http_req_failed: ['rate<0.01'],   // 에러율 1% 미만
    },
};

export default function () {
    const res = http.get('http://localhost:8080/api/books/paged?page=0&size=12');
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(1);
}
