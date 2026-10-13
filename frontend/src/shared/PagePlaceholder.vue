<template>
  <div class="placeholder">
    <el-card>
      <h2 style="margin: 0 0 8px">{{ title }}</h2>
      <p class="muted">{{ description }}</p>
      <div v-if="contracts && contracts.length" class="tags">
        <el-tag v-for="c in contracts" :key="c" size="small" effect="plain">
          {{ c }}
        </el-tag>
      </div>
      <p class="muted small">M0 骨架占位页面，将在对应里程碑实现。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  contracts?: string[]
}>()
</script>

<style scoped>
.placeholder {
  padding: 24px;
}
.muted {
  color: #8a8f99;
  line-height: 1.7;
}
.small {
  font-size: 12.5px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
</style>
