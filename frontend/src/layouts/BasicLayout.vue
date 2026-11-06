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

import { useBasicLayout } from '@/composables/use-basic-layout'

const {
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
} = useBasicLayout()
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

      <el-menu :default-active="activeMenu" class="side-menu" @select="navigateTo">
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
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/quality-control">
          <el-icon><WarningFilled /></el-icon>
          <span>AI 质控</span>
        </el-menu-item>
        <el-menu-item
          v-if="['USER', 'DOCTOR'].includes(authStore.user?.roleCode || '')"
          index="/cases"
        >
          <el-icon><Files /></el-icon>
          <span>{{ authStore.user?.roleCode === 'USER' ? '我的病例' : '患者管理' }}</span>
        </el-menu-item>
        <template v-if="authStore.user?.roleCode === 'USER'">
          <el-menu-item index="/cases?mode=new"
            ><el-icon><Plus /></el-icon><span>新建检查</span></el-menu-item
          >
          <el-menu-item index="/cases?mode=progress"
            ><el-icon><Document /></el-icon><span>检查进度</span></el-menu-item
          >
          <el-menu-item index="/cases?mode=reports"
            ><el-icon><Checked /></el-icon><span>正式报告</span></el-menu-item
          >
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
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/agent-evaluations">
          <el-icon><DataAnalysis /></el-icon>
          <span>Agent 评测</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/prompt-evaluations">
          <el-icon><Checked /></el-icon>
          <span>Prompt 评测</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.user?.roleCode === 'ADMIN'" index="/rag-evaluations">
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
          <el-button :icon="SwitchButton" plain @click="handleLogout"> 退出登录 </el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped src="./BasicLayout.css"></style>
