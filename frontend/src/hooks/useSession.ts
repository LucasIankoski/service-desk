import { useQuery } from "@tanstack/react-query";
import { getMe } from "../api/auth";
import { ApiError } from "../api/http";

export function useSession() {
  return useQuery({
    queryKey: ["me"],
    queryFn: getMe,
    retry: false,
    throwOnError: false,
    staleTime: 60_000
  });
}

export function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}
