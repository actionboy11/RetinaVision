<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: string
  placeholder: string
  sending: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

const updateValue = (value: string) => emit('update:modelValue', value)
</script>

<template>
  <div class="composer">
    <el-input
      :model-value="props.modelValue"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 5 }"
      resize="none"
      :placeholder="placeholder"
      :disabled="sending"
      @update:model-value="updateValue"
      @keydown.ctrl.enter.prevent="emit('submit')"
    />
    <el-button
      type="primary"
      :icon="Search"
      :loading="sending"
      :disabled="sending || !props.modelValue.trim()"
      @click="emit('submit')"
    >
      查询
    </el-button>
  </div>
</template>

<style scoped>
.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: end;
}

.composer :deep(.el-textarea__inner) {
  min-height: 54px !important;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #d5dde5 inset;
}

.composer .el-button {
  min-width: 82px;
  height: 38px;
}

@media (max-width: 540px) {
  .composer {
    grid-template-columns: 1fr;
  }

  .composer .el-button {
    width: 100%;
  }
}
</style>
