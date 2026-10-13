<script setup lang="ts">
import Icon from './Icon.vue'

defineProps<{
  open: boolean
  title: string
  sub?: string
  wide?: boolean
}>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  ok: []
}>()

function close() {
  emit('update:open', false)
}
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" :class="{ 'is-open': open }" @click.self="close">
      <div class="modal" :class="{ wide }">
        <div class="modal-head">
          <div>
            <h3>{{ title }}</h3>
            <p v-if="sub" class="t-xs muted" style="margin-top: 4px">{{ sub }}</p>
          </div>
          <button class="btn btn-icon modal-x" @click="close"><Icon name="x" /></button>
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div class="modal-foot">
          <slot name="footer">
            <button class="btn btn-secondary" @click="close">取消</button>
            <button class="btn btn-primary" @click="emit('ok')">确定</button>
          </slot>
        </div>
      </div>
    </div>
  </Teleport>
</template>
