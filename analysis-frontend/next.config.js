/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/proxy/arch-graph/:path*',
        destination: 'http://architect-graph-service:8080/:path*',
      },
    ];
  },
};

module.exports = nextConfig;