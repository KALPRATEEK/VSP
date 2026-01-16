export class ApiError extends Error {
  public readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.status = status;
  }
}


export async function httpRequest<T>(
  request: () => Promise<Response>
): Promise<T> {
  let response: Response;

  try {
    response = await request();
  } catch {
    throw new ApiError("Backend not reachable");
  }

  if (!response.ok) {
    throw new ApiError(
      `Request failed with status ${response.status}`,
      response.status
    );
  }

  return response.json() as Promise<T>;
}
