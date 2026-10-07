<script setup lang="ts">
import {
  Checked,
  ChatDotRound,
  Cpu,
  DataAnalysis,
  Document,
  Files,
  Plus,
  Setting,
  SwitchButton,
  User,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getDoctorReviewReminders } from '@/api/doctor'
import { useAuthStore } from '@/stores/auth'
import type { DoctorReviewReminder } from '@/types/doctor'
import { userRoleTextMap } from '@/utils/enums'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const reminder = ref<DoctorReviewReminder | null>(null)
const reminderTimer = ref<ReturnType<typeof setInterval> | null>(null)
const lastOverdueNoticeAt = ref(0)

const activeMenu = computed(() => {
  if (route.path.startsWith('/cases')) {
    if (authStore.user?.roleCode === 'USER' && route.query.mode) {
      return `/cases?mode=${String(route.query.mode)}`
    }
    return '/cases'
  }

  if (route.path === '/tasks/create') {
    return '/tasks/create'
  }

  if (route.path.startsWith('/knowledge')) {
    return '/knowledge'
  }

  if (route.path.startsWith('/agent')) {
    return '/agent'
  }

  if (route.path.startsWith('/quality-control')) {
    return '/quality-control'
  }

  if (route.path.startsWith('/admin/prompts')) {
    return '/admin/prompts'
  }
  if (route.path.startsWith('/admin/agent-skills')) return '/admin/agent-skills'

  if (route.path.startsWith('/prompt-evaluations')) {
    return '/prompt-evaluations'
  }

  if (route.path.startsWith('/rag-evaluations')) {
    return '/rag-evaluations'
  }

  if (route.path.startsWith('/tasks')) {
    return '/tasks'
  }

  return route.path
})

const displayName = computed(() => {
  return authStore.user?.realName || authStore.user?.username || '未命名用户'
})

const roleText = computed(() => {
  const roleCode = authStore.user?.roleCode
  return roleCode ? userRoleTextMap[roleCode] : '未知角色'
})

const agentMenuText = computed(() => {
  if (authStore.user?.roleCode === 'DOCTOR') return '临床工作助手'
  if (authStore.user?.roleCode === 'USER') return '检查助手'
  return '平台运维助手'
})

const pageTitle = computed(() => route.path.startsWith('/agent') ? agentMenuText.value : route.meta.title)

const totalDoctorTodoCount = computed(() => {
  if (!reminder.value) {
    return 0
  }

  return reminder.value.pendingReviewCount + reminder.value.pendingReportCount
})

const overdueDoctorTodoCount = computed(() => {
  if (!reminder.value) {
    return 0
  }

  return reminder.value.overdueReviewCount + reminder.value.overdueReportCount
})

const navigateTo = (path: string) => {
  void router.push(path)
}

const stopDoctorReminderPolling = () => {
  if (reminderTimer.value) {
    clearInterval(reminderTimer.value)
    reminderTimer.value = null
  }
  reminder.value = null
}

const showOverdueNotification = () => {
  const overdueCount = overdueDoctorTodoCount.value

  if (overdueCount <= 0) {
    return
  }

  const now = Date.now()
  const tenMinutes = 10 * 60 * 1000
  if (now - lastOverdueNoticeAt.value < tenMinutes) {
    return
  }

  lastOverdueNoticeAt.value = now
  const preview = reminder.value?.latestOverdueItems
    ?.slice(0, 3)
    .map((item) => `${item.caseNo || '未知病例'}：${item.reason}`)
    .join('；')

  ElNotification.warning({
    title: '医生审核待办提醒',
    message: `还有 ${overdueCount} 个超时未处理任务。${preview || '点击前往医生审核工作台。'}`,
    duration: 10000,
    onClick: () => {
      void router.push('/doctor/reviews')
    },
  })
}

const loadDoctorReminder = async () => {
  if (authStore.user?.roleCode !== 'DOCTOR') {
    stopDoctorReminderPolling()
    return
  }

  try {
    reminder.value = await getDoctorReviewReminders()
    showOverdueNotification()
  } catch {
    // 医生提醒是辅助能力，请求失败不能影响病例、任务和审核主流程。
  }
}

const startDoctorReminderPolling = () => {
  if (authStore.user?.roleCode !== 'DOCTOR') {
    stopDoctorReminderPolling()
    return
  }

  if (!reminderTimer.value) {
    void loadDoctorReminder()
    reminderTimer.value = setInterval(() => {
      void loadDoctorReminder()
    }, 60 * 1000)
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确认退出当前登录账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  await authStore.logoutAction()
  stopDoctorReminderPolling()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}

watch(
  () => authStore.user?.roleCode,
  () => {
    startDoctorReminderPolling()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopDoctorReminderPolling()
})
</script>

<template>
  <el-container class="app-shell">
    <el-aside class="app-aside" width="224px">
      <div class="brand">
        <div class="brand-mark">RV</div>
        <div>
          <div class="brand-title">RetinaVision</div>
          <div class="brand-subtitle">AI 分析管理</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="side-menu"
        @select="navigateTo"
      >
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>统计看板</span>
        </el-menu-item>
        <el-menu-item
          v-if="['RESEARCHER', 'ADMIN'].includes(authStore.user?.roleCode || '')"
          index="/knowledge"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ authStore.user?.roleCode === 'ADMIN' ? '知识库与问答' : '知识助手' }}</span>
        </el-menu-item>
        <el-menu-item
          v-if="['USER', 'DOCTOR', 'ADMIN'].includes(authStore.user?.roleCode || '')"
          index="/agent"
        >
          <el-icon><Cpu /></el-icon>
          <span>{{ agentMenuText }}</span>
        </el-menu-item>
        <el-menu-item
          v-if="authStore.user?.roleCode === 'ADMIN'"
          index="/quality-control"
        >
          <el-icon><WarningFilled /></el-icon>
          <span>AI 质控</span>
        </el-menu-item>
        <el-menu-item v-if="['USER', 'DOCTOR'].includes(authStore.user?.roleCode || '')" index="/cases">
          <el-icon><Files /></el-icon>
          <span>{{ authStore.user?.roleCode === 'USER' ? '我的病例' : '患者管理' }}</span>
        </el-menu-item>
        <template v-if="authStore.user?.roleCode === 'USER'">
          <el-menu-item index="/cases?mode=new"><el-icon><Plus /></el-icon><span>新建检查</span></el-menu-item>
          <el-menu-item index="/cases?mode=progress"><el-icon><Document /></el-icon><span>检查进度</span></el-menu-item>
          <el-menu-item index="/cases?mode=reports"><el-icon><Checked /></el-icon><span>正式报告</span></el-menu-item>
        </template>
        <el-menu-item v-if="authStore.user?.roleCode === 'DOCTOR'" index="/tasks">
          <el-icon><Document /></el-icon>
          <span>分析任务</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'DOCTOR'" index="/doctor/reviews">
          <el-icon><Checked /></el-icon>
          <el-badge
            :hidden="totalDoctorTodoCount === 0"
            :value="totalDoctorTodoCount"
            :type="overdueDoctorTodoCount > 0 ? 'danger' : 'primary'"
            class="menu-badge"
          >
            <span>医生审核</span>
          </el-badge>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户角色</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/admin/prompts">
          <el-icon><Setting /></el-icon>
          <span>Prompt 管理</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/admin/agent-skills">
          <el-icon><Cpu /></el-icon>
          <span>Agent Skill</span>
        </el-menu-item>
        <el-menu-item
          v-if="authStore.user?.roleCode === 'ADMIN'"
          index="/prompt-evaluations"
        >
          <el-icon><Checked /></el-icon>
          <span>Prompt 评测</span>
        </el-menu-item>
        <el-menu-item
          v-if="authStore.user?.roleCode === 'ADMIN'"
          index="/rag-evaluations"
        >
          <el-icon><Checked /></el-icon>
          <span>RAG 评测</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div>
          <div class="page-title">{{ pageTitle }}</div>
          <div class="page-subtitle">眼底图像 AI 批量分析与报告管理系统</div>
        </div>

        <div class="header-actions">
          <div class="user-summary">
            <el-icon><User /></el-icon>
            <div>
              <div class="user-name">{{ displayName }}</div>
              <div class="user-role">{{ roleText }}</div>
            </div>
          </div>
          <el-button :icon="SwitchButton" plain @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f5f7fb;
}

.app-aside {
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #edf0f5;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 6px;
  background: #1f7a8c;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.brand-title {
  color: #1f2937;
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
}

.brand-subtitle {
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.side-menu {
  border-right: 0;
  padding: 12px 8px;
}

.menu-badge {
  line-height: 20px;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.page-title {
  color: #111827;
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
}

.page-subtitle {
  color: #6b7280;
  font-size: 13px;
  line-height: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #374151;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.user-role {
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.app-main {
  padding: 24px;
}

@media (max-width: 620px) {
  .app-header {
    padding: 0 12px;
  }

  .page-subtitle,
  .user-summary {
    display: none;
  }

  .header-actions {
    gap: 0;
  }

  .header-actions :deep(.el-button) {
    width: 34px;
    height: 34px;
    padding: 0;
  }

  .header-actions :deep(.el-button span) {
    display: none;
  }

  .app-main {
    padding: 12px;
  }
}
</style>
