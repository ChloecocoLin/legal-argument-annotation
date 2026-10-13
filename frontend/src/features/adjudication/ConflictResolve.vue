<template>
  <PagePlaceholder
    title="冲突解决 · 第一阶段命题裁定"
    description="按源版本并排展示命题候选，逐项采用某版或自定义。命题逐项决定 J04 当前 PENDING_MODEL。"
    :contracts="['J03', 'J09', 'J10', 'J12']"
  />
</template>

<script setup lang="ts">
import PagePlaceholder from '@/shared/PagePlaceholder.vue'
</script>
