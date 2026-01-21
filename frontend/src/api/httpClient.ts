export async function httpRequest<T>(
    request: () => Promise<Response>
): Promise<T> {
  const response = await request();

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || response.statusText);
  }

  // 204 No Content → kein Body
  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();

  // leerer Body → void
  if (!text) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch (e) {
    throw new Error("Invalid JSON response from server");
  }
}
