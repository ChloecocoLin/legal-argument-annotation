<template>
  <div class="home">
    <el-card>
      <h2 style="margin: 0 0 6px">法律论证标注系统</h2>
      <p class="muted">在 B 的工程骨架上接入成员 A：文书、任务分配、原文与命题边界。</p>
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
const items = [
  { path: '/documents', title: '文书总库', sub: '成员 A · D01/D03/D05' },
  { path: '/tasks/create', title: '创建任务', sub: '成员 A · T02/T05' },
  { path: '/tasks', title: '任务列表与分配', sub: '成员 A · T01/T07/T10/T12' },
  { path: '/guide', title: '标注指南管理', sub: '成员 B · 指南库 G 系列' },
  { path: '/adjudication/conflict', title: '冲突解决', sub: '命题裁定 J 系列' },
  { path: '/expert', title: '专家复核工作台', sub: 'X 系列' }
]
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
