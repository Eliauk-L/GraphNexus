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
  ALIGNED_TO: '#F97316',       // 橙 — 实体对齐到知识点
  PREREQUISITE_OF: '#3B82F6',  // 蓝 — 前置依赖
  BELONGS_TO: '#10B981',       // 绿 — 类别归属
  CHILD_OF: '#EAB308',         // 黄 — 层级
  MASTERS: '#EC4899',          // 粉 — 掌握度
  TESTED: '#8B5CF6',           // 紫 — 考试考查
  REFERENCES: '#9CA3AF',       // 灰 — 引用
  EXTRACTS: '#EF4444',         // 红 — 文档抽取
  DERIVES: '#EF4444',          // 红 — 推导
  CONTAINS: '#EF4444',         // 红 — 包含
}

// ── 边线型（按 edgeType）──

export const EDGE_LINE_STYLES: Record<string, 'solid' | 'dashed' | 'dotted'> = {
  ALIGNED_TO: 'solid',
  PREREQUISITE_OF: 'dashed',
  BELONGS_TO: 'dotted',
  CHILD_OF: 'dotted',
  MASTERS: 'solid',
  TESTED: 'dashed',
  REFERENCES: 'dotted',
  EXTRACTS: 'solid',
  DERIVES: 'dashed',
  CONTAINS: 'dashed',
}

/** 边宽度（按 edgeType · px），增强区分度 */
export const EDGE_WIDTHS: Record<string, number> = {
  ALIGNED_TO: 2.5,
  PREREQUISITE_OF: 2,
  BELONGS_TO: 1.5,
  CHILD_OF: 1.5,
  MASTERS: 3,
  TESTED: 2,
  REFERENCES: 1,
  EXTRACTS: 2.5,
  DERIVES: 2,
  CONTAINS: 2,
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
export const DEFAULT_EDGE_WIDTH = 2

/** 默认边透明度 */
export const DEFAULT_EDGE_OPACITY = 0.85

/** 节点默认大小（兜底） */
export const DEFAULT_NODE_SIZE = 28

/** 节点默认颜色（兜底） */
export const DEFAULT_NODE_COLOR = '#8C8C8C'

/** 边默认颜色（兜底） */
export const DEFAULT_EDGE_COLOR = '#8C8C8C'

// ── 度量映射参数 ──

/** 度量驱动的最小节点半径 px */
export const METRIC_SIZE_MIN = 20

/** 度量驱动的最大节点半径 px */
export const METRIC_SIZE_MAX = 60

/** 无度量数据时的默认节点半径 px（与 NODE_SIZES.KnowledgePoint 一致） */
export const METRIC_SIZE_DEFAULT = 40

/** PageRank 色阶（5 档 OKLCH 暖色梯度，低→高） */
export const PAGERANK_COLORS: string[] = [
  'oklch(0.55 0.18 250)',   // 0~20%   冷蓝（接近默认 KP 色）
  'oklch(0.65 0.10 180)',   // 20~40%  浅暖
  'oklch(0.60 0.15 120)',   // 40~60%  中暖
  'oklch(0.55 0.18 70)',    // 60~80%  暖橙
  'oklch(0.50 0.22 50)',    // 80~100% 深橙
]

/**
 * 计算值在数组中的百分位索引（0-based）。
 * 用于将 PageRank 值映射到色阶档位。
 */
export function percentileIndex(value: number, allValues: number[], bucketCount: number): number {
  if (allValues.length === 0) return 0
  const sorted = [...allValues].sort((a, b) => a - b)
  const rank = sorted.filter((v) => v < value).length
  return Math.min(Math.floor((rank / sorted.length) * bucketCount), bucketCount - 1)
}

/**
 * 线性映射：value 从 [inMin, inMax] 映射到 [outMin, outMax]。
 * 用 95 百分位截断防止离群值压缩主体分布。
 */
export function linearMap(
  value: number,
  inMin: number,
  inMax: number,
  outMin: number,
  outMax: number,
): number {
  if (inMax === inMin) return (outMin + outMax) / 2
  const clamped = Math.min(Math.max(value, inMin), inMax)
  return outMin + ((clamped - inMin) / (inMax - inMin)) * (outMax - outMin)
}
