<template>
  <PagePlaceholder
    title="专家复核工作台"
    description="列出本人待办，查看问题与原文，接受复核、提交结论并确认。"
    :contracts="['X01', 'X02', 'X03', 'X04', 'X05']"
  />
</template>

<script setup lang="ts">
import PagePlaceholder from '@/shared/PagePlaceholder.vue'
</script>
