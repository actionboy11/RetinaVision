import { createRouter, createWebHistory } from 'vue-router'

import BasicLayout from '@/layouts/BasicLayout.vue'
import { useAuthStore } from '@/stores/auth'
import CaseList from '@/views/case/CaseList.vue'
import Dashboard from '@/views/Dashboard.vue'
import ImageUpload from '@/views/image/ImageUpload.vue'
import KnowledgeAssistant from '@/views/knowledge/KnowledgeAssistant.vue'
import Login from '@/views/Login.vue'
import QualityControl from '@/views/quality/QualityControl.vue'
import Register from '@/views/Register.vue'
import TaskCreate from '@/views/task/TaskCreate.vue'
import TaskDetail from '@/views/task/TaskDetail.vue'
import TaskList from '@/views/task/TaskList.vue'
import UserManagement from '@/views/admin/UserManagement.vue'
import PromptManagement from '@/views/admin/PromptManagement.vue'
import PromptEvaluation from '@/views/admin/PromptEvaluation.vue'
import RagEvaluation from '@/views/admin/RagEvaluation.vue'
import ReviewWorkbench from '@/views/doctor/ReviewWorkbench.vue'
import ClinicalAgent from '@/views/agent/ClinicalAgent.vue'
import AgentSkillManagement from '@/views/admin/AgentSkillManagement.vue'
import PatientCaseProgress from '@/views/case/PatientCaseProgress.vue'
import { getRoleHome } from '@/utils/role-home'

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
      component: Login,
      meta: {
        title: '登录',
        public: true,
      },
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
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
          component: ReviewWorkbench,
          meta: { title: '医生审核工作台', requiresAuth: true, doctorOnly: true },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: UserManagement,
          meta: { title: '用户角色管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'admin/prompts',
          name: 'admin-prompts',
          component: PromptManagement,
          meta: { title: 'Prompt 管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'admin/agent-skills',
          name: 'admin-agent-skills',
          component: AgentSkillManagement,
          meta: { title: 'Agent Skill 管理', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'prompt-evaluations',
          name: 'prompt-evaluations',
          component: PromptEvaluation,
          meta: { title: 'Prompt 评测', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'rag-evaluations',
          name: 'rag-evaluations',
          component: RagEvaluation,
          meta: { title: 'RAG 评测', requiresAuth: true, adminOnly: true },
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: Dashboard,
          meta: {
            title: '统计看板',
            requiresAuth: true,
            adminOnly: true,
          },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: KnowledgeAssistant,
          meta: {
            title: '知识助手',
            requiresAuth: true,
          },
        },
        {
          path: 'agent',
          name: 'agent',
          component: ClinicalAgent,
          meta: {
            title: '工作助手',
            requiresAuth: true,
            agentOnly: true,
          },
        },
        {
          path: 'quality-control',
          name: 'quality-control',
          component: QualityControl,
          meta: {
            title: 'AI 质控',
            requiresAuth: true,
            adminOnly: true,
          },
        },
        {
          path: 'cases',
          name: 'cases',
          component: CaseList,
          meta: {
            title: '病例管理',
            requiresAuth: true,
            clinicalOnly: true,
          },
        },
        {
          path: 'cases/:caseId/images',
          name: 'case-images',
          component: ImageUpload,
          meta: {
            title: '图像管理',
            requiresAuth: true,
            clinicalOnly: true,
          },
        },
        {
          path: 'cases/:caseId/progress',
          name: 'case-progress',
          component: PatientCaseProgress,
          meta: { title: '检查进度', requiresAuth: true, patientOnly: true },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: TaskList,
          meta: {
            title: '分析任务',
            requiresAuth: true,
            doctorOnly: true,
          },
        },
        {
          path: 'tasks/create',
          name: 'task-create',
          component: TaskCreate,
          meta: {
            title: '创建任务',
            requiresAuth: true,
            doctorOnly: true,
          },
        },
        {
          path: 'tasks/:taskId',
          name: 'task-detail',
          component: TaskDetail,
          meta: {
            title: '任务详情',
            requiresAuth: true,
            doctorOnly: true,
          },
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
  if (to.matched.some((record) => record.meta.doctorOnly) && authStore.user?.roleCode !== 'DOCTOR') {
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
  if (to.path.startsWith('/knowledge') && ['USER', 'DOCTOR'].includes(authStore.user?.roleCode || '')) {
    return '/agent'
  }
  return true
})

export default router
