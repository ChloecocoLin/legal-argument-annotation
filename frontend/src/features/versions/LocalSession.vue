<script setup lang="ts">
import { ref } from 'vue'
const enabled = import.meta.env.DEV
const token = ref(localStorage.getItem('token') || '')
const choices = [
  ['dev-annotator-chen', '陈晓 · 标注员'], ['dev-annotator-liu', '刘一鸣 · 标注员'],
  ['dev-arbiter-zhao', '赵明远 · 裁定者'], ['dev-creator-li', '李承志 · 任务创建者'], ['dev-admin-wang', '王慧 · 管理员']
]
function change() { localStorage.setItem('token', token.value); window.location.reload() }
</script>
<template>
  <div v-if="enabled" class="local-session panel between">
    <div><b>本地联调</b><span class="muted t-xs"> · 统一切割及 A/B/C 草稿使用预置场景，正式提交保存到数据库</span></div>
    <select class="select" aria-label="本地联调身份" v-model="token" @change="change">
      <option value="">请选择联调身份</option><option v-for="[value, label] in choices" :key="value" :value="value">{{ label }}</option>
    </select>
  </div>
</template>
<style scoped>.local-session{padding:12px 16px;margin-bottom:20px;gap:12px}.select{width:210px}@media(max-width:760px){.local-session{align-items:flex-start;flex-direction:column}}</style>
