/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  distDir: '../../releases',
  images: {
    unoptimized: true
  },
  // trailingSlash 제거: Query Parameter 방식 사용
}

module.exports = nextConfig