<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { getRoleHome } from '@/utils/role-home'

const router = useRouter()
const authStore = useAuthStore()
const homePath = computed(() => getRoleHome(authStore.user?.roleCode))
</script>

<template>
  <section class="not-found">
    <div class="not-found__code">404</div>
    <h1>页面不存在</h1>
    <p>您访问的地址无效或已被移除，请检查链接，或返回工作台继续操作。</p>
    <el-button type="primary" @click="router.push(homePath)">返回工作台</el-button>
  </section>
</template>

<style scoped src="./NotFound.css"></style>
