/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    env: {
      // Эти переменные будут доступны на сервере (getServerSideProps и т.п.)
      API_URL: process.env.API_URL || 'http://gateway:8080',
    },
    publicRuntimeConfig: {
      // Для клиента используем NEXT_PUBLIC_*
    },
  };
  
  module.exports = nextConfig;