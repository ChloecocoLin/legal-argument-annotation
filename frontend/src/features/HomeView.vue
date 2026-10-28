<template>
  <div class="home">
    <el-card>
      <h2 style="margin: 0 0 6px">法律论证标注系统</h2>
      <p class="muted">按已授权角色进入任务与标注流程。</p>
      <el-divider />
      <el-row :gutter="16">
        <el-col v-for="item in items" :key="item.path" :span="8">
          <router-link :to="item.path" class="entry">
            <el-card shadow="hover">
              <b>{{ item.title }}</b>
              <p class="muted small">{{ item.sub }}</p>
            </el-card>
          </router-link>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
const user = useUserStore()
const entries = [
  { path: '/tasks', title: '标注任务', sub: '查看任务、案例及提交进度', roles: ['admin','creator','annotator','arbiter'] },
  { path: '/tasks/create', title: '创建任务', sub: '选择指南、添加文书与分配人员', roles: ['creator'] },
  { path: '/documents', title: '文书总库', sub: '浏览裁判文书与规范化原文', roles: ['admin','creator','annotator','arbiter'] },
  { path: '/guide', title: '标注指南', sub: '查阅指南版本与标签定义', roles: ['admin','creator','annotator','arbiter','expert','teacher','student'] },
  { path: '/adjudication/conflict', title: '冲突解决', sub: '比较完整版本并裁定命题与关系', roles: ['arbiter'] },
  { path: '/final-review', title: '最终版本审查', sub: '检查最终标注与图验证结果', roles: ['arbiter'] },
  { path: '/expert', title: '专家复核', sub: '查看指派的疑难问题', roles: ['expert'] }
]
const items = computed(() => entries.filter(item => user.roles.some(role => item.roles.includes(role))))

</script>

<style scoped>
.home {
  padding: 24px;
}
.muted {
  color: #8a8f99;
}
.small {
  font-size: 12.5px;
}
.entry {
  text-decoration: none;
  color: inherit;
  display: block;
  margin-bottom: 16px;
}
</style>
