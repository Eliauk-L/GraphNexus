/**
 * 图谱可视化常量 — 节点/边颜色、尺寸、线型、交互态参数。
 * 从 graphAdapter.ts 抽取，供 graphAdapter、GraphCanvas、GraphLegend 等模块共享。
 */

// ── 节点颜色（按 nodeType）──

export const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: '#3B82F6',   // 蓝 — 核心知识点
  Student: '#EC4899',          // 粉 — 学生
  Entity: '#F97316',           // 橙 — 原文片段
  Exam: '#8B5CF6',             // 紫 — 考试
  KnowledgeCategory: '#EAB308',// 黄 — 知识分类
  Document: '#10B981',         // 绿 — 源文档
}

// ── 节点大小（按 nodeType · px）──

export const NODE_SIZES: Record<string, number> = {
  KnowledgePoint: 40,
  Student: 35,
  Exam: 32,
  KnowledgeCategory: 38,
  Document: 32,
  Entity: 28,
}

// ── 边颜色（按 edgeType）──

export const EDGE_COLORS: Record<string, string> = {
  PREREQUISITE_OF: '#3B82F6',  // 蓝 — 前置依赖
  ALIGNED_TO: '#F97316',       // 橙 — 实体对齐
  MASTERS: '#EC4899',          // 粉 — 掌握度
  TESTED: '#8B5CF6',           // 紫 — 考试考查
  CHILD_OF: '#EAB308',         // 黄 — 层级归属
  BELONGS_TO: '#9CA3AF',       // 灰 — 学科归属
  REFERENCES: '#9CA3AF',       // 灰 — 引用
}

// ── 边线型（按 edgeType）──

export const EDGE_LINE_STYLES: Record<string, 'solid' | 'dashed'> = {
  PREREQUISITE_OF: 'solid',
  ALIGNED_TO: 'solid',
  MASTERS: 'solid',
  TESTED: 'solid',
  CHILD_OF: 'dashed',
  BELONGS_TO: 'dashed',
  REFERENCES: 'dashed',
}

// ── 交互态参数 ──

/** 非高亮节点/边透明度 */
export const DIM_OPACITY = 0.15

/** 高亮边宽度倍数 */
export const HIGHLIGHT_EDGE_WIDTH = 2.5

/** 路径高亮边宽度 px */
export const PATH_EDGE_WIDTH = 3

/** 选中态边框宽度 px */
export const SELECTED_BORDER_WIDTH = 3

/** Hover 缩放比例 */
export const HOVER_SCALE = 1.1

/** 默认边宽度 px */
export const DEFAULT_EDGE_WIDTH = 1.5

/** 默认边透明度 */
export const DEFAULT_EDGE_OPACITY = 0.6

/** 节点默认大小（兜底） */
export const DEFAULT_NODE_SIZE = 28

/** 节点默认颜色（兜底） */
export const DEFAULT_NODE_COLOR = '#8C8C8C'

/** 边默认颜色（兜底） */
export const DEFAULT_EDGE_COLOR = '#8C8C8C'
