<template>
  <div class="admin-page">
    <section class="admin-dashboard-hero">
      <div>
        <span class="status-pill">前端导航壳</span>
        <h2>工作台</h2>
        <p>录音任务平台管理端。当前仅展示后台布局、菜单和模块入口，不接入业务接口。</p>
      </div>
    </section>

    <section class="admin-stat-grid" aria-label="工作台统计占位">
      <article class="admin-stat-card">
        <span>待发布任务</span>
        <strong>待接入</strong>
      </article>
      <article class="admin-stat-card">
        <span>待审核录音</span>
        <strong>待接入</strong>
      </article>
      <article class="admin-stat-card">
        <span>已通过结果</span>
        <strong>待接入</strong>
      </article>
      <article class="admin-stat-card">
        <span>被驳回任务</span>
        <strong>待接入</strong>
      </article>
    </section>

    <section class="admin-dashboard-grid">
      <article class="admin-page-card">
        <h3>任务流程概览</h3>
        <ol class="admin-flow-list">
          <li>管理员创建录音任务</li>
          <li>录音人员领取并上传</li>
          <li>进入机器审核、一审或二审</li>
          <li>通过后进入结果管理</li>
        </ol>
      </article>

      <article class="admin-page-card">
        <h3>近期模块入口</h3>
        <div class="admin-entry-list">
          <router-link to="/admin/tasks/batches">任务批次</router-link>
          <router-link to="/admin/review/overview">审核总览</router-link>
          <router-link to="/admin/results/approved">通过结果</router-link>
          <router-link to="/admin/voice-generation/workbench">语音生成</router-link>
        </div>
      </article>
    </section>
  </div>
</template>
