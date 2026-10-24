# Week 7

本周项目在之前课程内容的基础上整合 Google SSO、JWT、OpenAI、Google Sheets、Speech to Text 和 Text to Speech，提供 Android 与 Web 两个客户端。

## 目录

```text
week7/
├── backend/    # Python 后端；Web 页面也由该后端提供
└── android/    # Android 工程，使用 Kotlin 和 Retrofit
```

- `backend/`：放置 Python API、身份验证、OpenAI 代理、Google Sheets 记录和语音处理代码；Web 页面使用该目录下的 `templates/` 与 `static/`。
- `android/`：放置 Android Studio 工程，调用同一个 Python 后端。
- OpenAI API key、JWT 签名密钥和 Google 服务账号私钥只在后端配置，不放入 Android 或 Web 客户端。

完整需求见 [项目需求文档](../PROJECT_REQUIREMENTS.md)。当前仅建立目录结构，功能代码待整合。
