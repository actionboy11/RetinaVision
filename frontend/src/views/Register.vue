<script setup lang="ts">
import { Lock, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { register } from '@/api/auth'
import type { RegisterRequest } from '@/types/auth'

interface RegisterForm extends RegisterRequest {
  confirmPassword: string
}

const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  realName: '',
  roleCode: 'USER',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }

  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }

  callback()
}

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      min: 3,
      max: 32,
      message: '用户名长度为 3-32 个字符',
      trigger: 'blur',
    },
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    {
      min: 2,
      max: 32,
      message: '真实姓名长度为 2-32 个字符',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      min: 6,
      max: 64,
      message: '密码长度为 6-64 个字符',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
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
    await register({
      username: form.username.trim(),
      realName: form.realName.trim(),
      roleCode: form.roleCode,
      password: form.password,
    })
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
  } catch (error) {
    const message = error instanceof Error ? error.message : '注册失败'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}

const goToLogin = () => {
  void router.push('/login')
}
</script>

<template>
  <main class="register-page">
    <section class="register-panel">
      <div class="register-brand">
        <div class="brand-mark">RV</div>
        <div>
          <h1>患者注册</h1>
          <p>创建患者账号，系统将自动生成匿名患者编号</p>
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
            maxlength="32"
            placeholder="请输入用户名"
          />
        </el-form-item>
        <el-form-item label="显示名称" prop="realName">
          <el-input
            v-model.trim="form.realName"
            :prefix-icon="UserFilled"
            maxlength="32"
            placeholder="请输入用于登录后展示的名称"
          />
        </el-form-item>
        <el-alert title="公开注册仅创建患者账号；医生和研究员身份由管理员审核授予。临床病例只使用系统生成的匿名编号。" type="info" :closable="false" />
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            autocomplete="new-password"
            maxlength="64"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            :prefix-icon="Lock"
            autocomplete="new-password"
            maxlength="64"
            placeholder="请再次输入密码"
            show-password
            type="password"
          />
        </el-form-item>

        <el-button
          type="primary"
          :loading="submitting"
          class="register-button"
          @click="handleSubmit"
        >
          注册
        </el-button>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <el-button link type="primary" @click="goToLogin">返回登录</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: #f5f7fb;
}

.register-panel {
  width: min(460px, calc(100vw - 32px));
  padding: 32px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgb(15 23 42 / 8%);
}

.register-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.brand-mark {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 8px;
  background: #1f7a8c;
  color: #ffffff;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 32px;
}

p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.full-width,
.register-button {
  width: 100%;
}

.register-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 18px;
  color: #6b7280;
  font-size: 13px;
}
</style>
