import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  experimental: {
    // Server Actions (star button, "Load broker data") reject requests whose Origin doesn't match
    // the host. Allow the Cloudflare quick-tunnel address used to share the site from the dev PC.
    serverActions: {
      allowedOrigins: ["*.trycloudflare.com"],
    },
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
