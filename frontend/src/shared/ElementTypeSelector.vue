<script setup lang="ts">
import { computed } from 'vue'
import type { LabelDefinition } from '@/types/guide'
import { groupElementTypes } from './elementTypeGroups'
import { typeGroup } from './elementMeta'

interface Props {
  labels: LabelDefinition[]
  modelValue?: string | null
  loading?: boolean
  error?: string | null
  disabled?: boolean
  clearable?: boolean
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  loading: false,
  error: null,
  disabled: false,
  clearable: false,
  emptyText: '当前指南版本暂无可用标签'
})

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  change: [value: string | null, label: LabelDefinition | null]
  retry: []
}>()

const groups = computed(() => groupElementTypes(props.labels))

function select(label: LabelDefinition): void {
  if (props.disabled) return

  const nextValue = props.clearable && props.modelValue === label.code ? null : label.code
  const nextLabel = nextValue ? label : null
  emit('update:modelValue', nextValue)
  emit('change', nextValue, nextLabel)
}
</script>

<template>
  <section
    class="element-type-selector"
    :class="{ 'is-disabled': disabled }"
    aria-label="要素类型选择"
    :aria-busy="loading"
  >
    <div v-if="loading" class="selector-state" role="status">
      <span class="selector-spinner" aria-hidden="true" />
      <span>正在加载要素类型…</span>
    </div>

    <div v-else-if="error" class="selector-state is-error" role="alert">
      <span>{{ error }}</span>
      <button type="button" class="btn btn-secondary btn-sm" @click="emit('retry')">
        重新加载
      </button>
    </div>

    <div v-else-if="groups.length === 0" class="selector-state">
      {{ emptyText }}
    </div>

    <div v-else class="selector-groups">
      <fieldset v-for="group in groups" :key="group.key" class="selector-group">
        <legend>
          <span class="selector-group-title">{{ group.title }}</span>
          <span class="selector-group-description">{{ group.description }}</span>
        </legend>

        <div class="selector-options">
          <button
            v-for="label in group.labels"
            :key="label.id"
            type="button"
            class="selector-option"
            :class="[
              `selector-option-${typeGroup(label.code)}`,
              { 'is-selected': modelValue === label.code }
            ]"
            :disabled="disabled"
            :aria-pressed="modelValue === label.code"
            :title="label.definition || label.name"
            @click="select(label)"
          >
            <span class="selector-option-code">{{ label.code }}</span>
            <span class="selector-option-name">{{ label.name }}</span>
            <span v-if="modelValue === label.code" class="selector-option-check" aria-hidden="true">
              ✓
            </span>
          </button>
        </div>
      </fieldset>
    </div>
  </section>
</template>

<style scoped>
.element-type-selector {
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--surface);
  overflow: hidden;
}

.element-type-selector.is-disabled {
  opacity: 0.68;
}

.selector-groups {
  display: grid;
}

.selector-group {
  min-width: 0;
  margin: 0;
  padding: var(--sp-4) var(--sp-5);
  border: 0;
}

.selector-group + .selector-group {
  border-top: 1px solid var(--border);
}

.selector-group legend {
  width: 100%;
  padding: 0;
}

.selector-group-title {
  display: block;
  color: var(--fg);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.selector-group-description {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: var(--fs-xs);
  font-weight: 400;
}

.selector-options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(172px, 1fr));
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.selector-option {
  --option-color: var(--nl);
  --option-soft: var(--nl-soft);
  --option-line: var(--nl-line);

  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--sp-2);
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid var(--option-line);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--fg-2);
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.selector-option:hover:not(:disabled) {
  background: var(--option-soft);
  border-color: var(--option-color);
}

.selector-option:focus-visible {
  outline: 2px solid var(--option-color);
  outline-offset: 2px;
}

.selector-option.is-selected {
  background: var(--option-soft);
  border-color: var(--option-color);
  box-shadow: inset 3px 0 0 var(--option-color);
}

.selector-option:disabled {
  cursor: not-allowed;
}

.selector-option-gm {
  --option-color: var(--gm);
  --option-soft: var(--gm-soft);
  --option-line: var(--gm-line);
}

.selector-option-sm {
  --option-color: var(--sm);
  --option-soft: var(--sm-soft);
  --option-line: var(--sm-line);
}

.selector-option-gf {
  --option-color: var(--gf);
  --option-soft: var(--gf-soft);
  --option-line: var(--gf-line);
}

.selector-option-sf {
  --option-color: var(--sf);
  --option-soft: var(--sf-soft);
  --option-line: var(--sf-line);
}

.selector-option-code {
  color: var(--option-color);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.selector-option-name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selector-option-check {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--option-color);
  color: var(--surface);
  font-size: 11px;
  font-weight: 700;
}

.selector-state {
  display: flex;
  min-height: 116px;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-5);
  color: var(--muted);
  font-size: var(--fs-sm);
  text-align: center;
}

.selector-state.is-error {
  color: var(--danger);
}

.selector-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--border-strong);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: selector-spin 0.8s linear infinite;
}

@keyframes selector-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .selector-group {
    padding: var(--sp-4);
  }

  .selector-options {
    grid-template-columns: 1fr;
  }
}
</style>
