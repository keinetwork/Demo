// vue-cli-service의 개발 서버(webpack-dev-server) 설정.
// /api로 시작하는 요청을 백엔드(demo-mybatis-pgsql, 기본 포트 8080)로 그대로 전달해
// 개발 중에도 CORS 설정 없이 axios 호출이 되도록 한다.
module.exports = {
  devServer: {
    port: 8081,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
}
