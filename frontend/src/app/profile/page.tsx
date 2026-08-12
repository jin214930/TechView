import type { Metadata } from "next";

import { ProfilePage } from "@/features/auth/ui/profile-page";

export const metadata: Metadata = {
  title: "프로필 | TechView",
};

export default function ProfileRoute() {
  return <ProfilePage />;
}
