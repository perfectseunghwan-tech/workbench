import 'primeicons/primeicons.css'
import './assets/tailwind.css'

// [추가] PrimeUI 라이선스 경고 콘솔 출력 차단
const originalWarn = console.warn
console.warn = (...args: any[]) => {
  if (typeof args[0] === 'string' && args[0].includes('invalid primeUI license')) return
  originalWarn(...args)
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { definePreset } from '@primevue/themes'
import Aura from '@primevue/themes/aura'

import App from './App.vue'
import router from './router'
import { btnStyles, paginatorStyles, selectStyles } from './utils/Styles'

const assetNotePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}',
    },
  },
})

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.use(PrimeVue, {
  theme: {
    preset: assetNotePreset,
    options: {
      darkModeSelector: false,
      cssLayer: false,
    },
  },
  pt: {
    button: btnStyles.body,
    select: selectStyles.body,
    paginator: paginatorStyles,
  },
})

app.mount('#app')
