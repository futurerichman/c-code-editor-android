# C语言草稿纸 (c-code-editor-android)

Android 上的轻量 C 语言编辑器与编译器，内置 TCC (Tiny C Compiler) 引擎，
无需 Root、无需 Termux，打开即可写代码、编译运行。

- 包名：`com.ccode.mintest`
- minSdk 26 / targetSdk 34
- 零运行时权限、零 WebView、零第三方库
- 非 Gradle 构建：aapt2 + javac + d8 + zipalign + apksigner

## 功能特性

- 代码编辑：VS Code 深色配色语法高亮、自动缩进、成对符号补全、行号栏、断点行号点击
- 文件操作：新建 / 打开（SAF）/ 保存（SAF，UTF-8 + GBK 自动识别）
- 编译运行：内置 TCC 引擎，一键编译运行，支持交互式 scanf
- 调试模式：行号栏点击设断点，单步执行，变量实时显示
- 字号调节：双指捏合或工具栏 +/-，10-30sp 持久化
- 自定义背景：支持图片 / 视频壁纸，可调透明度，全屏 / 编辑区两种模式
- 全面屏适配：刘海屏 shortEdges，屏幕旋转不丢状态

## 目录结构

```
src/AndroidManifest.xml
src/com/ccode/mintest/   # Java 源码
res/                      # 布局 / drawable / values
assets/                   # TCC 运行时（不随仓库分发，见下方）
build.sh                  # Linux/macOS 构建脚本
build.ps1                 # Windows PowerShell 构建脚本
build-local.sh            # Windows Git Bash 构建脚本
```

## 前置依赖：TCC 运行时二进制

仓库 **不包含** assets/ 下的二进制运行时文件（体积大、版权归属 TCC 项目）。
自行编译或下载后放到对应位置：

```
assets/
sysroot.zip               # Android aarch64 系统头文件
tcc/tcc.gz                # TCC aarch64 静态二进制（gzip 压缩）
tcc/tcc_runtime.zip       # libtcc1.a + crt + tcc 内置头文件
```

这些文件参考 TCC 官方 (https://bellard.org/tcc/) 与 Android NDK 头文件自行交叉编译。
缺少时 App 可安装打开，但运行按钮会报"缺少运行时"。

## 构建

### Windows (PowerShell)
```powershell
powershell -File build.ps1 -SrcDir . -OutApk app.apk -VersionName 1.28.25 -VersionCode 63
```

### Linux / Git Bash
```bash
chmod +x build.sh
bash build.sh app.apk
```

需要：JDK 17、Android SDK build-tools 34+、Android Platform android-34。

> 注意：构建脚本中的 keystore 路径和密码是开发者本机配置，开源使用者需自备签名密钥。

## License

[MIT](./LICENSE)，TCC 编译器版权归 Fabrice Bellard 所有。
