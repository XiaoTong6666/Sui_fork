# Sui

用于 Android 的现代超级用户界面（SUI）实现。<del>名字 Sui 也来自于一个[角色](https://github.com/XiaoTong6666/Sui/issues/1)。</del>

## 简介

Sui 为 Root 应用提供了 Java API（即 [Shizuku API](https://github.com/RikkaApps/Shizuku-API)）。它主要提供两项能力：

1. 让 Root 应用能够直接调用 Android API（几乎等同于以 root 身份在 Java 层调用系统 API）。
2. 以 root 身份启动应用自身的、AIDL 风格的 Java 服务。

这会让 Root 应用的开发变得更加舒适。

另一个优势是：Sui 不会向 `PATH` 中添加二进制文件，也不会安装一个管理器应用。这意味着我们不需要花大量时间去对抗那些会检测这些东西的应用。

需要说明的是，“root”的完整实现远不止 `su` 本身。在它之前还有很多工作要做。Sui 不是一个完整的 Root 方案，它需要 Magisk 才能运行。

<details>
  <summary>为什么 “su” 对应用开发不友好</summary>

`su` 提供的是一个以 root 身份运行的 “shell”，它离 Android 的世界太远了。

为了说明这一点，我们先简要解释系统 API 的工作方式。比如我们可以用 `PackageManager#getInstalledApplications` 来获取应用列表。这个过程本质上是应用进程与 system server 进程之间的 IPC（跨进程通信），只是 Android Framework 帮我们封装好了内部细节。Android 使用 `Binder` 来完成这类 IPC。`Binder` 会让 server 端知道 client 的 uid 和 pid，从而 system server 可以据此检查 client 是否有权限执行相应操作。

回到 `su`。在 `su` 环境下，我们只能使用系统提供的命令。还是同一个例子，如果想用 `su` 获取应用列表，我们得执行 `pm list`。这非常痛苦：

1. **文本化输出**：这意味着你拿不到像 Java 里的 `PackageInfo` 那样的结构化数据，只能去解析文本。
2. **速度慢**：每执行一个命令至少要启动一个新进程，而 `PackageManager#getInstalledApplications` 本身也会在 `pm list` 内部被调用。
3. **能力有限**：命令能做的事情很少，只覆盖了 Android API 的一小部分。

虽然可以通过 `app_process` 以 root 身份调用 Java API（已有如 libsu、librootjava 等库），但在 app 进程和 root 进程之间传递 Binder 非常麻烦。尤其当你希望 root 进程作为常驻 daemon 时，一旦 app 进程重启，就没有廉价的方式重新获得 root 进程的 Binder。

事实上，对于 Magisk 或其他 Root 方案来说，要让 `su` 正常工作并不如很多人想象的那么简单（无论是 `su` 本身，还是 `su` 与管理器应用之间的通信，都有大量不愉快的幕后工作）。

</details>

## 用户指南

注意：现有只支持 `su` 的应用，其行为不会发生变化。

### 安装

你可以直接在 Magisk 中下载并安装 Sui。或者从 [release](https://github.com/XiaoTong6666/Sui/releases) 下载 zip 包，并在 Magisk 的 “从本地安装（Install from storage）” 中刷入。

### 管理界面（Management UI）

* 在桌面长按系统设置图标，会看到 Sui 的快捷方式
* 在 Sui 管理界面点击右上角菜单（三个点），点击 **“添加快捷方式到桌面”** 即可在桌面创建快捷方式
* 在默认拨号器中输入 `*#*#784784#*#*`
* 可以通过 KernelSU/Magisk 的 Action 按钮打开 Sui 管理界面

> **注意：** 对于部分系统，长按设置可能不会出现 Sui 快捷方式；为了避免打扰用户，此版本已 **移除** 进入 **“开发者选项”** 时自动询问添加快捷方式的功能

### 交互式 shell

Sui 提供交互式 shell。

由于 Sui 不会向 `PATH` 写入文件，所以需要手动复制所需文件。你可以参考 `/data/adb/sui/post-install.example.sh` 学习如何自动完成这一步。

文件正确复制后，就可以使用 `rish` 作为 `sh` 来启动交互式 shell。

## 应用开发指南

[https://github.com/RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API)

## 编译/构建（Build）

使用 `git clone --recurse-submodules` 克隆项目。

Gradle 任务：

`BuildType` 可为 `Debug` 或 `Release`。

* `:module:assemble<BuildType>`

  构建模块。完成后 Magisk 模块 zip 会生成在 `out` 目录。

* `:module:zip<BuildType>`

  生成 Magisk 模块 zip 至 `out`。

* `:module:push<BuildType>`

  通过 adb 推送 zip 至 `/data/local/tmp`。

* `:module:flash<BuildType>`

  用 `adb shell su -c magisk --install-module` 安装 zip。

* `:module:flashWithKsud<BuildType>`

  用 `adb shell su -c ksud module install` 安装 zip。

* `:module:flashAndReboot<BuildType>`

  安装 zip 并重启设备。

* `:module:flashWithKsudAndReboot<BuildType>`

  使用 ksud 安装并重启。

## 内部实现（Internals）

Sui 依赖 [Zygisk](https://github.com/topjohnwu/zygisk-module-sample)，它允许我们注入 system server 进程和应用进程。

整体上有四个部分：

* **Root 进程（Root process）**

  这是一个由 Magisk 启动的 root 进程。它会启动一个 Java server，实现 Shizuku API 以及其他部分所需的私有 API。

* **SystemServer 注入（SystemServer inject）**

  * Hook `Binder#execTransact`，最终让我们可以处理一个未使用的 Binder 调用
  * 在该 Binder 调用中实现 “get binder” / “set binder” 逻辑，使 root 进程能把自己的 Binder 发送给 system server，而应用可以从 system server 获取 root 进程的 Binder

* **SystemUI 注入（SystemUI inject）**

  * 从 root server 获取我们 apk 的 fd，并基于它创建 `Resource` 实例
  * 当收到 root server 的回调时，使用我们的 `Resource` 和 `ClassLoader` 弹出确认窗口

* **Settings 注入（Settings inject）**

  * 从 root server 获取我们 apk 的 fd，并基于它创建 `Resource` 实例
  * 发布一个快捷方式，指向现有的 `Activity`，但带有特殊 intent extra
  * 替换 `ActivityThread#mInstrumentation` 来介入 `Activity` 的实例化流程：当 intent 含有该特殊 extra 时，改为创建我们自己的 `Activity`，并使用我们的 `Resource` 与 `ClassLoader`
