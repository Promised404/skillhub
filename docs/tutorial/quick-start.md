---
title: 快速上手
---

# 快速上手

10 分钟从零到发布你的第一个 Skill。

## 第一步：登录

1. 打开 [SkillHub](https://skillhub.fr24.ai)
2. 使用公司 OMS 账号登录

## 第二步：获取 API Token

这是让 AI 助手（OpenClaw / WorkBuddy）操作 SkillHub 的凭证。

1. 登录后，点击右上角头像 → **Settings**
2. 进入 **API Tokens** 页面
3. 点击 **Create Token**，给个名字（比如"日常使用"），选择有效期
4. 复制生成的 Token（**只会显示一次，请妥善保存**）

## 第三步：安装 fr24-skillhub 技能

这个技能让你可以用自然语言操作 SkillHub，不用记命令。

对 AI 助手（OpenClaw / WorkBuddy）说：

> 帮我安装 fr24-skillhub 技能，使用 registry https://skillhub.fr24.ai，版本 latest

AI 助手会自动完成安装，你不需要自己操作终端。

安装完成后，接着说：

> 用这个 token 登录公司 SkillHub：`sk_xxxxx`

AI 助手会自动验证 Token 并保存，之后所有操作都不用再输入 Token。

## 第四步：搜索并安装一个 Skill

对 AI 助手说：

> 查一下公司有哪些和日报相关的 skill

系统会返回搜索结果。找到你想要的之后说：

> 安装 daily-report 这个 skill

搞定！你现在可以在 AI 助手中使用这个技能了。

### 线上实践：验证 SkillHub 接入

安装 fr24-skillhub 后，对 AI 助手说：

> 查一下公司最近发布了哪些 skill

如果能看到 `fr24-ai` 等公开技能，说明你的 SkillHub 接入已经跑通。

## 第五步：创建并发布你的第一个 Skill

### 1. 准备目录

创建一个文件夹，里面放一个 `SKILL.md` 文件：

```
my-first-skill/
└── SKILL.md
```

### 2. 写 SKILL.md

最简模板如下：

```markdown
---
name: my-first-skill
description: 这是一个示例技能，用来演示发布流程
---

# 我的第一个技能

这里写技能的具体内容和使用说明。
```

只需要填两个东西：
- **name**：技能名称，用英文小写加横线，比如 `daily-report`
- **description**：一句话说清楚这个技能是干什么的

你还可以往目录里加其他文件（脚本、文档、图片等），但 `SKILL.md` 是必须的。

### 3. 发布

对 AI 助手说：

> 把当前目录的 skill 发布到公司 SkillHub

AI 助手会先确认发布详情（目标地址、团队、版本号、可见性），你确认后才会执行。不用担心误操作。

**网页备选方式**：登录 SkillHub → Dashboard → 发布 → 选择团队 → 上传文件 → 填写版本说明 → 点击发布

## 之后呢？

- **搜 Skill**：对 AI 助手说 "搜一下 xxx 相关的 skill"
- **装 Skill**：对 AI 助手说 "安装 xxx skill"
- **发新版本**：改好文件后再次说 "发布这个 skill"，版本号递增即可
- **看详情**：对 AI 助手说 "看看 xxx skill 是做什么的"

更多细节请参考 [完整指南](/full-guide)。
