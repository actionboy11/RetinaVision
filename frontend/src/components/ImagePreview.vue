<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  imageUrl: string
  title?: string
  filename?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const loadFailed = ref(false)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

watch(
  () => [props.modelValue, props.imageUrl],
  () => {
    loading.value = Boolean(props.modelValue && props.imageUrl)
    loadFailed.value = false
  },
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

<style scoped>
.preview-shell {
  min-height: 360px;
}

.preview-filename {
  margin-bottom: 12px;
  color: #4b5563;
  font-size: 14px;
}

.preview-empty,
.preview-image-wrap {
  display: grid;
  min-height: 360px;
  place-items: center;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.preview-image {
  display: block;
  max-width: 100%;
  max-height: 68vh;
  object-fit: contain;
}
</style>
