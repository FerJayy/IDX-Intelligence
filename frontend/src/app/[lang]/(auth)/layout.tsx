import { AuthHeader } from "@/components/auth-header";

/** Log-in, register and password pages: only the name and the language/theme switches, no menu. */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <AuthHeader />
      {children}
    </>
  );
}
