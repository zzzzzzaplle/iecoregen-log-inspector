const JSON_HEADERS = {
  Accept: "application/json",
};

async function request(path) {
  const response = await fetch(path, { headers: JSON_HEADERS });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function listLogs() {
  return request("/api/logs");
}

export function analyzeLog(id) {
  return request(`/api/logs/${encodeURIComponent(id)}/analysis`);
}

export function readLines(id, start, end) {
  return request(`/api/logs/${encodeURIComponent(id)}/lines?start=${start}&end=${end}`);
}
