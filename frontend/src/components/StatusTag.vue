<script setup lang="ts">
import type { TaskStatus } from '@/types/task'
import { taskStatusTagTypeMap, taskStatusTextMap } from '@/utils/enums'

defineProps<{
  status: TaskStatus
}>()
</script>

<template>
  <el-tag :type="taskStatusTagTypeMap[status]">
    {{ taskStatusTextMap[status] }}
  </el-tag>
</template>
