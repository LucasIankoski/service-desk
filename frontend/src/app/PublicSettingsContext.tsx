import { createContext, useContext } from "react";
import type { PublicSettings } from "../api/types";

export const PublicSettingsContext = createContext<PublicSettings | undefined>(undefined);

export function usePublicSettings() {
  return useContext(PublicSettingsContext);
}
