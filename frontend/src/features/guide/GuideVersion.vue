<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Chip from '@/shared/Chip.vue'
import StatusBadge from '@/shared/StatusBadge.vue'
import BaseModal from '@/shared/BaseModal.vue'
import { toast } from '@/shared/toast'
import { getGuide, listLabels } from '@/api/guide'
import type { Guide, GuideVersion, LabelDefinition } from '@/types/guide'

const route = useRoute()
const router = useRouter()
const gid = route.params.guideId as string

const guide = ref<Guide>()
const version = ref<GuideVersion>()
const labels = ref<LabelDefinition[]>([])

onMounted(async () => {
  const summary = await getGuide(gid)
  guide.value = summary.guide
  version.value = summary.versions[0]
  if (version.value) {
    labels.value = await listLabels(version.value.id)
  }
})

function bySortOrder(a: LabelDefinition, b: LabelDefinition): number {
  return a.sortOrder - b.sortOrder || a.code.localeCompare(b.code)
}

// 标签是否存在、属于哪一类，完全以当前指南版本的 G06 字典为准。
const tagNl = computed(() =>
  labels.value
    .filter((label) => label.category === 'NON_PROPOSITION')
    .slice()
    .sort(bySortOrder)
)
const tagGm = computed(() =>
  labels.value
    .filter((label) => label.category === 'PROPOSITION' && label.baseType === 'GM')
    .slice()
    .sort(bySortOrder)
)
const tagSm = computed(() =>
  labels.value
    .filter((label) => label.category === 'PROPOSITION' && label.baseType === 'SM')
    .slice()
    .sort(bySortOrder)
)
const tagGf = computed(() =>
  labels.value
    .filter((label) => label.category === 'PROPOSITION' && label.baseType === 'GF')
    .slice()
    .sort(bySortOrder)
)
const tagSf = computed(() =>
  labels.value
    .filter((label) => label.category === 'PROPOSITION' && label.baseType === 'SF')
    .slice()
    .sort(bySortOrder)
)

function fmtSize(bytes?: number | null): string {
  if (bytes == null) return '—'
  const kb = bytes / 1024
  if (kb >= 1024) return `${(kb / 1024).toFixed(1)} MB`
  return `${Math.round(kb)} KB`
}
function fmtDate(t?: string | null): string {
  return t ? t.slice(0, 10) : '—'
}

function download() {
  toast('原件下载接口待接入', 'err')
}

// 设为启用确认
const confirmState = reactive({ open: false })
function askActivate() {
  confirmState.open = true
}
function doActivate() {
  confirmState.open = false
  toast('当前契约仅支持归档与发布新版本，重新启用旧版本的接口未就绪', 'err')
}

// 章节目录
const TOC: [string, string[]][] = [
  ['一、适用范围与总体进路', ['1.1 适用文书范围', '1.2 两阶段四步骤', '1.3 标注总原则']],
  ['二、颗粒度对齐与文本切割', ['2.1 句号 / 分号切割', '2.2 逗号子句考查', '2.3 引号与过渡语词处理']],
  ['三、非命题要素标注', ['3.1 争议焦点 IS', '3.2 态度声明 AS / 总结性陈述 SS', '3.3 判决依据 JG / 判决结果 JD']],
  ['四、命题要素标注', ['4.1 一般规范判断 GM 及其子类型', '4.2 个别规范判断 SM', '4.3 一般 / 个别事实判断 GF · SF']],
  ['五、关系标注', ['5.1 支持关系 S', '5.2 反对关系 A', '5.3 组合关系 J', '5.4 匹配关系 M', '5.5 同一关系 I']],
  ['六、论证图示规范', ['6.1 节点与符号', '6.2 嵌套结构', '6.3 布局与自内而外构造']],
  ['七、标注流程与质量要求', ['7.1 冲突解决流程', '7.2 最终版本审查']],
  ['八、完整标注示例', ['8.1 余某、徐某案', '8.2 指导案例 1 号']]
]

const REL_FIGS: [string, string][] = [
  ['rel-S.png', 'S 支持 · 实心圆 ● 有向边'],
  ['rel-A.png', 'A 反对 · 空心圆 ○ 有向边'],
  ['rel-J2.png', 'J 组合 · 带“+”圆节点 无向'],
  ['rel-M.png', 'M 匹配 · 带“+”圆节点 有向'],
  ['rel-J3.png', 'J 组合 · 三项合取结构'],
  ['nest-multi.png', '多关系综合结构']
]
const NEST_FIGS: [string, string][] = [
  ['nest-M-J.png', 'M(J(p1, p2), p3)'],
  ['nest-J-M.png', 'J(M(p1, p2), p3)'],
  ['nest-S-J.png', 'S(J(p1, p2), p3)'],
  ['nest-S-J3.png', 'S(J(p1, p2, p3), p4)'],
  ['nest-S-M.png', 'S(M(ps, pg), pj)'],
  ['nest-A-S.png', 'A(po, S(pi, pj))']
]
</script>

<template>
  <div v-if="guide && version">
    <div class="page-head between">
      <div>
        <a class="t-xs" style="color: var(--accent); cursor: pointer" @click="router.push('/guide')">← 返回指南管理</a>
        <h1 class="t-h1" style="margin-top: 8px">{{ guide.title }}</h1>
        <div class="row wrap" style="margin-top: 8px; gap: 8px">
          <span class="badge accent mono">v{{ version.versionCode }}</span>
          <StatusBadge :status="guide.status === 'ACTIVE' ? 'active' : 'archived'" />
          <span class="t-xs muted">{{ version.pageCount }} 页 · {{ fmtSize(version.fileSize) }}</span>
          <span class="t-xs muted">上传于 {{ fmtDate(version.createdAt) }} · {{ version.uploadedBy }}</span>
        </div>
      </div>
      <div class="row">
        <button class="btn btn-secondary" @click="download">下载原件</button>
        <button v-if="guide.status !== 'ACTIVE'" class="btn btn-primary" @click="askActivate">设为启用</button>
      </div>
    </div>

    <section class="grid-2" style="grid-template-columns: 280px 1fr; align-items: start">
      <div class="stack-5">
        <div class="card">
          <div class="card-head tight">
            <div class="card-title" style="font-size: 13.5px">章节目录</div>
          </div>
          <div class="card-body tight toc">
            <div v-for="(s, i) in TOC" :key="i">
              <a @click.prevent>{{ s[0] }}</a>
              <div class="lv2">
                <a v-for="(x, j) in s[1]" :key="j" @click.prevent>{{ x }}</a>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head tight">
            <div class="card-title" style="font-size: 13.5px">版本信息</div>
          </div>
          <div class="card-body tight">
            <div class="stack-3 t-sm">
              <div class="between"><span class="muted">版本号</span><span class="mono strong">v{{ version.versionCode }}</span></div>
              <div class="between"><span class="muted">状态</span>
                <StatusBadge :status="guide.status === 'ACTIVE' ? 'active' : 'archived'" />
              </div>
              <div class="between"><span class="muted">页数</span><span class="mono">{{ version.pageCount }}</span></div>
              <div class="between"><span class="muted">文件大小</span><span class="mono">{{ fmtSize(version.fileSize) }}</span></div>
              <div class="between"><span class="muted">上传人</span><span>{{ version.uploadedBy }}</span></div>
              <div class="between"><span class="muted">上传时间</span><span class="mono t-xs">{{ fmtDate(version.createdAt) }}</span></div>
            </div>
          </div>
        </div>
      </div>

      <div class="stack-6">
        <!-- 要素标签体系 -->
        <div class="card">
          <div class="card-head">
            <div class="card-title"><span class="dot"></span>要素标签体系</div>
            <span class="badge outline" style="margin-left: auto">指南 第 4 · 5 章</span>
          </div>
          <div class="card-body">
            <div class="section-label" style="margin-bottom: 10px">一、非命题要素</div>
            <div class="row wrap" style="gap: 6px">
              <Chip v-for="label in tagNl" :key="label.id" :code="label.code" long />
            </div>
            <div class="section-label" style="margin: 18px 0 10px">二、命题要素</div>
            <div class="grid-2" style="gap: 16px">
              <div>
                <div class="t-xs muted" style="margin-bottom: 8px">一般规范判断 GM（子类型）</div>
                <div class="row wrap" style="gap: 6px">
                  <Chip v-for="label in tagGm" :key="label.id" :code="label.code" />
                </div>
              </div>
              <div>
                <div class="t-xs muted" style="margin-bottom: 8px">个别规范判断 SM</div>
                <div class="row wrap" style="gap: 6px">
                  <Chip v-for="label in tagSm" :key="label.id" :code="label.code" />
                </div>
              </div>
              <div>
                <div class="t-xs muted" style="margin-bottom: 8px">一般事实判断 GF</div>
                <div class="row wrap" style="gap: 6px">
                  <Chip v-for="label in tagGf" :key="label.id" :code="label.code" long />
                </div>
              </div>
              <div>
                <div class="t-xs muted" style="margin-bottom: 8px">个别事实判断 SF</div>
                <div class="row wrap" style="gap: 6px">
                  <Chip v-for="label in tagSf" :key="label.id" :code="label.code" long />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 关系图元 -->
        <div class="card">
          <div class="card-head">
            <div class="card-title"><span class="dot"></span>关系类型与图元符号</div>
            <span class="badge outline" style="margin-left: auto">指南 第 6 章</span>
          </div>
          <div class="card-body">
            <div class="grid-3">
              <figure v-for="(f, i) in REL_FIGS" :key="i" class="fig">
                <img :src="`/figures/${f[0]}`" :alt="`${f[1]} 图元示例`">
                <figcaption>{{ f[1] }}</figcaption>
              </figure>
            </div>
          </div>
        </div>

        <!-- 嵌套示例 -->
        <div class="card">
          <div class="card-head">
            <div class="card-title"><span class="dot"></span>嵌套结构示例</div>
            <span class="badge outline" style="margin-left: auto">指南 第 7 章</span>
          </div>
          <div class="card-body">
            <div class="grid-3">
              <figure v-for="(f, i) in NEST_FIGS" :key="i" class="fig">
                <img :src="`/figures/${f[0]}`" :alt="`${f[1]} 嵌套结构示例`">
                <figcaption class="mono">{{ f[1] }}</figcaption>
              </figure>
            </div>
            <div class="note guide" style="margin-top: 16px">
              <div class="note-body">嵌套结构图示自内而外逐层构造：先画最内层关系节点，再以其整体作为外层关系的输入。关系节点保持独立，不得合并或省略。</div>
            </div>
          </div>
        </div>

        <!-- 完整示例 -->
        <div class="card">
          <div class="card-head">
            <div class="card-title"><span class="dot"></span>完整标注示例（指南 7.4）</div>
          </div>
          <div class="card-body">
            <div class="grid-2">
              <figure class="fig">
                <img src="/figures/doc1-graph1.png" alt="裁判理由论证图示一">
                <figcaption>图示一：P4–P9 部分的支持 / 组合 / 匹配关系</figcaption>
              </figure>
              <figure class="fig">
                <img src="/figures/doc1-graph2.png" alt="裁判理由论证图示二">
                <figcaption>图示二：P10–P13 部分的反对与支持关系</figcaption>
              </figure>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 启用确认 -->
    <BaseModal v-model:open="confirmState.open" :title="`启用 v${version.versionCode}？`">
      <p class="t-sm" style="line-height: 1.7">新任务将默认使用该版本；历史任务锁定原版本。</p>
      <template #footer>
        <button class="btn btn-secondary" @click="confirmState.open = false">取消</button>
        <button class="btn btn-primary" @click="doActivate">确认</button>
      </template>
    </BaseModal>
  </div>
</template>
