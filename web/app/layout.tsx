import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Daily Thread Dashboard",
  description: "Daily Thread productivity and sync dashboard",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="id">
      <body>{children}</body>
    </html>
  );
}
