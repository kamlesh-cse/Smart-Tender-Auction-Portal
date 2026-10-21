import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 20,
  duration: "30s",
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";

export default function () {
  const response = http.get(`${baseUrl}/actuator/health`);
  check(response, { "health responds": (result) => result.status === 200 });
  sleep(1);
}
