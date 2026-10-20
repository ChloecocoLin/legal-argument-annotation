<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { post } from '@/api/request'
import { mutationAttempt, errorCode, errorMessage } from '@/features/versions/api'
import { useUserStore } from '@/stores/user'
import { toast } from '@/shared/toast'
import {
  listMyExpertReviews,
  getExpertReviewDetail
} from '@/api/expert'
import type {
  ExpertReviewSummary,
  ExpertReviewDetail,
  ExpertIssue
} from '@/types/expert'

const statusName=(value:string)=>({REQUESTED:'待接受',IN_REVIEW:'复核中',CONFIRMED:'已确认',RETURNED:'已退回',PROPOSITION:'命题问题',OPEN:'待解决',RESOLVED:'已解决'} as Record<string,string>)[value]||value
const STATUS_OPTIONS = ['', 'REQUESTED', 'IN_REVIEW', 'CONFIRMED']
const user=useUserStore(), pending=ref(false), busy=ref(false), commandError=ref('')
const attempt=mutationAttempt<{url:string;body:object;actor:string}>()
onBeforeRouteLeave(()=>!busy.value&&!pending.value)

const loadingList = ref(true)
const listError = ref(false)
const summaries = ref<ExpertReviewSummary[]>([])
const filterStatus = ref('')
const selectedId = ref<string>('')

const detailLoading = ref(false)
const detailError = ref(false)
const detail = ref<ExpertReviewDetail | null>(null)

const accepting = ref(false)
const confirming = ref(false)
const resolvingId = ref('')
const resolutionDraft = ref<Record<string, { resolution: string; guideBasis: string }>>({})

const openCount = computed(() => {
  if (!detail.value) return 0
  return detail.value.issues.filter((i) => i.status === 'OPEN').length
})

async function loadList() {
  loadingList.value = true
  listError.value = false
  try {
    const all:ExpertReviewSummary[]=[]
    for(let page=1;;page++){const r=await listMyExpertReviews({status:filterStatus.value||undefined,page,size:100});all.push(...r.items);if(all.length>=r.total)break}
    const r={items:all}
    summaries.value = all
    if(!all.some(row=>row.review.id===selectedId.value)){selectedId.value='';detail.value=null}
    if (!selectedId.value && r.items.length) {
      selectedId.value = r.items[0].review.id
      await loadDetail()
    }
  } catch {
    listError.value = true
  } finally {
    loadingList.value = false
  }
}

async function loadDetail() {
  if (!selectedId.value) {
    detail.value = null
    return
  }
  detailLoading.value = true
  detailError.value = false
  try {
    detail.value = await getExpertReviewDetail(selectedId.value)
    for (const issue of detail.value.issues) {
      if (!resolutionDraft.value[issue.id]) {
        resolutionDraft.value[issue.id] = { resolution: '', guideBasis: '' }
      }
    }
  } catch {
    detailError.value = true
  } finally {
    detailLoading.value = false
  }
}

async function select(id: string) {
  selectedId.value = id
  await loadDetail()
}

async function mutate(url:string,body:object,version:number) {
  if(busy.value)return;busy.value=true;commandError.value=''
  const a=attempt.get({url,body:JSON.parse(JSON.stringify(body)),actor:user.id},version);pending.value=true
  if(a.body.actor!==user.id){attempt.reset();pending.value=false;busy.value=false;detail.value=null;return}
  try{await post(a.body.url,a.body.body,{headers:{'Idempotency-Key':a.key,'If-Match':a.version}});attempt.reset();pending.value=false;await Promise.all([loadList(),loadDetail()]);toast('已保存专家复核操作')}
  catch(e){commandError.value=errorMessage(e);if(errorCode(e)){attempt.reset();pending.value=false}}
  finally{busy.value=false}
}
function retry(){if(attempt.pending())mutate('',{},0)}
async function onAccept(){if(detail.value)await mutate(`/expert-reviews/${detail.value.review.id}/transitions`,{action:'START'},detail.value.review.recordVersion)}
async function onResolve(issue:ExpertIssue){const draft=resolutionDraft.value[issue.id];if(!draft?.resolution.trim()||!draft.guideBasis.trim()){toast('结论和指南依据都要填','err');return}await mutate(`/expert-issues/${issue.id}/resolutions`,{resolution:draft.resolution.trim(),guideBasis:draft.guideBasis.trim()},issue.recordVersion)}
async function onConfirm(){if(detail.value)await mutate(`/expert-reviews/${detail.value.review.id}/confirm`,{confirmed:true},detail.value.review.recordVersion)}

function contextFor(issueId: string) {
  if (!detail.value) return null
  return detail.value.contexts.find((c) => c.issueId === issueId) || null
}

onMounted(loadList)
</script>

<template>
  <div>
    <div class="page-head between">
      <div>
        <h1 class="t-h1">专家复核工作台</h1>
        <div class="row" style="margin-top: 8px; gap: 8px">
          <span class="badge outline">当前专家：{{ user.name }}</span>
        </div>
      </div>
    </div>

    <p v-if="commandError" class="note danger">{{ commandError }}</p><p v-if="pending" class="note warn">上次操作结果未确认，重试将沿用原请求。<button class="btn btn-secondary" :disabled="busy" @click="retry">重试相同操作</button></p>
    <fieldset :disabled="busy||pending" style="border:0;padding:0;margin:0">
    <!-- 状态过滤 -->
    <div class="card" style="margin: 16px 0">
      <div class="card-body">
        <div class="row wrap" style="gap: 8px; align-items: center">
          <span class="t-xs muted">状态</span>
          <button
            v-for="s in STATUS_OPTIONS"
            :key="s || 'all'"
            class="btn"
            :class="filterStatus === s ? 'btn-primary' : 'btn-secondary'"
            @click="filterStatus = s; selectedId=''; loadList()"
          >{{ s?statusName(s):'全部' }}</button>
        </div>
      </div>
    </div>

    <div class="split">
      <!-- 左：复核单列表 -->
      <div class="left">
        <div v-if="loadingList" class="card"><div class="card-body">加载待办…</div></div>
        <div v-else-if="listError" class="card">
          <div class="card-body between">
            <span>加载失败。</span>
            <button class="btn btn-primary" @click="loadList">重试</button>
          </div>
        </div>
        <div v-else-if="summaries.length === 0" class="card">
          <div class="card-body">当前没有复核单。</div>
        </div>
        <div v-else class="stack-5">
          <div
            v-for="s in summaries"
            :key="s.review.id"
            class="card clickable"
            :class="{ active: selectedId === s.review.id }"
            @click="select(s.review.id)"
          >
            <div class="card-body">
              <div class="row between">
                <span class="badge">{{ statusName(s.review.status) }}</span>
                <span class="t-xs muted">{{ s.taskName }}</span>
              </div>
              <p class="t-sm" style="margin: 8px 0 0">
                问题 {{ s.issueCount }} · 未解决 {{ s.openIssueCount }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：详情 -->
      <div class="right">
        <div v-if="!selectedId" class="card">
          <div class="card-body">从左侧选择一张复核单。</div>
        </div>
        <div v-else-if="detailLoading" class="card">
          <div class="card-body">加载详情…</div>
        </div>
        <div v-else-if="detailError" class="card">
          <div class="card-body between">
            <span>详情加载失败。</span>
            <button class="btn btn-primary" @click="loadDetail">重试</button>
          </div>
        </div>
        <div v-else-if="detail" class="stack-5">
          <div class="card">
            <div class="card-head">
              <div class="row" style="gap: 8px">
                <span class="badge">{{ statusName(detail.review.status) }}</span>
                <span class="t-xs muted">会话 {{ detail.review.sessionId.slice(0, 8) }}</span>
              </div>
              <span class="t-xs muted">修订 {{ detail.review.recordVersion }}</span>
            </div>
            <div class="card-body" v-if="detail.review.status === 'REQUESTED'">
              <button class="btn btn-primary" :disabled="accepting" @click="onAccept">
                {{ accepting ? '接受中…' : '接受任务' }}
              </button>
            </div>
          </div>

          <div v-for="issue in detail.issues" :key="issue.id" class="card">
            <div class="card-head">
              <div class="row" style="gap: 8px">
                <span class="badge outline">{{ statusName(issue.itemKind) }}</span>
                <span class="badge" :class="issue.status === 'OPEN' ? '' : 'success'">{{ statusName(issue.status) }}</span>
              </div>
            </div>
            <div class="card-body">
              <p class="t-sm" style="margin-top: 0">{{ issue.question }}</p>

              <!-- 原文摘录（PROPOSITION 问题） -->
              <template v-if="contextFor(issue.id)">
                <div
                  v-for="g in contextFor(issue.id)!.candidateGroups"
                  :key="g.versionId"
                  class="card"
                  style="margin-bottom: 8px"
                >
                  <div class="card-body tight">
                    <div class="section-label">源版本 {{ g.displayCode }}</div>
                    <p v-for="(t, i) in g.elements.map(e=>e.text)" :key="i" class="t-sm" style="margin: 4px 0 0">{{ t }}</p>
                  </div>
                </div>
              </template>

              <!-- 已解决：只读展示 -->
              <div v-if="issue.status === 'RESOLVED'" class="note">
                <div><strong>专家结论：</strong>{{ issue.resolution }}</div>
                <div style="margin-top: 4px"><strong>指南依据：</strong>{{ issue.guideBasis }}</div>
              </div>

              <!-- OPEN：填结论 -->
              <div v-else style="margin-top: 12px">
                <input
                  v-model="resolutionDraft[issue.id].resolution" :disabled="detail.review.status!=='IN_REVIEW'"
                  class="input"
                  placeholder="专家结论"
                  style="width: 100%; margin-bottom: 8px; padding: 6px 8px"
                />
                <input
                  v-model="resolutionDraft[issue.id].guideBasis" :disabled="detail.review.status!=='IN_REVIEW'"
                  class="input"
                  placeholder="指南依据"
                  style="width: 100%; margin-bottom: 8px; padding: 6px 8px"
                />
                <button
                  class="btn btn-primary"
                  :disabled="resolvingId === issue.id||detail.review.status!=='IN_REVIEW'"
                  @click="onResolve(issue)"
                >{{ resolvingId === issue.id ? '提交中…' : '提交结论' }}</button>
              </div>
            </div>
          </div>

          <!-- 全部解决后可确认 -->
          <div v-if="detail.review.status === 'IN_REVIEW' && openCount === 0" class="card">
            <div class="card-body between">
              <span>所有问题已解决，可以确认完成。</span>
              <button class="btn btn-primary" :disabled="confirming" @click="onConfirm">
                {{ confirming ? '确认中…' : '确认完成' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    </fieldset>
  </div>
</template>

<style scoped>
.split {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.left {
  flex: 0 0 300px;
  min-width: 260px;
}
.right {
  flex: 1;
  min-width: 320px;
}
.clickable {
  cursor: pointer;
}
.clickable.active {
  outline: 2px solid var(--el-color-primary, #409eff);
}
</style>
