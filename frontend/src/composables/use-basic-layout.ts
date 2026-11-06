import { ElMessage, ElMessageBox, ElNotification } from '@/plugins/element-plus-api'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getDoctorReviewReminders } from '@/api/doctor'
import { useAuthStore } from '@/stores/auth'
import type { DoctorReviewReminder } from '@/types/doctor'
import { userRoleTextMap } from '@/utils/enums'

/**
 * 路由前缀 → 高亮菜单项。
 * 顺序敏感：更具体的前缀必须排在通用前缀之前（例如 /agent-evaluations 先于 /agent，
 * /tasks/create 先于 /tasks）。新增页面时只需在此登记一行。
 */
const MENU_PREFIX_MAP: Array<[prefix: string, menu: string]> = [
  ['/agent-evaluations', '/agent-evaluations'],
  ['/admin/prompts', '/admin/prompts'],
  ['/admin/agent-skills', '/admin/agent-skills'],
  ['/prompt-evaluations', '/prompt-evaluations'],
  ['/rag-evaluations', '/rag-evaluations'],
  ['/quality-control', '/quality-control'],
  ['/knowledge', '/knowledge'],
  ['/agent', '/agent'],
  ['/tasks/create', '/tasks/create'],
  ['/tasks', '/tasks'],
  ['/cases', '/cases'],
]

/** 应用主框架的逻辑层：菜单高亮、用户展示、导航、医生待办提醒轮询与登出。 */
export const useBasicLayout = () => {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()

  const reminder = ref<DoctorReviewReminder | null>(null)
  const reminderTimer = ref<ReturnType<typeof setInterval> | null>(null)
  const lastOverdueNoticeAt = ref(0)

  const activeMenu = computed(() => {
    // 患者从「新建检查」入口进入时，保留 query 以高亮对应菜单项
    if (
      route.path.startsWith('/cases') &&
      authStore.user?.roleCode === 'USER' &&
      route.query.mode
    ) {
      return `/cases?mode=${String(route.query.mode)}`
    }
    const matched = MENU_PREFIX_MAP.find(([prefix]) => route.path.startsWith(prefix))
    return matched?.[1] ?? route.path
  })

  const displayName = computed(
    () => authStore.user?.realName || authStore.user?.username || '未命名用户',
  )

  const roleText = computed(() => {
    const roleCode = authStore.user?.roleCode
    return roleCode ? userRoleTextMap[roleCode] : '未知角色'
  })

  const agentMenuText = computed(() => {
    if (authStore.user?.roleCode === 'DOCTOR') return '临床工作助手'
    if (authStore.user?.roleCode === 'USER') return '检查助手'
    return '平台运维助手'
  })

  const pageTitle = computed(() =>
    route.path === '/agent' ? agentMenuText.value : route.meta.title,
  )

  const totalDoctorTodoCount = computed(() => {
    if (!reminder.value) return 0
    return reminder.value.pendingReviewCount + reminder.value.pendingReportCount
  })

  const overdueDoctorTodoCount = computed(() => {
    if (!reminder.value) return 0
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
    if (overdueCount <= 0) return

    const now = Date.now()
    const tenMinutes = 10 * 60 * 1000
    if (now - lastOverdueNoticeAt.value < tenMinutes) return

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

  return {
    authStore,
    activeMenu,
    displayName,
    roleText,
    agentMenuText,
    pageTitle,
    totalDoctorTodoCount,
    overdueDoctorTodoCount,
    navigateTo,
    handleLogout,
  }
}
