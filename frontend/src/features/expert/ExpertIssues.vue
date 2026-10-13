<template>
  <PagePlaceholder
    title="疑难问题说明文档"
    description="记录核心概念分歧与专家结论。条目随复核确认生成，支持导出。"
    :contracts="['X02', 'X05']"
  />
</template>

<script setup lang="ts">
import PagePlaceholder from '@/shared/PagePlaceholder.vue'
</script>
