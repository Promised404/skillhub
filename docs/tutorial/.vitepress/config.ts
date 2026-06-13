import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'FR24 SkillHub 使用教程',
  description: 'SkillHub Skill 开发者与团队管理员使用指南',
  base: '/tutorial/',

  head: [],

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '快速上手', link: '/quick-start' },
      { text: '完整指南', link: '/full-guide' },
      { text: 'SkillHub', link: 'https://skillhub.fr24.ai' },
    ],
    sidebar: [
      {
        text: '开始',
        items: [
          { text: '教程首页', link: '/' },
          { text: '快速上手', link: '/quick-start' },
        ],
      },
      {
        text: '详细指南',
        items: [
          { text: '完整指南', link: '/full-guide' },
        ],
      },
    ],
    outline: { label: '页面导航', level: [2, 3] },
    lastUpdated: { text: '最后更新' },
    docFooter: { prev: '上一页', next: '下一页' },
    footer: { message: '版权所有 © 科大讯飞股份有限公司' },

    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档' },
          modal: {
            noResultsText: '未找到结果',
            resetButtonTitle: '清除搜索',
            footer: { selectText: '选择', navigateText: '切换' },
          },
        },
      },
    },
  },
})
