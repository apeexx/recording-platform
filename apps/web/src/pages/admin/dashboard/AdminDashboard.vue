<script setup>
import { adminDashboardData } from '../../../data/adminStaticData.js'
</script>

<template>
  <div class="admin-page">
    <section class="admin-dashboard-hero">
      <div>
        <span class="status-pill">静态前端原型</span>
        <h2>工作台</h2>
        <p>
          录音任务平台管理端工作台。当前展示脱敏示例数据、本地交互和模块入口，不接入业务接口。
        </p>
      </div>
    </section>

    <section class="admin-stat-grid" aria-label="工作台统计">
      <article
        v-for="metric in adminDashboardData.metrics"
        :key="metric.label"
        class="admin-stat-card admin-metric-card"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.helper }}</small>
      </article>
    </section>

    <section class="admin-dashboard-grid">
      <article class="admin-page-card">
        <div class="admin-card-heading">
          <h3>近期待办</h3>
        </div>
        <div class="admin-dashboard-todo">
          <button
            v-for="item in adminDashboardData.todo"
            :key="item.title"
            type="button"
            class="admin-dashboard-todo__item"
          >
            <span>{{ item.title }}</span>
            <strong>{{ item.status }}</strong>
          </button>
        </div>
      </article>

      <article class="admin-page-card">
        <div class="admin-card-heading">
          <h3>模块入口</h3>
        </div>
        <div class="admin-entry-list admin-entry-list--rich">
          <router-link
            v-for="entry in adminDashboardData.entries"
            :key="entry.path"
            :to="entry.path"
          >
            <strong>{{ entry.title }}</strong>
            <span>{{ entry.summary }}</span>
          </router-link>
        </div>
      </article>
    </section>

    <section class="admin-page-card">
      <div class="admin-card-heading">
        <h3>流程进度</h3>
        <span class="admin-section-label">静态趋势</span>
      </div>
      <div class="admin-progress-grid">
        <div
          v-for="item in adminDashboardData.progress"
          :key="item.label"
          class="admin-progress-item"
        >
          <div>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <div class="admin-progress-bar" aria-hidden="true">
            <span :style="{ width: item.value }"></span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
