<script setup lang="ts">
import Icon from './Icon.vue'
import { toasts } from './toast'
</script>

<template>
  <Teleport to="body">
    <div class="toast-wrap">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.kind">
        <Icon :name="t.kind === 'err' ? 'alert' : 'check'" />
        <span>{{ t.message }}</span>
      </div>
    </div>
  </Teleport>
</template>
