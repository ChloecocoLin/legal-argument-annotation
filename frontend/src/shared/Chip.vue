<script setup lang="ts">
import { computed } from 'vue'
import { TYPE_META, typeGroup } from './elementMeta'

const props = defineProps<{ code?: string; long?: boolean; lg?: boolean }>()

const group = computed(() => (props.code ? typeGroup(props.code) : 'nl'))
const meta = computed(() => (props.code ? TYPE_META[props.code] : null))
</script>

<template>
  <span
    v-if="code"
    class="chip"
    :class="['chip-' + group, { 'chip-lg': lg }]"
    :title="meta?.name"
  >
    {{ code }}<span v-if="long" class="chip-name">{{ meta?.name }}</span>
  </span>
  <span v-else class="chip chip-nl is-off">未标注</span>
</template>
