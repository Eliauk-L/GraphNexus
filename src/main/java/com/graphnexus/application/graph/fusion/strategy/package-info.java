/**
 * 宽图谱融合策略包 — KP 匹配策略与权重计算策略的接口与实现。
 *
 * <p>策略通过 yml 配置切换（fusion.kp-matching.strategy / fusion.weight.strategy），
 * 新增算法只需实现对应接口并在 yml 中注册即可。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
package com.graphnexus.application.graph.fusion.strategy;