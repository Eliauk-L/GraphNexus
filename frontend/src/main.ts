import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { registerAuthGuard } from './router/authGuard'
import { setupInterceptors } from './api/interceptor'
import './assets/tokens.css'
import './assets/global.css'

const app = createApp(App)

// Pinia 必须先安装（拦截器和守卫依赖 store）
const pinia = createPinia()
app.use(pinia)
app.use(router)

// 注册 axios 拦截器（401 自动刷新 · 直接读 localStorage 不依赖 Pinia）
setupInterceptors()

// 注册路由守卫（认证 + 权限）
registerAuthGuard(router)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue Error]', err)
  console.error('[Vue Error Info]', info)
  // 在页面上显示错误以便调试
  const appEl = document.getElementById('app')
  if (appEl && !appEl.textContent?.trim()) {
    appEl.innerHTML = `<div style="padding:32px;color:red;font-family:monospace;">
      <h2>应用启动失败</h2>
      <pre>${String(err)}</pre>
      <p>请打开浏览器控制台查看详细错误</p>
    </div>`
  }
}

app.mount('#app')