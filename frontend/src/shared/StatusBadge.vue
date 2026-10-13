<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

const toneMap: Record<string, string> = {
  active: 'success',
  archived: 'default',
  PUBLISHED: 'success',
  ARCHIVED: 'default',
  DRAFT: 'default',
  PENDING_REVIEW: 'warn'
}
const labelMap: Record<string, string> = {
  active: '启用中',
  archived: '已归档',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  DRAFT: '草稿',
  PENDING_REVIEW: '待审阅'
}

const tone = computed(() => toneMap[props.status] || 'default')
const label = computed(() => labelMap[props.status] || props.status)
</script>

<template>
  <span class="badge" :class="tone"><span class="bdot"></span>{{ label }}</span>
</template>
