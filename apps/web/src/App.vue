<template>
  <main class="app-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">录音任务平台</p>
        <h1 class="page-title">Web 端主题规范确认</h1>
        <p class="page-subtitle">
          当前阶段只落地前端主题变量和基础视觉样式，为后续管理员端、审核端页面提供统一的浅色优先界面基础。
        </p>
      </div>
      <span class="status-pill">基础主题已预留</span>
    </header>

    <section class="theme-preview" aria-labelledby="theme-preview-title">
      <h2 id="theme-preview-title" class="section-title">模块预览</h2>
      <div class="card-grid">
        <article class="page-card">
          <span class="card-marker">A</span>
          <h2>管理员端</h2>
          <p>后续用于任务创建、任务状态查看和运营管理入口，本轮仅展示统一卡片样式。</p>
        </article>

        <article class="page-card">
          <span class="card-marker">R</span>
          <h2>审核端</h2>
          <p>后续预留给机器审核、一审和二审流程页面，本轮不实现审核业务。</p>
        </article>

        <article class="page-card">
          <span class="card-marker">M</span>
          <h2>小程序录音端</h2>
          <p>后续对应录音人员领取、录制和提交入口，本轮只作为 Web 端视觉参考。</p>
        </article>
      </div>

      <div class="demo-row">
        <div>
          <strong>通用控件示例</strong>
          <p class="muted-text">按钮、状态标签、卡片和边框均优先使用主题变量。</p>
        </div>
        <button class="primary-button" type="button">主按钮示例</button>
      </div>
    </section>
  </main>
</template>
