<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from '@/plugins/element-plus-api'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { getRoleHome } from '@/utils/role-home'
import type { LoginRequest } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<LoginRequest>({
  username: '',
  password: '',
})

const rules: FormRules<LoginRequest> = {
  username: [
    {
      required: true,
      message: '请输入用户名',
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
    },
  ],
}

const getRedirectPath = () => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/')
    ? redirect
    : getRoleHome(authStore.user?.roleCode)
}

const handleSubmit = async () => {
  if (submitting.value) {
    return
  }

  const valid = await formRef.value?.validate().catch(() => false)

  if (!valid) {
    return
  }

  submitting.value = true

  try {
    await authStore.loginAction(form)
    ElMessage.success('登录成功')
    await router.replace(getRedirectPath())
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}

const goToRegister = () => {
  void router.push('/register')
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <div class="brand-mark">RV</div>
        <div>
          <h1>RetinaVision</h1>
          <p>眼底图像 AI 分析任务管理系统</p>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            :prefix-icon="User"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="login-button" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <el-button link type="primary" @click="goToRegister">去注册</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped src="./Login.css"></style>
