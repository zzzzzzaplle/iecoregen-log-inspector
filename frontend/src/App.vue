<script setup>
import { computed, onMounted, ref } from "vue";
import { analyzeLog, listLogs, readLines } from "./api";

const logs = ref([]);
const selectedLogId = ref("");
const analysis = ref(null);
const selectedSampleId = ref("");
const loading = ref(false);
const error = ref("");
const linePreview = ref(null);
const previewLoading = ref(false);
const activeAnnotationResponse = ref("");
const activeVerificationResponse = ref("");
const activeCompletionClass = ref("");
const activeFixingClass = ref("");

const selectedLog = computed(() => logs.value.find((item) => item.id === selectedLogId.value));
const selectedSample = computed(() => {
  if (!analysis.value) return null;
  return analysis.value.samples.find((sample) => sample.id === selectedSampleId.value) || null;
});

const groupedExceptions = computed(() => {
  const exceptions = selectedSample.value?.exceptions || [];
  const groups = [];
  const byLine = new Map();

  for (const event of exceptions) {
    let group = byLine.get(event.line);
    if (!group) {
      group = {
        line: event.line,
        events: [],
      };
      byLine.set(event.line, group);
      groups.push(group);
    }
    group.events.push(event);
  }

  return groups;
});

const logTitle = computed(() => selectedLog.value?.path || "请选择 log.txt");

const overview = computed(() => {
  if (!analysis.value) {
    return [];
  }
  const samples = analysis.value.samples || [];
  return [
    { label: "日志行数", value: analysis.value.totalLines.toLocaleString() },
    { label: "Samples", value: samples.length },
    { label: "成功", value: samples.filter((sample) => sample.status === "SUCCESS").length },
    {
      label: "异常事件",
      value: samples.reduce((sum, sample) => sum + sample.exceptions.length, 0),
    },
  ];
});

async function loadLogs() {
  loading.value = true;
  error.value = "";
  try {
    logs.value = await listLogs();
    if (logs.value.length > 0) {
      selectedLogId.value = logs.value[0].id;
      await loadAnalysis();
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function loadAnalysis() {
  if (!selectedLogId.value) return;
  loading.value = true;
  error.value = "";
  linePreview.value = null;
  try {
    analysis.value = await analyzeLog(selectedLogId.value);
    const firstSample = analysis.value.samples?.[0];
    selectedSampleId.value = firstSample?.id || "";
    activeAnnotationResponse.value = firstSample?.operationAnnotationResponses?.[0]?.label || "";
    activeVerificationResponse.value = firstSample?.operationVerificationResponses?.[0]?.label || "";
    activeCompletionClass.value = firstSample?.codeCompletionClasses?.[0]?.name || "";
    activeFixingClass.value = firstSample?.fixingClasses?.[0]?.name || "";
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

async function showLineContext(line, endLine = null) {
  if (!selectedLogId.value || !line) return;
  previewLoading.value = true;
  try {
    const start = endLine ? line : Math.max(1, line - 12);
    const end = endLine || (line + 12);
    linePreview.value = await readLines(selectedLogId.value, start, end);
  } catch (err) {
    linePreview.value = { error: err.message, lines: [] };
  } finally {
    previewLoading.value = false;
  }
}

function stageByKey(key) {
  return selectedSample.value?.stages.find((stage) => stage.key === key);
}

function lineRange(stage) {
  if (!stage || !stage.startLine || !stage.endLine) {
    return "未识别";
  }
  return `L${stage.startLine} - L${stage.endLine}`;
}

function statusText(status) {
  return status === "SUCCESS" ? "成功" : "需要关注";
}

function activateSample(sample) {
  selectedSampleId.value = sample.id;
  activeAnnotationResponse.value = sample.operationAnnotationResponses?.[0]?.label || "";
  activeVerificationResponse.value = sample.operationVerificationResponses?.[0]?.label || "";
  activeCompletionClass.value = sample.codeCompletionClasses?.[0]?.name || "";
  activeFixingClass.value = sample.fixingClasses?.[0]?.name || "";
  linePreview.value = null;
}

async function showStageResponse(item, mode) {
  if (!item) return;
  if (mode === "annotation") {
    activeAnnotationResponse.value = item.label;
  } else {
    activeVerificationResponse.value = item.label;
  }
  await showLineContext(item.startLine, item.endLine);
}

async function showClassResponse(item, mode) {
  if (!item) return;
  if (mode === "completion") {
    activeCompletionClass.value = item.name;
  } else {
    activeFixingClass.value = item.name;
  }
  if (item.responseStartLine && item.responseEndLine) {
    await showLineContext(item.responseStartLine, item.responseEndLine);
    return;
  }
  await showLineContext(item.line);
}

onMounted(loadLogs);
</script>

<template>
  <main class="page">
    <header class="topbar">
      <div>
        <h1>iEcoregen Log Inspector</h1>
        <p>{{ logTitle }}</p>
      </div>
      <div class="toolbar">
        <label>
          <span>日志选择</span>
          <select v-model="selectedLogId" :disabled="loading" @change="loadAnalysis">
            <option v-for="log in logs" :key="log.id" :value="log.id">
              {{ log.caseName }} / {{ log.model }} / {{ log.run }}
            </option>
          </select>
        </label>
        <button type="button" :disabled="loading" @click="loadAnalysis">刷新</button>
      </div>
    </header>

    <section v-if="error" class="notice error">{{ error }}</section>

    <section v-if="analysis" class="metrics">
      <article v-for="item in overview" :key="item.label" class="metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="layout">
      <aside class="samples">
        <div class="panel-title">
          <h2>Sample 列表</h2>
          <span>{{ analysis?.samples?.length || 0 }}</span>
        </div>
        <button
          v-for="sample in analysis?.samples || []"
          :key="sample.id"
          type="button"
          class="sample-card"
          :class="{ active: sample.id === selectedSampleId }"
          @click="activateSample(sample)"
        >
          <div class="sample-head">
            <strong>{{ sample.name }}</strong>
            <span :class="['status-pill', sample.status === 'SUCCESS' ? 'success' : 'warn']">
              {{ statusText(sample.status) }}
            </span>
          </div>
          <p>L{{ sample.startLine }} - L{{ sample.endLine }}</p>
          <p>有效起点：{{ sample.effectiveStartLine ? `L${sample.effectiveStartLine}` : '未识别' }}</p>
        </button>
      </aside>

      <section class="details">
        <div v-if="!selectedSample" class="empty">选择左侧 sample 查看详情</div>

        <template v-else>
          <div class="detail-header">
            <div>
              <h2>{{ selectedSample.name }}</h2>
              <p>{{ selectedSample.id }}</p>
            </div>
            <span :class="['status-pill', selectedSample.status === 'SUCCESS' ? 'success' : 'warn']">
              {{ statusText(selectedSample.status) }}
            </span>
          </div>

          <details open class="section">
            <summary>
              <span>异常列表</span>
              <strong>{{ groupedExceptions.length }} 行</strong>
            </summary>
            <div v-if="groupedExceptions.length === 0" class="empty">未识别到异常模式</div>
            <table v-else>
              <thead>
                <tr>
                  <th>行号</th>
                  <th>类型</th>
                  <th>内容</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="group in groupedExceptions" :key="`exception-line-${group.line}`">
                  <td>
                    <button type="button" class="line-button" @click="showLineContext(group.line)">L{{ group.line }}</button>
                  </td>
                  <td>
                    <div class="exception-types">
                      <span
                        v-for="event in group.events"
                        :key="`${event.type}-${event.line}-${event.text}`"
                        class="exception-tag"
                      >
                        {{ event.type }}
                      </span>
                    </div>
                  </td>
                  <td>
                    <div class="exception-content-list">
                      <div
                        v-for="event in group.events"
                        :key="`${event.type}-${event.line}-${event.text}`"
                        class="exception-content-item"
                      >
                        {{ event.text }}
                      </div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </details>

          <details class="section">
            <summary>
              <span>操作规格补全阶段</span>
              <strong>{{ selectedSample.operationAnnotationResponses.length }} 条</strong>
            </summary>
            <div class="stage-body">
              <div v-if="!stageByKey('operationAnnotation')" class="empty">未识别到阶段</div>
              <template v-else>
                <div class="stage-meta">
                  <span>起止行：{{ stageByKey('operationAnnotation').startLine ? 'L' + stageByKey('operationAnnotation').startLine : '未识别' }} - {{ stageByKey('operationAnnotation').endLine ? 'L' + stageByKey('operationAnnotation').endLine : '未识别' }}</span>
                  <span :class="['status-pill', stageByKey('operationAnnotation').completed ? 'success' : 'warn']">{{ stageByKey('operationAnnotation').completed ? '已完成' : '不完整' }}</span>
                </div>
                <div v-if="selectedSample.operationAnnotationResponses.length === 0" class="empty">无 LLM Response 记录</div>
                <div v-else class="class-explorer">
                  <button
                    v-for="item in selectedSample.operationAnnotationResponses"
                    :key="item.label + item.line"
                    type="button"
                    class="class-row"
                    :class="{ active: activeAnnotationResponse === item.label }"
                    @click="showStageResponse(item, 'annotation')"
                  >
                    <span class="class-row-title">{{ item.label }}</span>
                    <span class="class-row-meta">L{{ item.startLine }} - L{{ item.endLine }}</span>
                  </button>
                </div>
                <div v-if="stageByKey('operationAnnotation').events.length === 0" class="empty">无详细事件</div>
                <table v-else>
                  <tbody>
                    <tr v-for="event in stageByKey('operationAnnotation').events" :key="event.line + event.type + event.text">
                      <td><button type="button" class="line-button" @click="showLineContext(event.line)">L{{ event.line }}</button></td>
                      <td>{{ event.type }}</td>
                      <td>{{ event.text }}</td>
                    </tr>
                  </tbody>
                </table>
              </template>
            </div>
          </details>

          <details class="section">
            <summary>
              <span>操作规格校验阶段</span>
              <strong>{{ selectedSample.operationVerificationResponses.length }} 条</strong>
            </summary>
            <div class="stage-body">
              <div v-if="!stageByKey('operationVerification')" class="empty">未识别到阶段</div>
              <template v-else>
                <div class="stage-meta">
                  <span>起止行：{{ stageByKey('operationVerification').startLine ? 'L' + stageByKey('operationVerification').startLine : '未识别' }} - {{ stageByKey('operationVerification').endLine ? 'L' + stageByKey('operationVerification').endLine : '未识别' }}</span>
                  <span :class="['status-pill', stageByKey('operationVerification').completed ? 'success' : 'warn']">{{ stageByKey('operationVerification').completed ? '已完成' : '不完整' }}</span>
                </div>
                <div v-if="selectedSample.operationVerificationResponses.length === 0" class="empty">无 LLM Response 记录</div>
                <div v-else class="class-explorer">
                  <button
                    v-for="item in selectedSample.operationVerificationResponses"
                    :key="item.label + item.line"
                    type="button"
                    class="class-row"
                    :class="{ active: activeVerificationResponse === item.label }"
                    @click="showStageResponse(item, 'verification')"
                  >
                    <span class="class-row-title">{{ item.label }}</span>
                    <span class="class-row-meta">L{{ item.startLine }} - L{{ item.endLine }}</span>
                  </button>
                </div>
                <div v-if="stageByKey('operationVerification').events.length === 0" class="empty">无详细事件</div>
                <table v-else>
                  <tbody>
                    <tr v-for="event in stageByKey('operationVerification').events" :key="event.line + event.type + event.text">
                      <td><button type="button" class="line-button" @click="showLineContext(event.line)">L{{ event.line }}</button></td>
                      <td>{{ event.type }}</td>
                      <td>{{ event.text }}</td>
                    </tr>
                  </tbody>
                </table>
              </template>
            </div>
          </details>

          <details class="section">
            <summary>
              <span>代码补全阶段</span>
              <strong>{{ selectedSample.codeCompletionClasses.length }} 类</strong>
            </summary>
            <div v-if="selectedSample.codeCompletionClasses.length === 0" class="empty">无代码补全类记录</div>
            <div v-else class="class-explorer">
              <button
                v-for="item in selectedSample.codeCompletionClasses"
                :key="item.name + item.line"
                type="button"
                class="class-row"
                :class="{ active: activeCompletionClass === item.name }"
                @click="showClassResponse(item, 'completion')"
              >
                <span class="class-row-title">{{ item.name }}补全</span>
                <span class="class-row-meta">
                  <span v-if="item.responseStartLine && item.responseEndLine">LLM Response · L{{ item.responseStartLine }} - L{{ item.responseEndLine }}</span>
                  <span v-else>L{{ item.line }}</span>
                </span>
              </button>
            </div>
          </details>

          <details class="section">
            <summary>
              <span>代码修复阶段</span>
              <strong>{{ selectedSample.fixingClasses.length }} 类</strong>
            </summary>
            <div v-if="selectedSample.fixingClasses.length === 0" class="empty">无逐类修复记录</div>
            <div v-else class="class-explorer">
              <button
                v-for="item in selectedSample.fixingClasses"
                :key="item.name + item.line"
                type="button"
                class="class-row"
                :class="{ active: activeFixingClass === item.name }"
                @click="showClassResponse(item, 'fixing')"
              >
                <span class="class-row-title">{{ item.name }}修复</span>
                <span class="class-row-meta">
                  <span v-if="item.responseStartLine && item.responseEndLine">LLM Response · L{{ item.responseStartLine }} - L{{ item.responseEndLine }}</span>
                  <span v-else>L{{ item.line }}</span>
                </span>
              </button>
            </div>
            <div class="stage-body">
              <div v-if="!stageByKey('codeFixing')" class="empty">未识别到阶段</div>
              <template v-else>
                <div class="stage-meta">
                  <span>起止行：{{ stageByKey('codeFixing').startLine ? 'L' + stageByKey('codeFixing').startLine : '未识别' }} - {{ stageByKey('codeFixing').endLine ? 'L' + stageByKey('codeFixing').endLine : '未识别' }}</span>
                  <span :class="['status-pill', stageByKey('codeFixing').completed ? 'success' : 'warn']">{{ stageByKey('codeFixing').completed ? '已完成' : '不完整' }}</span>
                </div>
                <div v-if="stageByKey('codeFixing').events.length === 0" class="empty">无详细事件</div>
                <table v-else>
                  <tbody>
                    <tr v-for="event in stageByKey('codeFixing').events" :key="event.line + event.type + event.text">
                      <td><button type="button" class="line-button" @click="showLineContext(event.line)">L{{ event.line }}</button></td>
                      <td>{{ event.type }}</td>
                      <td>{{ event.text }}</td>
                    </tr>
                  </tbody>
                </table>
              </template>
            </div>
          </details>

          <details open class="section">
            <summary>
              <span>最终状态</span>
              <strong>{{ statusText(selectedSample.status) }}</strong>
            </summary>
            <div class="stage-body">
              <div v-if="!stageByKey('finalStatus')" class="empty">未识别到阶段</div>
              <template v-else>
                <div class="stage-meta">
                  <span>起止行：{{ stageByKey('finalStatus').startLine ? 'L' + stageByKey('finalStatus').startLine : '未识别' }} - {{ stageByKey('finalStatus').endLine ? 'L' + stageByKey('finalStatus').endLine : '未识别' }}</span>
                  <span :class="['status-pill', stageByKey('finalStatus').completed ? 'success' : 'warn']">{{ stageByKey('finalStatus').completed ? '已完成' : '不完整' }}</span>
                </div>
                <div v-if="stageByKey('finalStatus').events.length === 0" class="empty">无详细事件</div>
                <table v-else>
                  <tbody>
                    <tr v-for="event in stageByKey('finalStatus').events" :key="event.line + event.type + event.text">
                      <td><button type="button" class="line-button" @click="showLineContext(event.line)">L{{ event.line }}</button></td>
                      <td>{{ event.type }}</td>
                      <td>{{ event.text }}</td>
                    </tr>
                  </tbody>
                </table>
              </template>
            </div>
          </details>

          <section v-if="linePreview" class="line-preview">
            <div class="preview-head">
              <h3>原始日志上下文</h3>
              <span v-if="previewLoading">读取中</span>
              <span v-else>L{{ linePreview.startLine }} - L{{ linePreview.endLine }}</span>
            </div>
            <pre v-if="linePreview.error">{{ linePreview.error }}</pre>
            <pre v-else><code v-for="line in linePreview.lines" :key="line.line">{{ String(line.line).padStart(6, ' ') }}  {{ line.text }}
</code></pre>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>
