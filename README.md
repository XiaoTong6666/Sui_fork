# Sui
[🇨🇳中文README](https://github.com/XiaoTong6666/Sui/blob/main/README.zh-CN.md)    

Modern super user interface (SUI) implementation on Android. <del>The name, Sui, also comes from [a character](https://github.com/XiaoTong6666/Sui/issues/1).</del>

## Introduction

Sui provides Java APIs, [Shizuku API](https://github.com/RikkaApps/Shizuku-API), for root apps. It mainly provides the ability to use Android APIs directly (almost in Java as the identity of the root, and start app's own AIDL-style Java service under root. This will make root app development much more comfortable.

Another advantage is that Sui does not add binaries to `PATH` and does not install a manager app. This means we no longer need to spend a huge amount of time to fight with apps that detect them.

To be clear, the full implementation of "root" is far more than "su" itself, there is a lot of hard work to be done before. Sui is not a full root solution, it requires Magisk to run.

<details>
  <summary>Why "su" is unfriendly for app development</summary>

The "su", a "shell" runs as root, is too far from the Android world.

To explain this, we need to talk about how system API works. For example, we can use `PackageManager#getInstalledApplications` to get the app list. This is actually an interprocess communication (IPC) process of the app process and system server process, just the Android framework did the inner works for us. Android uses `Binder` to do this type of IPC. `Binder` allows the server-side to learn the uid and pid of the client-side so that the system server can check if the app has the permission to do the operation.

Back to "su", there are commands provided by the Android system. In the same example, to get the app list with "su", we have to use `pm list`. This is too painful.

1. Text-based, this means there is no structured data like `PackageInfo` in Java. You have to parse the output text.
2. It is much slower because run a command means at least one new process is started. And `PackageManager#getInstalledApplications` is used inside `pm list`.
3. The possibility is limited to how the command can do. The command only covers a little amount of Android APIs.

Although it is possible to use Java APIs as root with `app_process` (there are libraries like libsu and librootjava), transfer binder between app process and root process is painful. If you want the root process to run as a daemon. When the app process restarts, it has no cheap way to get the binder of the root process.

In fact, for Magisk and other root solutions, makes the "su" to work is not that easy as some people think (let "su" itself work and the communication between the "su" and the manager app have a lot of unhappy work behind).

</details>

## User guide

Note, the behavior of existing apps that only supports "su" will NOT change.

### Install

You can download and install Sui from Magisk directly. Or, download the zip from [release](https://github.com/XiaoTong6666/Sui/releases) and use "Install from storage" in Magisk.

### Management UI

- Long press the **System Settings** icon on the home screen to see the Sui shortcut
- In the Sui management interface, tap the menu button (three dots) in the top-right corner and select **"Add shortcut to home screen"**
- Enter `*#*#784784#*#*` in the default dialer app
- Open the Sui management interface via the **Action** button in the KernelSU/Magisk manager

> **Note:** On some systems, the Sui shortcut may not appear when long-pressing Settings. Additionally, to avoid disturbance, the feature that automatically prompts to add a shortcut when entering "Developer options" has been **removed** in this version.

### Interactive shell

Sui provides interactive shell.

Since Sui does not add files to `PATH`, the files need to be copied manually. See `/data/adb/sui/post-install.example.sh` to learn how to do this automatically.

After the files are correctly copied, use `rish` as 'sh'.

## Application development guide

https://github.com/RikkaApps/Shizuku-API

## Build

Clone with `git clone --recurse-submodules`.

Gradle tasks:

`BuildType` could be `Debug` and `Release`.

* `:module:assemble<BuildType>`

  Build the module. After assemble finishes, Magisk module zip will be generated to `out`.

* `:module:zip<BuildType>`

  Generate Magisk module zip to `out`.

* `:module:push<BuildType>`

  Push the zip with adb to `/data/local/tmp`.

* `:module:flash<BuildType>`

  Install the zip with `adb shell su -c magisk --install-module`.

* `:module:flashWithKsud<BuildType>`

  Install the zip with `adb shell su -c ksud module install`.

* `:module:flashAndReboot<BuildType>`

  Install the zip and reboot the device.

* `:module:flashWithKsudAndReboot<BuildType>`

  Install the zip with ksud and reboot the device.


## Internals

Sui requires [Zygisk](https://github.com/topjohnwu/zygisk-module-sample). Zygisk allows us to inject into system server process and app processes.

In short, there are four parts:

- Root process

  This is a root process started by Magisk. This process starts a Java server that implements Shizuku API and private APIs used by other parts.

- SystemServer inject

  - Hooks `Binder#execTransact` and finally allow us to handle an unused binder call
  - Implements "get binder", "set binder" logic in that binder call, so that root process can send its binder to the system server, and the apps can acquire root process's binder

- SystemUI inject

  - Acquire the fd of our apk from the root server, create a `Resource` instance from it
  - Show confirmation window with our `Resource` and `ClassLoader` when recevied callback from the root server

- Settings inject

  - Acquire the fd of our apk from the root server, create a `Resource` instance from it
  - Publish shortcut which targets an existing `Acitivty` but with a special intent extra
  - Replace `ActivityThread#mInstrumentation` to intervene the `Acitivty` instantiate process, if the intent has the speical extra, create our `Activity` which uses our `Resource` and `ClassLoader`
