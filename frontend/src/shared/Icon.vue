<script setup lang="ts">
import { computed } from 'vue'
import { ICON_PATHS } from './icons'

const props = defineProps<{ name: string; cls?: string }>()
const inner = computed(() => ICON_PATHS[props.name] || '')
</script>

<template>
  <svg
    :class="props.cls"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="inner"
  ></svg>
</template>
