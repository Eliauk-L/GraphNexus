import type { GraphSubgraphVO, SubgraphResponse } from '@/api/types'
import type { Node, Edge } from 'vis-network'

export interface VisGraphData {
  nodes: Node[]
  edges: Edge[]
}

const NODE_COLORS: Record<string, string> = {
  KnowledgePoint: '#4A90D9',
  Student: '#52C41A',
  Entity: '#8C8C8C',
  Exam: '#FAAD14',
  KnowledgeCategory: '#722ED1',
  Document: '#BFBFBF',
}

const EDGE_COLORS: Record<string, string> = {
  PREREQUISITE_OF: '#4A90D9',
  ALIGNED_TO: '#52C41A',
  MASTERS: '#FAAD14',
  TESTED: '#FF7A45',
  CHILD_OF: '#722ED1',
}

export function transformGraphSubgraphVO(vo: GraphSubgraphVO): VisGraphData {
  const nodeIds = new Set(vo.nodes.map((n) => n.id))

  return {
    nodes: vo.nodes.map((n) => ({
      id: n.id,
      label: nodeLabel(n.nodeType, {}),
      color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
      size: nodeSize(n.nodeType),
      font: { size: 12, color: '#333' },
    } satisfies Node)),
    edges: vo.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e) => ({
        from: e.sourceNodeId,
        to: e.targetNodeId,
        label: shortLabel(e.edgeType),
        color: { color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C' },
        dashes: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType),
        arrows: 'to',
      } satisfies Edge)),
  }
}

export function transformSubgraphResponse(res: SubgraphResponse): VisGraphData {
  const nodeIds = new Set(res.nodes.map((n) => n.id))

  return {
    nodes: res.nodes.map((n) => ({
      id: n.id,
      label: nodeLabel(n.nodeType, n.properties),
      color: NODE_COLORS[n.nodeType] ?? '#8C8C8C',
      size: nodeSize(n.nodeType),
      font: { size: 12, color: '#333' },
      title: formatProperties(n.properties),
    } satisfies Node)),
    edges: res.edges
      .filter((e) => nodeIds.has(e.sourceNodeId) && nodeIds.has(e.targetNodeId))
      .map((e) => ({
        from: e.sourceNodeId,
        to: e.targetNodeId,
        label: shortLabel(e.edgeType),
        color: { color: EDGE_COLORS[e.edgeType] ?? '#8C8C8C' },
        width: 1 + Math.min(e.weight, 3),
        dashes: ['BELONGS_TO', 'CHILD_OF', 'REFERENCES'].includes(e.edgeType),
        arrows: 'to',
      } satisfies Edge)),
  }
}

function nodeLabel(nodeType: string, props: Record<string, unknown>): string {
  return (props.name as string) ?? (props.label as string) ?? nodeType
}

function nodeSize(nodeType: string): number {
  const map: Record<string, number> = {
    KnowledgePoint: 36, Student: 32, Exam: 30,
    KnowledgeCategory: 34, Document: 30, Entity: 28,
  }
  return map[nodeType] ?? 28
}

function shortLabel(type: string): string {
  return type.length > 12 ? type.substring(0, 11) + '…' : type
}

function formatProperties(props: Record<string, unknown>): string {
  return Object.entries(props)
    .map(([k, v]) => `<b>${k}:</b> ${String(v)}`)
    .join('<br>')
}