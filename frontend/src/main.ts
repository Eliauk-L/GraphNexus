import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/tokens.css'
import './assets/global.css'

const app = createApp(App)

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

app.use(createPinia())
app.use(router)
app.mount('#app')