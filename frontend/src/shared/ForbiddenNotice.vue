<script setup lang="ts">
// 403 的空状态。后端拒绝时把**它自己的话**显出来，而不是统一写成「加载失败」——
// 「加载失败」会让人以为网络坏了、反复刷新；403 的真实含义是「这个账号没有这项权限」。
import Icon from './Icon.vue'

defineProps<{ message?: string }>()
const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<template>
  <div class="empty" style="padding: 56px 24px">
    <div class="empty-ico"><Icon name="lock" /></div>
    <h4>当前账号没有这个功能</h4>
    <p class="t-sm muted" style="max-width: 46ch; line-height: 1.7">
      {{ message || '后端拒绝了本次请求，但没有返回具体原因。' }}
    </p>
    <p class="t-xs muted" style="max-width: 46ch; margin-top: 6px">
      权限由账号角色决定；需要这项功能时，请退出后换用有权限的账号登录。
    </p>
    <button class="btn btn-secondary btn-sm" style="margin-top: 14px" @click="emit('retry')">
      <Icon name="refresh" />
      重试
    </button>
  </div>
</template>
