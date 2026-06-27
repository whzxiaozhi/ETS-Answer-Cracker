# ETS Answer Cracker

安卓版 E 听说答案提取器，自动扫描 E 听说 App 的 `content.json` 文件并解析显示答案。

## 功能

- 📖 自动扫描 E 听说 App 数据目录下的 `content.json`
- 📝 支持三种题型解析：回答问题、选择题、话题简述
- 🔄 支持 root（su）和 Shizuku（ADB）两种权限方案

## 权限方案

| 方案 | 需要 | 说明 |
|------|------|------|
| **Root (su)** | Magisk/KernelSU | 直接用 `su -c find/cat` 读取 |
| **Shizuku** | Shizuku App + ADB | 通过 UserService 执行命令 |
| **File API** | 无 | Android < 11 或部分设备可用 |

## 扫描路径

```
/storage/emulated/0/Android/data/com.ets100.secondary/files/
```

自动递归查找子目录中的 `content.json` 文件。

## JSON 结构支持

- `collector.read` → 阅读材料（info.value）
- `collector.choose` → 选择题（info.xtlist[].answer）
- `collector.role` → 角色扮演（info.question[].std[0] + std[-2]）
- `collector.picture` → 话题讨论（info.std[]）

## 构建

1. 用 Android Studio 打开项目
2. Sync Gradle
3. Build → Make Project

## 依赖

- AndroidX AppCompat
- AndroidX RecyclerView
- Material Design
- Shizuku API (dev.rikka.shizuku:api:13.1.5)

## License

MIT
