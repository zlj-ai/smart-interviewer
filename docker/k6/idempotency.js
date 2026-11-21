// k6 压测脚本：幂等并发创建（验证三级短路不产生重复会话）
// 运行：k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> docker/k6/idempotency.js
// 产出：0 条重复记录即通过（幂等命中分布见应用日志）
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

export const options = {
  scenarios: {
    idempotent_create: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 500,
      maxDuration: '2m',
    },
  },
};

const REQUEST_ID = `k6-${__VU}-${Math.floor(Date.now() / 1000)}`;

export default function () {
  const res = http.post(`${BASE_URL}/api/interview/sessions`,
    JSON.stringify({
      requestId: REQUEST_ID,
      direction: 'JAVA_BACKEND',
      difficulty: 'MEDIUM',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`,
      },
    });
  check(res, {
    'status 200': (r) => r.status === 200,
    'business ok': (r) => JSON.parse(r.body).success === true,
  });
}
