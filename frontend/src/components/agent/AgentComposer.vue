<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

// 与 TaskResultPanel / ImagePreview 保持一致，统一使用 defineModel
const question = defineModel<string>({ default: '' })

defineProps<{
  placeholder: string
  sending: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()
</script>

<template>
  <div class="composer">
    <el-input
      v-model="question"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 5 }"
      resize="none"
      :placeholder="placeholder"
      :disabled="sending"
      @keydown.ctrl.enter.prevent="emit('submit')"
    />
    <el-button
      type="primary"
      :icon="Search"
      :loading="sending"
      :disabled="sending || !question.trim()"
      @click="emit('submit')"
    >
      查询
    </el-button>
  </div>
</template>

<style scoped src="./AgentComposer.css"></style>
