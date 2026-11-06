import { createPinia } from 'pinia'
import { createApp } from 'vue'

import 'element-plus/dist/index.css'

import App from './App.vue'
import { setupElementPlus } from './plugins/element-plus'
import router from './router'
import './styles/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
setupElementPlus(app)

app.mount('#app')
