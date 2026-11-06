import { createRouter, createWebHistory } from 'vue-router'

import BasicLayout from '@/layouts/BasicLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { getRoleHome } from '@/utils/role-home'

// 页面级一律懒加载：admin / agent 等重组件不进入首屏 bundle。
// BasicLayout 是登录后的公共骨架，保持静态引入避免路由切换时闪烁。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: {
        title: '登录',
        public: true,
      },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: {
        title: '患者注册',
        public: true,
      },
    },
    {
      path: '/',
      component: BasicLayout,
      meta: {
        requiresAuth: true,
      },
      children: [
        {
          path: 'doctor/reviews',
          name: 'doctor-reviews',
          component: () => import('@/views/doctor/ReviewWorkbench.vue'),
          meta: { title: '医生审核工作台', requiresAuth: true, doctorOnly: true },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserManagement.vue'),
          meta: { title: '用户角色管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'admin/prompts',
          name: 'admin-prompts',
          component: () => import('@/views/admin/PromptManagement.vue'),
          meta: { title: 'Prompt 管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'admin/agent-skills',
          name: 'admin-agent-skills',
          component: () => import('@/views/admin/AgentSkillManagement.vue'),
          meta: { title: 'Agent Skill 管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'agent-evaluations',
          name: 'agent-evaluations',
          component: () => import('@/views/admin/AgentEvaluation.vue'),
          meta: { title: 'Agent 评测', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'prompt-evaluations',
          name: 'prompt-evaluations',
          component: () => import('@/views/admin/PromptEvaluation.vue'),
          meta: { title: 'Prompt 评测', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'rag-evaluations',
          name: 'rag-evaluations',
          component: () => import('@/views/admin/RagEvaluation.vue'),
          meta: { title: 'RAG 评测', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: {
            title: '统计看板',
            requiresAuth: true,
            adminOnly: true,
          },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/KnowledgeAssistant.vue'),
          meta: {
            title: '知识助手',
            requiresAuth: true,
          },
        },
        {
          path: 'agent',
          name: 'agent',
          component: () => import('@/views/agent/ClinicalAgent.vue'),
          meta: {
            title: '工作助手',
            requiresAuth: true,
            agentOnly: true,
          },
        },
        {
          path: 'quality-control',
          name: 'quality-control',
          component: () => import('@/views/quality/QualityControl.vue'),
          meta: {
            title: 'AI 质控',
            requiresAuth: true,
            adminOnly: true,
          },
        },
        {
          path: 'cases',
          name: 'cases',
          component: () => import('@/views/case/CaseList.vue'),
          meta: {
            title: '病例管理',
            requiresAuth: true,
            clinicalOnly: true,
          },
        },
        {
          path: 'cases/:caseId/images',
          name: 'case-images',
          component: () => import('@/views/image/ImageUpload.vue'),
          meta: {
            title: '图像管理',
            requiresAuth: true,
            clinicalOnly: true,
          },
        },
        {
          path: 'cases/:caseId/progress',
          name: 'case-progress',
          component: () => import('@/views/case/PatientCaseProgress.vue'),
          meta: { title: '检查进度', requiresAuth: true, patientOnly: true },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/task/TaskList.vue'),
          meta: {
            title: '分析任务',
            requiresAuth: true,
            doctorOnly: true,
          },
        },
        {
          path: 'tasks/create',
          name: 'task-create',
          component: () => import('@/views/task/TaskCreate.vue'),
          meta: {
            title: '创建任务',
            requiresAuth: true,
            doctorOnly: true,
          },
        },
        {
          path: 'tasks/:taskId',
          name: 'task-detail',
          component: () => import('@/views/task/TaskDetail.vue'),
          meta: {
            title: '任务详情',
            requiresAuth: true,
            doctorOnly: true,
          },
        },
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('@/views/NotFound.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const needsAuth = to.matched.some((record) => record.meta.requiresAuth)

  if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
    if (!authStore.sessionValidated) {
      try {
        await authStore.fetchCurrentUser()
      } catch {
        authStore.resetAuth()
        return true
      }
    }

    return getRoleHome(authStore.user?.roleCode)
  }

  if (!needsAuth) {
    return true
  }

  if (!authStore.isLoggedIn) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (!authStore.sessionValidated) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      authStore.resetAuth()
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  if (to.matched.some((record) => record.meta.adminOnly) && authStore.user?.roleCode !== 'ADMIN') {
    return getRoleHome(authStore.user?.roleCode)
  }
  if (
    to.matched.some((record) => record.meta.doctorOnly) &&
    authStore.user?.roleCode !== 'DOCTOR'
  ) {
    return getRoleHome(authStore.user?.roleCode)
  }
  if (
    to.matched.some((record) => record.meta.agentOnly) &&
    !['USER', 'DOCTOR', 'ADMIN'].includes(authStore.user?.roleCode || '')
  ) {
    return getRoleHome(authStore.user?.roleCode)
  }
  if (
    to.matched.some((record) => record.meta.clinicalOnly) &&
    !['USER', 'DOCTOR'].includes(authStore.user?.roleCode || '')
  ) {
    return getRoleHome(authStore.user?.roleCode)
  }
  if (to.matched.some((record) => record.meta.patientOnly) && authStore.user?.roleCode !== 'USER') {
    return getRoleHome(authStore.user?.roleCode)
  }
  if (
    to.path.startsWith('/knowledge') &&
    ['USER', 'DOCTOR'].includes(authStore.user?.roleCode || '')
  ) {
    return '/agent'
  }
  return true
})

export default router
