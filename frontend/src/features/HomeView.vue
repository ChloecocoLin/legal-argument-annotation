<template>
  <div class="home">
    <el-card>
      <h2 style="margin: 0 0 6px">法律论证标注系统</h2>
      <p class="muted">成员B 负责页面入口（M0 骨架）。</p>
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
  { path: '/tasks', title: '任务进度与版本', sub: '查看 X/N、完整提交与撤回' },
  { path: '/guide', title: '标注指南管理', sub: '指南库 G 系列' },
  { path: '/adjudication/conflict', title: '冲突解决', sub: '命题裁定 J 系列' },
  { path: '/adjudication/annotate', title: '裁定者标注', sub: '补页' },
  { path: '/expert', title: '专家复核工作台', sub: 'X 系列' },
  { path: '/expert/issues', title: '疑难问题说明文档', sub: 'X 系列' }
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
