/**
 * Neo4j GDS 图算法调用适配器（L3）—— 封装 {@code CALL gds.*.stream} Cypher 过程调用。
 *
 * <p>{@link com.graphnexus.infrastructure.neo4j.gds.GdsAdapter} 负责：
 * transient 命名图投影 → 算法执行（PageRank/度中心性）→ 结果映射 → 释放命名图。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
package com.graphnexus.infrastructure.neo4j.gds;