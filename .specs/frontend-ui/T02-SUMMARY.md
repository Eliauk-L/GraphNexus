# T02-SUMMARY: Design tokens + Tailwind + 全局排版

- **任务**: T02
- **Change**: `frontend-ui`
- **日期**: 2026-06-18

## 做了什么

按 UI-DESIGN.md frontmatter 创建 CSS variables + 全局排版 + base reset。

## 改动文件

| 文件 | 说明 |
|------|------|
| `frontend/src/assets/tokens.css` | 15 个 OKLCH 色 + 9 档 spacing + 5 档 rounded + 3 档 shadow + 4 档 motion + 3 个 font stack；含 prefers-reduced-motion |
| `frontend/src/assets/global.css` | Base reset + 9 级 typography class + 链接样式 + focus-visible ring + 滚动条 |
| `frontend/src/main.ts` | 添加 CSS import |

## verify 输出

```
$ npx vue-tsc --noEmit
(TYPECHECK OK)

$ cat src/assets/tokens.css | grep oklch
15 个 OKLCH 颜色变量全部输出
```

## 6 维自查

- **R1**: tokens.css 和 global.css 均为声明式 CSS，无复杂逻辑
- **R2**: 仅改动 frontend/src/assets/ 和 main.ts
- **R3-R6**: N/A

## 越界检查

- TASK write_files：3 项
- 实际改动：3 项
- 越界：0 ✅