# Neo9 AOD 4.0 源码

包名：io.local.neoaod.typeguard；版本：4.0-schedule（29）。

基于 https://github.com/xiaoyuuo/IQOO-Neo9Spro-aod 修改，许可证见 LICENSE。

## 文件

- src/com/dsh/neoaod/Main.java：LSPosed 入口、局部/全屏兼容处理。目录沿用旧项目名，Java 包名为 io.local.neoaod.typeguard。
- Schedule.java：每日时间规则、跨午夜处理、重叠优先级、下一边界计算。
- ConfigStore.java：单次 Settings.Secure JSON 写入，避免两组规则只保存一部分。
- BrightnessRuntime.java：在已勾选的 AOD/SystemUI 进程内更新遮罩，监听设置和时间变化，按时段边界注册系统闹钟。
- SettingsActivity.java：默认亮度滑块、两组开关/时间选择器/亮度滑块、保存与错误提示。
- tests/ScheduleTest.java：1454 项时间规则检查。

## 构建

本机完整环境位于 work/aod-build，运行其中 build.ps1；使用 Android SDK build-tools 36.0.0、android-36.1/android.jar、Java 11 兼容字节码、Xposed API 82。

压缩包保留源码、资源、清单、构建脚本、Xposed API 依赖和测试源码，不包含 Android SDK 或签名密钥。原签名密钥保留在本机 work/aod-build/aod-test.p12，后续更新应继续使用同一密钥。本机脚本含本机 SDK 和输出目录路径，迁移机器时需调整。

## 安装与生效

本次已完成原包覆盖安装、WRITE_SECURE_SETTINGS 授权，以及 AOD/SystemUI 进程重载。无需再操作。作用域保留 com.vivo.nightpearl 与 com.android.systemui。未添加系统框架作用域，也未整机重启。

首次安装到其他环境时需要单独完成模块启用与设置写入授权；此模块仅实测于当前 Neo9 固件，不保证跨机型适配。

## 配置

secure/neo9_aod_schedule_config 为版本 1 JSON，含 manual、first、second。每组含 enabled、start、end、percent；时间为本地日内分钟数，亮度范围 20–100。

起点包含、终点不包含；跨午夜支持；重叠时 second 优先；启用时段不得起止相同。未命中时使用 manual。关闭设置 Activity 不会停止时段规则。

无新配置时回退到原 secure/neo9_aod_brightness_percent，默认 60。没有修改面板硬件亮度或正常亮屏亮度。使用图层遮罩调暗，因此百分比不是尼特或功耗比例。
