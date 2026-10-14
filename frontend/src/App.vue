<script setup lang="ts">
// 外壳分支：多数页面套 AppLayout（侧栏 + 顶栏），登录页这类需要占满整屏的走 meta.blank。
// 用 route.meta 而不是给 App.vue 造一个 props，是因为 meta 本来就是「路由自带的属性」，
// 路由表里写一次就不会忘。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/shared/AppLayout.vue'
import Toasts from '@/shared/Toasts.vue'

const route = useRoute()
const blank = computed(() => !!route.meta.blank)
</script>

<template>
  <router-view v-if="blank" />
  <!-- 空白外壳下 AppLayout 里的 Toasts 不会渲染，所以这里补一个：登录失败也要有提示。 -->
  <Toasts v-if="blank" />
  <AppLayout v-else>
    <router-view />
  </AppLayout>
</template>
