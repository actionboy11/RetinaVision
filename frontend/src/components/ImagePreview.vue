<script setup lang="ts">
import { ref, watch } from 'vue'

// 与 TaskResultPanel 保持一致，统一使用 defineModel 实现 v-model
const dialogVisible = defineModel<boolean>({ default: false })

const props = defineProps<{
  imageUrl: string
  title?: string
  filename?: string
}>()

const loading = ref(false)
const loadFailed = ref(false)

watch(
  () => [dialogVisible.value, props.imageUrl],
  () => {
    loading.value = Boolean(dialogVisible.value && props.imageUrl)
    loadFailed.value = false
  },
  // immediate 保证「挂载时对话框已打开」也显示 loading 态
  { immediate: true },
)

const handleLoad = () => {
  loading.value = false
  loadFailed.value = false
}

const handleError = () => {
  loading.value = false
  loadFailed.value = true
}
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="title || '图像预览'" width="72%">
    <div class="preview-shell">
      <div v-if="filename" class="preview-filename">{{ filename }}</div>

      <div v-if="!imageUrl" class="preview-empty">
        <el-empty description="暂无可预览图像" />
      </div>

      <div v-else v-loading="loading" class="preview-image-wrap">
        <el-empty v-if="loadFailed" description="图像加载失败" />
        <img
          v-show="!loadFailed"
          :alt="filename || title || '图像预览'"
          :src="imageUrl"
          class="preview-image"
          @error="handleError"
          @load="handleLoad"
        />
      </div>
    </div>
  </el-dialog>
</template>

<style scoped src="./ImagePreview.css"></style>
