# Neo9 AOD 息屏亮度调节（iQOO Neo9 LSPosed 模块）

一个用于 **iQOO Neo9（PD2338 / V2338A）** 的 LSPosed 模块：为息屏显示（AOD）增加**默认亮度调节 + 两组定时分段亮度**，全部在手机端设置，无需连电脑。

基于 [xiaoyuuo/IQOO-Neo9Spro-aod](https://github.com/xiaoyuuo/IQOO-Neo9Spro-aod) 修改，MIT 许可证（见 [LICENSE](LICENSE)）。

## 用途

vivo 原生 AOD 在夜间或深色壁纸上常常偏亮、刺眼。本模块通过在 AOD 图层上叠加**黑色半透明遮罩**来调暗息屏内容（不是改面板亮度/尼特，正常亮屏不受影响），并且可以按时间段自动切换亮度，例如：

- 白天（08:00–22:00）：60%
- 夜间（22:00–次日 08:00）：30%

## 功能

- **默认亮度**：20%–100% 滑块，无时段生效时使用
- **两组定时时段**：各自独立的开关、起止时间、亮度
  - 使用手机本地时间，支持跨午夜（如 22:00 → 次日 08:00）
  - 起点包含、终点不包含；起止时间不能相同
  - 两组重叠时段 2 优先；结束后自动回退到另一生效时段或默认亮度
- **手机端设置页**：桌面入口"息屏亮度"，保存后立即生效，关闭软件不停止定时
- **省电设计**：无新增常驻后台服务、无逐秒轮询，仅在时段边界登记系统闹钟（AlarmManager）
- **局部/全屏样式兼容**：局部原生样式与集成样式（如"搭配锁屏"）分别适配

## 安装与使用

### 前提条件

- 已 root（临时 root 即可）并安装 **LSPosed**
- 已安装 vivo「熄屏显示」（com.vivo.nightpearl）
- 适配固件：PD2338C_A_16.2.13.2.W10，熄屏显示 8.0.4.2（其他机型/固件不保证可用）

### 安装步骤

1. 从 [Releases](../../releases) 下载 `Neo9-AOD-typeguard.apk` 并安装
2. 在 LSPosed 中启用模块，作用域勾选：
   - 熄屏显示 `com.vivo.nightpearl`
   - 系统界面 `com.android.systemui`
   （无需勾选系统框架）
3. 给模块授予 `WRITE_SECURE_SETTINGS` 权限（可用 root 执行 `pm grant io.local.neoaod.typeguard android.permission.WRITE_SECURE_SETTINGS`）
4. 重载熄屏显示与系统界面进程（重启 LSPosed 作用域或重启手机）

### 手机端设置

打开桌面「**息屏亮度**」，或在 LSPosed 中打开模块设置：

1. 拖动「默认亮度」滑块选择无时段生效时的亮度（20%–100%）
2. 打开「时段 1」「时段 2」开关，设置起止时间和该时段亮度
3. 点击「**保存设置**」——未保存的修改不生效；保存后自动应用，无需连电脑

> 定时由注入的系统进程处理，关闭设置软件不会停止定时，无需常驻后台。

## 下载

前往 [Releases](../../releases) 页面下载最新 APK：

- `Neo9-AOD-typeguard.apk` —— 模块本体（含手机端设置页）

包名：`io.local.neoaod.typeguard`，当前版本：4.0-schedule（versionCode 29）。

## 构建

本仓库包含完整源码、资源、清单、构建脚本、Xposed API 依赖和测试：

```
src/com/dsh/neoaod/Main.java              LSPosed 入口、局部/全屏兼容处理
src/com/dsh/neoaod/Schedule.java          时间规则：跨午夜、重叠优先级、边界计算
src/com/dsh/neoaod/ConfigStore.java       Settings.Secure JSON 单次原子写入
src/com/dsh/neoaod/BrightnessRuntime.java AOD/SystemUI 进程内遮罩更新、闹钟登记
src/com/dsh/neoaod/SettingsActivity.java  手机端设置页
tests/ScheduleTest.java                   1454 项时间规则检查
build.ps1                                 构建脚本（需按本机环境调整路径）
```

构建环境：Android SDK build-tools 36.0.0、android-36.1 android.jar、Java 11 兼容字节码、Xposed API 82。签名密钥**不在仓库内**，需自行签名。

## 配置存储

配置写入 `Settings.Secure` 的 `neo9_aod_schedule_config`（版本 1 JSON），包含 `manual`、`first`、`second` 三组，每组 `enabled / start / end / percent`（时间为本地日内分钟数，亮度 20–100）。无新配置时回退到旧键 `neo9_aod_brightness_percent`，默认 60。

## 回退

[rollback/aod-rollback.sh](rollback/aod-rollback.sh) 可完整卸载模块并恢复原始六项系统配置（需当前 root 有效；只重载 AOD 与 SystemUI 进程，不整机重启）：

```bash
adb push rollback/aod-rollback.sh /data/local/tmp/aod-rollback.sh
adb shell su -c 'sh /data/local/tmp/aod-rollback.sh'
```

`rollback/` 内还保留修改前的原始配置备份 JSON。

## 验证

- 时间规则：1454 项自动化检查通过（跨午夜、边界、重叠优先级、回退、非法输入）
- 真机验证：手动亮度保存、时段自动切换（截图采样确认图层变化）、配置持久化
- 详细记录见 [docs/](docs/)

## 免责声明

- 仅在上述 Neo9 固件实测；系统升级或 LSPosed/Zygisk 失效后不保证可用
- 未修改面板亮度节点、显示驱动或任何 boot/system/vendor 分区
- 所有下载样式与长期耗电表现未逐一验证，请自行评估后使用
