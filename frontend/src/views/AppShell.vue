<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const collapsed = ref(false)
const mobileOpen = ref(false)
const isMobile = ref(false)
let mediaQuery: MediaQueryList | undefined

const navigation = [
  {
    label: '투자 관리',
    items: [
      { label: '거래 내역', icon: 'pi pi-chart-line', to: '/' },
      { label: '증권사·계좌 관리', icon: 'pi pi-wallet', to: '/settings/accounts' },
    ],
  },
  {
    label: '시스템',
    items: [{ label: '서버 연결 확인', icon: 'pi pi-link', to: '/connection' }],
  },
]

const pageTitle = computed(() => String(route.meta.title ?? 'Asset Note'))

function updateViewport(event?: MediaQueryListEvent) {
  isMobile.value = event?.matches ?? mediaQuery?.matches ?? false
  if (!isMobile.value) mobileOpen.value = false
}

function toggleSidebar() {
  if (isMobile.value) mobileOpen.value = !mobileOpen.value
  else collapsed.value = !collapsed.value
}

function closeMobileSidebar() {
  if (isMobile.value) mobileOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') mobileOpen.value = false
}

watch(() => route.fullPath, closeMobileSidebar)
watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 760px)')
  updateViewport()
  mediaQuery.addEventListener('change', updateViewport)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', updateViewport)
  window.removeEventListener('keydown', onKeydown)
  document.body.style.removeProperty('overflow')
})
</script>

<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'sidebar-open': mobileOpen }">
    <aside id="app-sidebar" class="sidebar" aria-label="주 메뉴">
      <RouterLink class="sidebar-brand" to="/" aria-label="Asset Note 홈" @click="closeMobileSidebar">
        <span class="brand-symbol"><i class="pi pi-chart-line"></i></span>
        <span class="brand-name">Asset Note</span>
      </RouterLink>

      <nav class="sidebar-nav">
        <section v-for="group in navigation" :key="group.label" class="nav-group">
          <p>{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="nav-item"
            :title="collapsed && !isMobile ? item.label : undefined"
            @click="closeMobileSidebar"
          >
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>

      <div class="sidebar-footer">
        <span class="footer-icon"><i class="pi pi-sparkles"></i></span>
        <span><strong>Asset Note</strong><small>나만의 투자 기록</small></span>
      </div>
    </aside>

    <button v-if="mobileOpen" class="sidebar-backdrop" type="button" aria-label="메뉴 닫기" @click="closeMobileSidebar"></button>

    <div class="app-column">
      <header class="app-topbar">
        <div class="topbar-start">
          <button class="menu-toggle" type="button" aria-label="메뉴 열기 또는 접기" aria-controls="app-sidebar" :aria-expanded="isMobile ? mobileOpen : !collapsed" @click="toggleSidebar">
            <i class="pi pi-bars"></i>
          </button>
          <span class="topbar-divider"></span>
          <span class="topbar-title">{{ pageTitle }}</span>
        </div>
        <div class="topbar-tools" aria-label="사용자 도구">
          <button type="button" title="검색"><i class="pi pi-search"></i></button>
          <button type="button" title="알림"><i class="pi pi-bell"></i></button>
          <span class="profile-avatar" aria-label="사용자 프로필">AN</span>
        </div>
      </header>

      <main class="app-content"><RouterView /></main>
    </div>
  </div>
</template>

<style scoped>
.app-shell{--sidebar-width:238px;--topbar-height:64px;min-height:100vh;color:#25324b;background:#f5f7fb}
.sidebar{position:fixed;inset:0 auto 0 0;z-index:60;display:flex;width:var(--sidebar-width);flex-direction:column;overflow:hidden;background:#f7f8fc;border-right:1px solid #e7ebf3;transition:width .22s ease,transform .22s ease}
.sidebar-brand{display:flex;min-height:var(--topbar-height);align-items:center;gap:10px;padding:0 20px;color:#263551;text-decoration:none;white-space:nowrap}
.brand-symbol{display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;color:#fff;background:linear-gradient(145deg,#7567ef,#5a68e9);border-radius:10px;box-shadow:0 6px 16px rgba(102,98,235,.25)}
.brand-name{font-size:18px;font-weight:800;letter-spacing:-.03em}
.sidebar-nav{flex:1;overflow-y:auto;padding:18px 10px}
.nav-group+.nav-group{margin-top:26px}
.nav-group>p{margin:0 10px 8px;color:#a4adc0;font-size:9px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;white-space:nowrap}
.nav-item{position:relative;display:flex;min-height:42px;align-items:center;gap:12px;margin:3px 0;padding:0 12px;color:#68738c;border-radius:7px;font-size:13px;font-weight:650;text-decoration:none;white-space:nowrap;transition:color .16s ease,background .16s ease}
.nav-item>i{width:20px;flex:0 0 20px;color:#9aa4b8;font-size:15px;text-align:center}
.nav-item:hover{color:#625ce7;background:#f0efff}.nav-item.router-link-exact-active{color:#645ee9;background:#eeedff}.nav-item.router-link-exact-active::before{position:absolute;left:-10px;width:3px;height:24px;background:#6c63ed;border-radius:0 4px 4px 0;content:''}.nav-item.router-link-exact-active>i{color:#645ee9}
.sidebar-footer{display:flex;align-items:center;gap:10px;margin:12px;padding:14px;overflow:hidden;color:#5e6780;background:#efefff;border-radius:10px;white-space:nowrap}.footer-icon{display:grid;width:34px;height:34px;flex:0 0 34px;place-items:center;color:#665ee9;background:#fff;border-radius:50%}.sidebar-footer>span:last-child{display:grid;gap:3px}.sidebar-footer strong{font-size:12px}.sidebar-footer small{color:#929ab0;font-size:10px}
.app-column{min-height:100vh;margin-left:var(--sidebar-width);transition:margin-left .22s ease}.app-topbar{position:sticky;top:0;z-index:40;display:flex;height:var(--topbar-height);align-items:center;justify-content:space-between;padding:0 22px;background:rgba(255,255,255,.95);border-bottom:1px solid #e7ebf3;backdrop-filter:blur(12px)}.topbar-start,.topbar-tools{display:flex;align-items:center}.menu-toggle,.topbar-tools button{display:grid;width:36px;height:36px;padding:0;place-items:center;color:#758198;background:transparent;border:0;border-radius:8px}.menu-toggle:hover,.topbar-tools button:hover{color:#625ce7;background:#f1f2f8}.topbar-divider{width:1px;height:20px;margin:0 16px 0 8px;background:#e6e9f0}.topbar-title{color:#36425b;font-size:13px;font-weight:750}.topbar-tools{gap:5px}.profile-avatar{display:grid;width:34px;height:34px;margin-left:6px;place-items:center;color:#fff;background:linear-gradient(145deg,#283b63,#526b9b);border:3px solid #edf0f6;border-radius:50%;font-size:10px;font-weight:800}.app-content{min-width:0}
.sidebar-collapsed{--sidebar-width:64px}.sidebar-collapsed .sidebar-brand{justify-content:center;padding:0}.sidebar-collapsed .brand-name,.sidebar-collapsed .nav-group>p,.sidebar-collapsed .nav-item span,.sidebar-collapsed .sidebar-footer>span:last-child{display:none}.sidebar-collapsed .sidebar-nav{padding-inline:8px}.sidebar-collapsed .nav-group+.nav-group{margin-top:18px;padding-top:18px;border-top:1px solid #e2e6ef}.sidebar-collapsed .nav-item{justify-content:center;padding:0}.sidebar-collapsed .nav-item.router-link-exact-active::before{left:-8px}.sidebar-collapsed .sidebar-footer{justify-content:center;margin:8px;padding:7px}.sidebar-backdrop{position:fixed;inset:0;z-index:50;width:100%;padding:0;background:rgba(24,32,48,.42);border:0;border-radius:0}
@media(max-width:760px){.app-shell{--sidebar-width:238px}.sidebar{box-shadow:18px 0 50px rgba(25,34,52,.16);transform:translateX(-100%)}.sidebar-open .sidebar{transform:translateX(0)}.app-column{margin-left:0}.app-topbar{padding:0 13px}.topbar-divider{margin-right:12px}.topbar-tools button:first-child{display:none}}
@media (min-width: 761px) {
  .sidebar-collapsed .sidebar:hover {
    width: 238px;
    box-shadow: 18px 0 48px rgba(28, 38, 58, .12);
  }

  .sidebar-collapsed .sidebar:hover .sidebar-brand {
    justify-content: flex-start;
    padding: 0 20px;
  }

  .sidebar-collapsed .sidebar:hover .brand-name,
  .sidebar-collapsed .sidebar:hover .nav-group > p,
  .sidebar-collapsed .sidebar:hover .nav-item span,
  .sidebar-collapsed .sidebar:hover .nav-group > p { display: block; }
  .sidebar-collapsed .sidebar:hover .sidebar-footer > span:last-child { display: grid; }

  .sidebar-collapsed .sidebar:hover .sidebar-nav { padding-inline: 10px; }
  .sidebar-collapsed .sidebar:hover .nav-group + .nav-group {
    margin-top: 26px;
    padding-top: 0;
    border-top: 0;
  }
  .sidebar-collapsed .sidebar:hover .nav-item {
    justify-content: flex-start;
    padding: 0 12px;
  }
  .sidebar-collapsed .sidebar:hover .nav-item.router-link-exact-active::before { left: -10px; }
  .sidebar-collapsed .sidebar:hover .sidebar-footer {
    justify-content: flex-start;
    margin: 12px;
    padding: 14px;
  }
}
</style>
