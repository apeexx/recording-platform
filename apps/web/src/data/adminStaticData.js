const commonFilters = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '进行中' },
  { key: 'pending', label: '待处理' },
  { key: 'done', label: '已完成' }
]

const reviewFilters = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待审核' },
  { key: 'active', label: '审核中' },
  { key: 'rejected', label: '已驳回' },
  { key: 'done', label: '已通过' }
]

const systemFilters = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '启用' },
  { key: 'pending', label: '待配置' },
  { key: 'done', label: '已归档' }
]

function makeMetrics(primary, secondary, tertiary, quaternary) {
  return [
    { label: primary.label, value: primary.value, tone: 'primary' },
    { label: secondary.label, value: secondary.value, tone: 'success' },
    { label: tertiary.label, value: tertiary.value, tone: 'warning' },
    { label: quaternary.label, value: quaternary.value, tone: 'danger' }
  ]
}

function makeRows(prefix, statuses = ['active', 'pending', 'done']) {
  return [
    {
      id: `${prefix}-001`,
      name: `${prefix} 示例一`,
      owner: '管理员 A',
      status: statuses[0],
      statusText: statuses[0] === 'done' ? '已完成' : '进行中',
      progress: '72%',
      updatedAt: '2026-06-27 09:20'
    },
    {
      id: `${prefix}-002`,
      name: `${prefix} 示例二`,
      owner: '审核员 B',
      status: statuses[1],
      statusText: statuses[1] === 'rejected' ? '已驳回' : '待处理',
      progress: '38%',
      updatedAt: '2026-06-27 10:05'
    },
    {
      id: `${prefix}-003`,
      name: `${prefix} 示例三`,
      owner: '录音员 C',
      status: statuses[2],
      statusText: statuses[2] === 'rejected' ? '已驳回' : '已完成',
      progress: '100%',
      updatedAt: '2026-06-26 18:40'
    }
  ]
}

function prototypePage({
  title,
  description,
  module,
  action,
  metrics,
  filters = commonFilters,
  rows,
  tabs,
  timeline,
  checklist
}) {
  return {
    title,
    description,
    module,
    action,
    filters,
    metrics,
    rows,
    tabs,
    timeline,
    checklist
  }
}

export const adminPrototypePages = {
  'language-types': prototypePage({
    title: '语言类型',
    module: '基础设置',
    action: '新增语言类型',
    description: '维护录音任务可用语言类型的静态原型，当前仅展示配置入口和本地启停状态。',
    metrics: makeMetrics(
      { label: '语言示例', value: '8' },
      { label: '启用中', value: '6' },
      { label: '待配置', value: '2' },
      { label: '停用', value: '1' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'LANG-001', name: '普通话', owner: '基础配置组', status: 'active', statusText: '启用', progress: '默认', updatedAt: '2026-06-27 09:18' },
      { id: 'LANG-002', name: '英语', owner: '基础配置组', status: 'active', statusText: '启用', progress: '可选', updatedAt: '2026-06-26 16:44' },
      { id: 'LANG-003', name: '方言示例', owner: '待接口接入', status: 'pending', statusText: '待配置', progress: '草稿', updatedAt: '2026-06-25 11:30' }
    ],
    tabs: ['语言列表', '启停策略', '录音提示'],
    timeline: ['新增语言仅影响后续任务示例', '停用语言不会删除历史展示', '正式接入前需确认语言编码'],
    checklist: ['检查语言名称', '检查录音端显示文案', '检查后续任务筛选入口']
  }),
  'split-rules': prototypePage({
    title: '切分规则',
    module: '基础设置',
    action: '新建规则',
    description: '配置文本切分方式的静态原型，用于演示规则优先级、启用状态和预览结果。',
    metrics: makeMetrics(
      { label: '规则示例', value: '5' },
      { label: '启用中', value: '3' },
      { label: '待验证', value: '2' },
      { label: '停用', value: '1' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'RULE-001', name: '按句号切分', owner: '文本配置组', status: 'active', statusText: '启用', progress: '优先级 1', updatedAt: '2026-06-27 08:56' },
      { id: 'RULE-002', name: '按字数切分', owner: '文本配置组', status: 'pending', statusText: '待验证', progress: '优先级 2', updatedAt: '2026-06-26 15:12' },
      { id: 'RULE-003', name: '按换行切分', owner: '待接口接入', status: 'done', statusText: '已归档', progress: '历史规则', updatedAt: '2026-06-24 17:48' }
    ],
    tabs: ['规则列表', '切分预览', '优先级'],
    timeline: ['导入文本后读取启用规则', '静态预览展示切分片段', '正式接入后由后端保存规则'],
    checklist: ['确认规则名称', '检查优先级冲突', '保留历史任务兼容说明']
  }),
  announcements: prototypePage({
    title: '系统公告',
    module: '基础设置',
    action: '发布公告',
    description: '发布和管理公告的静态原型，支持本地切换已读和发布状态。',
    metrics: makeMetrics(
      { label: '公告示例', value: '6' },
      { label: '展示中', value: '3' },
      { label: '待发布', value: '2' },
      { label: '已下线', value: '1' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'ANN-001', name: '录音规范更新提醒', owner: '平台运营', status: 'active', statusText: '展示中', progress: '全员可见', updatedAt: '2026-06-27 10:10' },
      { id: 'ANN-002', name: '审核节奏说明', owner: '审核组', status: 'pending', statusText: '待发布', progress: '管理员可见', updatedAt: '2026-06-26 19:35' },
      { id: 'ANN-003', name: '旧版本入口下线', owner: '系统管理员', status: 'done', statusText: '已下线', progress: '历史公告', updatedAt: '2026-06-22 12:00' }
    ],
    tabs: ['公告列表', '展示范围', '发布预览'],
    timeline: ['编辑公告草稿', '选择展示范围', '发布后同步到前端入口'],
    checklist: ['避免写入敏感信息', '检查展示时间', '确认公告对象']
  }),
  'text-import': prototypePage({
    title: '文本导入',
    module: '文本处理',
    action: '模拟导入',
    description: '导入录音文本素材的静态原型，当前只展示上传区域、校验结果和模拟入库步骤。',
    metrics: makeMetrics(
      { label: '待导入批次', value: '4' },
      { label: '校验通过', value: '3' },
      { label: '待修正', value: '1' },
      { label: '失败示例', value: '0' }
    ),
    rows: makeRows('文本导入'),
    tabs: ['上传文件', '字段校验', '导入预览'],
    timeline: ['选择 CSV 或 Excel 示例文件', '前端展示字段校验结果', '点击确认后显示模拟成功提示'],
    checklist: ['不上传真实客户文本', '检查文本编号', '检查语言类型']
  }),
  'text-list': prototypePage({
    title: '文本列表',
    module: '文本处理',
    action: '批量标记',
    description: '查看和维护录音文本的静态原型，支持筛选、查看详情和本地状态切换。',
    metrics: makeMetrics(
      { label: '文本示例', value: '128' },
      { label: '可发布', value: '96' },
      { label: '待清洗', value: '18' },
      { label: '已停用', value: '14' }
    ),
    rows: makeRows('文本素材'),
    tabs: ['文本列表', '质量标签', '使用记录'],
    timeline: ['导入文本素材', '清洗和切分', '进入任务发布候选池'],
    checklist: ['检查敏感词', '检查重复文本', '检查字数范围']
  }),
  'text-batches': prototypePage({
    title: '文本批次',
    module: '文本处理',
    action: '创建批次',
    description: '管理文本批次的静态原型，用于演示批次进度、负责人和后续任务关联。',
    metrics: makeMetrics(
      { label: '批次示例', value: '12' },
      { label: '处理中', value: '5' },
      { label: '待确认', value: '3' },
      { label: '已完成', value: '4' }
    ),
    rows: makeRows('文本批次'),
    tabs: ['批次列表', '切分进度', '关联任务'],
    timeline: ['批次创建', '文本校验', '切分完成后进入任务发布'],
    checklist: ['确认批次名称', '确认文本数量', '确认关联语言']
  }),
  'task-batches': prototypePage({
    title: '任务批次',
    module: '录音任务',
    action: '新建任务批次',
    description: '管理录音任务批次的静态原型，展示发布进度、领取进度和回收入口。',
    metrics: makeMetrics(
      { label: '任务批次', value: '9' },
      { label: '发布中', value: '4' },
      { label: '待发布', value: '2' },
      { label: '已结束', value: '3' }
    ),
    rows: makeRows('任务批次'),
    tabs: ['批次概览', '领取进度', '审核进度'],
    timeline: ['选择文本批次', '配置录音要求', '发布到录音端'],
    checklist: ['确认任务数量', '确认录音时长', '确认领取规则']
  }),
  'task-publish': prototypePage({
    title: '任务发布',
    module: '录音任务',
    action: '模拟发布',
    description: '发布录音任务的静态原型，使用步骤区展示发布前检查，不产生真实任务。',
    metrics: makeMetrics(
      { label: '待发布', value: '18' },
      { label: '可发布', value: '12' },
      { label: '待补全', value: '6' },
      { label: '异常', value: '0' }
    ),
    rows: makeRows('发布计划'),
    tabs: ['发布配置', '录音要求', '确认发布'],
    timeline: ['选择任务批次', '设置领取上限', '确认后展示模拟发布结果'],
    checklist: ['检查文本来源', '检查录音端提示', '检查截止时间']
  }),
  'task-claims': prototypePage({
    title: '领取情况',
    module: '录音任务',
    action: '刷新示例',
    description: '查看录音任务领取情况的静态原型，展示领取人、进度和本地筛选。',
    metrics: makeMetrics(
      { label: '领取记录', value: '64' },
      { label: '录制中', value: '28' },
      { label: '待提交', value: '16' },
      { label: '已提交', value: '20' }
    ),
    rows: makeRows('领取记录'),
    tabs: ['领取列表', '进度分布', '异常提醒'],
    timeline: ['录音员领取任务', '本地展示录制进度', '提交后进入审核队列'],
    checklist: ['检查重复领取', '检查超时任务', '检查提交状态']
  }),
  'task-recycle': prototypePage({
    title: '任务回收',
    module: '录音任务',
    action: '模拟回收',
    description: '处理任务回收的静态原型，展示超时、主动释放和管理员回收状态。',
    metrics: makeMetrics(
      { label: '可回收', value: '11' },
      { label: '超时', value: '5' },
      { label: '待确认', value: '3' },
      { label: '已回收', value: '3' }
    ),
    rows: makeRows('回收任务'),
    tabs: ['回收队列', '回收原因', '处理记录'],
    timeline: ['识别超时任务', '管理员确认回收', '任务回到可领取池'],
    checklist: ['确认回收原因', '保留操作记录', '提醒录音员刷新页面']
  }),
  'review-overview': prototypePage({
    title: '审核总览',
    module: '录音审核',
    action: '查看队列',
    description: '查看审核整体状态的静态原型，展示机器审核、一审、二审和驳回分布。',
    metrics: makeMetrics(
      { label: '待审核', value: '42' },
      { label: '审核中', value: '18' },
      { label: '已通过', value: '96' },
      { label: '已驳回', value: '7' }
    ),
    filters: reviewFilters,
    rows: makeRows('审核队列', ['pending', 'active', 'done']),
    tabs: ['审核概览', '队列分布', '风险提示'],
    timeline: ['录音上传后进入机器审核', '按规则分配一审或二审', '驳回后等待重新录制'],
    checklist: ['检查机器审核结果', '检查人工审核队列', '检查驳回原因']
  }),
  'first-review': prototypePage({
    title: '一审管理',
    module: '录音审核',
    action: '模拟通过',
    description: '处理一审工作的静态原型，支持本地切换通过、驳回和详情预览。',
    metrics: makeMetrics(
      { label: '一审待办', value: '24' },
      { label: '审核中', value: '8' },
      { label: '已通过', value: '38' },
      { label: '已驳回', value: '5' }
    ),
    filters: reviewFilters,
    rows: makeRows('一审录音', ['pending', 'active', 'rejected']),
    tabs: ['待审录音', '质检项', '处理记录'],
    timeline: ['打开录音详情', '检查文本匹配和噪声', '选择通过或驳回示例状态'],
    checklist: ['确认音频可播放', '确认文本一致', '填写驳回原因']
  }),
  'second-review': prototypePage({
    title: '二审管理',
    module: '录音审核',
    action: '模拟复核',
    description: '处理二审工作的静态原型，展示复核队列、重点问题和通过状态。',
    metrics: makeMetrics(
      { label: '二审待办', value: '12' },
      { label: '复核中', value: '4' },
      { label: '已完成', value: '21' },
      { label: '退回一审', value: '2' }
    ),
    filters: reviewFilters,
    rows: makeRows('二审录音', ['pending', 'active', 'done']),
    tabs: ['复核队列', '抽检规则', '复核记录'],
    timeline: ['读取一审结果', '复核关键质检项', '确认通过后进入结果区'],
    checklist: ['检查一审意见', '检查异常波形', '确认最终状态']
  }),
  'rejected-records': prototypePage({
    title: '驳回记录',
    module: '录音审核',
    action: '查看原因',
    description: '查看审核驳回记录的静态原型，展示驳回原因、重录状态和跟进人。',
    metrics: makeMetrics(
      { label: '驳回记录', value: '17' },
      { label: '待重录', value: '6' },
      { label: '重录中', value: '4' },
      { label: '已关闭', value: '7' }
    ),
    filters: reviewFilters,
    rows: makeRows('驳回记录', ['rejected', 'pending', 'done']),
    tabs: ['驳回列表', '原因统计', '重录跟进'],
    timeline: ['审核驳回', '录音员重新录制', '再次提交进入审核'],
    checklist: ['记录驳回原因', '提醒重录要求', '检查再次提交状态']
  }),
  'approved-results': prototypePage({
    title: '通过结果',
    module: '录音结果',
    action: '查看结果',
    description: '查看审核通过录音结果的静态原型，展示结果汇总、文件状态和导出入口。',
    metrics: makeMetrics(
      { label: '通过结果', value: '186' },
      { label: '可导出', value: '142' },
      { label: '待归档', value: '28' },
      { label: '异常', value: '0' }
    ),
    rows: makeRows('通过结果'),
    tabs: ['结果列表', '文件校验', '导出准备'],
    timeline: ['审核通过', '文件进入结果池', '按批次导出或归档'],
    checklist: ['检查文件完整性', '检查文本映射', '检查导出字段']
  }),
  'audio-files': prototypePage({
    title: '音频文件',
    module: '录音结果',
    action: '预览文件',
    description: '查看录音文件元数据入口的静态原型，不展示真实音频地址或完整签名链接。',
    metrics: makeMetrics(
      { label: '文件示例', value: '240' },
      { label: '已校验', value: '210' },
      { label: '待处理', value: '30' },
      { label: '异常', value: '2' }
    ),
    rows: makeRows('音频文件'),
    tabs: ['文件列表', '元数据', '存储状态'],
    timeline: ['上传后生成文件元数据', '审核通过后进入结果池', '导出时只使用脱敏摘要'],
    checklist: ['不展示完整音频 URL', '检查文件时长', '检查文件归属']
  }),
  'result-export': prototypePage({
    title: '结果导出',
    module: '录音结果',
    action: '模拟导出',
    description: '导出录音结果的静态原型，展示字段选择、任务队列和模拟完成状态。',
    metrics: makeMetrics(
      { label: '导出任务', value: '7' },
      { label: '处理中', value: '2' },
      { label: '待确认', value: '1' },
      { label: '已完成', value: '4' }
    ),
    rows: makeRows('导出任务'),
    tabs: ['字段选择', '导出队列', '完成记录'],
    timeline: ['选择结果范围', '选择导出字段', '生成模拟导出任务'],
    checklist: ['不导出敏感字段', '确认文件命名', '检查结果范围']
  }),
  'project-statistics': prototypePage({
    title: '项目统计',
    module: '工作报表',
    action: '切换维度',
    description: '查看项目维度统计的静态原型，用进度条和摘要展示任务、审核、结果情况。',
    metrics: makeMetrics(
      { label: '项目示例', value: '6' },
      { label: '进行中', value: '4' },
      { label: '待复核', value: '2' },
      { label: '已完成', value: '2' }
    ),
    rows: makeRows('项目统计'),
    tabs: ['项目维度', '任务趋势', '质量分布'],
    timeline: ['按项目聚合任务', '按审核状态拆分', '展示静态趋势摘要'],
    checklist: ['确认统计口径', '确认时间范围', '确认展示维度']
  }),
  'recorder-statistics': prototypePage({
    title: '录音员统计',
    module: '工作报表',
    action: '查看人员',
    description: '查看录音员维度统计的静态原型，展示领取、提交、通过和驳回摘要。',
    metrics: makeMetrics(
      { label: '录音员示例', value: '32' },
      { label: '活跃', value: '18' },
      { label: '待提交', value: '9' },
      { label: '异常', value: '1' }
    ),
    rows: makeRows('录音员'),
    tabs: ['人员排行', '提交趋势', '质量摘要'],
    timeline: ['领取任务', '提交录音', '按通过率形成静态报表'],
    checklist: ['只展示脱敏姓名', '检查统计周期', '检查异常记录']
  }),
  'reviewer-statistics': prototypePage({
    title: '审核员统计',
    module: '工作报表',
    action: '查看审核员',
    description: '查看审核员维度统计的静态原型，展示审核量、平均耗时和驳回比例。',
    metrics: makeMetrics(
      { label: '审核员示例', value: '12' },
      { label: '在线', value: '7' },
      { label: '待处理', value: '16' },
      { label: '复核异常', value: '2' }
    ),
    rows: makeRows('审核员'),
    tabs: ['审核排行', '耗时分析', '驳回原因'],
    timeline: ['分配审核队列', '记录处理结果', '形成静态绩效摘要'],
    checklist: ['确认审核口径', '避免人员敏感信息', '检查二审差异']
  }),
  users: prototypePage({
    title: '用户管理',
    module: '系统管理',
    action: '新增用户',
    description: '管理平台用户的静态原型，展示脱敏用户、角色和启用状态，不实现登录权限。',
    metrics: makeMetrics(
      { label: '用户示例', value: '28' },
      { label: '启用', value: '21' },
      { label: '待配置', value: '5' },
      { label: '停用', value: '2' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'USER-001', name: '管理员示例', owner: '系统管理', status: 'active', statusText: '启用', progress: '管理员', updatedAt: '2026-06-27 09:40' },
      { id: 'USER-002', name: '审核员示例', owner: '审核组', status: 'active', statusText: '启用', progress: '审核员', updatedAt: '2026-06-26 18:22' },
      { id: 'USER-003', name: '录音员示例', owner: '录音组', status: 'pending', statusText: '待配置', progress: '录音员', updatedAt: '2026-06-25 15:16' }
    ],
    tabs: ['用户列表', '角色绑定', '状态记录'],
    timeline: ['创建脱敏用户示例', '绑定角色', '正式接入后由后端鉴权'],
    checklist: ['不写入真实员工信息', '检查角色范围', '检查停用状态']
  }),
  roles: prototypePage({
    title: '角色权限',
    module: '系统管理',
    action: '配置角色',
    description: '管理角色和权限的静态原型，当前仅演示角色分组和菜单范围，不实现真实权限控制。',
    metrics: makeMetrics(
      { label: '角色示例', value: '5' },
      { label: '启用', value: '4' },
      { label: '待确认', value: '1' },
      { label: '停用', value: '0' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'ROLE-001', name: '系统管理员', owner: '系统管理', status: 'active', statusText: '启用', progress: '全菜单示例', updatedAt: '2026-06-27 09:30' },
      { id: 'ROLE-002', name: '审核员', owner: '审核组', status: 'active', statusText: '启用', progress: '审核菜单', updatedAt: '2026-06-26 17:20' },
      { id: 'ROLE-003', name: '录音员', owner: '录音组', status: 'pending', statusText: '待确认', progress: '小程序端', updatedAt: '2026-06-25 14:00' }
    ],
    tabs: ['角色列表', '菜单范围', '权限说明'],
    timeline: ['定义角色名称', '选择菜单范围', '后续登录阶段再接入鉴权'],
    checklist: ['不实现 JWT', '不写后端权限', '保留菜单配置一致性']
  }),
  'operation-logs': prototypePage({
    title: '操作日志',
    module: '系统管理',
    action: '筛选日志',
    description: '查看平台操作日志的静态原型，仅展示必要摘要，不记录敏感 payload。',
    metrics: makeMetrics(
      { label: '日志示例', value: '86' },
      { label: '普通操作', value: '71' },
      { label: '待确认', value: '10' },
      { label: '风险提示', value: '5' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'LOG-001', name: '任务批次状态切换', owner: '管理员示例', status: 'active', statusText: '普通', progress: 'requestId 摘要', updatedAt: '2026-06-27 10:30' },
      { id: 'LOG-002', name: '审核驳回示例', owner: '审核员示例', status: 'pending', statusText: '待确认', progress: '无敏感载荷', updatedAt: '2026-06-27 09:55' },
      { id: 'LOG-003', name: '导出任务完成', owner: '系统示例', status: 'done', statusText: '已归档', progress: '状态码 200', updatedAt: '2026-06-26 20:12' }
    ],
    tabs: ['日志列表', '风险摘要', '保留策略'],
    timeline: ['记录操作摘要', '过滤敏感字段', '按时间查询日志'],
    checklist: ['不记录敏感凭证', '不记录完整 payload', '保留必要 requestId']
  }),
  'system-settings': prototypePage({
    title: '系统设置',
    module: '系统管理',
    action: '保存示例',
    description: '维护平台系统设置的静态原型，仅提供本地开关和配置摘要，不写入环境变量。',
    metrics: makeMetrics(
      { label: '设置分组', value: '6' },
      { label: '启用', value: '4' },
      { label: '待配置', value: '2' },
      { label: '风险项', value: '0' }
    ),
    filters: systemFilters,
    rows: [
      { id: 'SET-001', name: '审核队列提醒', owner: '系统设置', status: 'active', statusText: '启用', progress: '站内提示', updatedAt: '2026-06-27 10:08' },
      { id: 'SET-002', name: '导出字段模板', owner: '结果管理', status: 'pending', statusText: '待配置', progress: '静态模板', updatedAt: '2026-06-26 18:44' },
      { id: 'SET-003', name: '录音端提示', owner: '小程序预留', status: 'active', statusText: '启用', progress: '示例文案', updatedAt: '2026-06-25 17:33' }
    ],
    tabs: ['基础设置', '提醒设置', '安全说明'],
    timeline: ['修改本地开关', '展示模拟保存提示', '后续由后端配置持久化'],
    checklist: ['不写入 .env', '不保存真实密钥', '检查默认状态']
  })
}

export const adminDashboardData = {
  metrics: [
    { label: '待发布任务', value: '18', helper: '静态示例' },
    { label: '待审核录音', value: '42', helper: '含一审和二审' },
    { label: '可导出结果', value: '142', helper: '已通过汇总' },
    { label: '待处理驳回', value: '7', helper: '等待重录' }
  ],
  entries: [
    { title: '任务批次', path: '/admin/tasks/batches', summary: '查看发布进度和领取情况' },
    { title: '文本导入', path: '/admin/text/import', summary: '模拟上传、校验和预览' },
    { title: '审核总览', path: '/admin/review/overview', summary: '查看审核队列和风险提示' },
    { title: '结果导出', path: '/admin/results/export', summary: '选择字段并生成模拟导出任务' }
  ],
  todo: [
    { title: '确认文本批次字段', status: '待处理' },
    { title: '复核一审驳回原因', status: '进行中' },
    { title: '检查导出字段脱敏', status: '待确认' }
  ],
  progress: [
    { label: '文本准备', value: '84%' },
    { label: '任务发布', value: '61%' },
    { label: '录音审核', value: '48%' },
    { label: '结果归档', value: '72%' }
  ]
}
