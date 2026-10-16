# Plugin API

> 文档版本：v1.1 · 随宿主版本更新维护。
> 本文件是 API 的**唯一权威来源**：方法名、参数顺序、字段名一律以此为准。
> 拿不准就去查，不要用名字相近的凑 —— 字段名写错的实际代价见「核心数据结构 → MsgData」。

## 简介

脚本引擎基于 `BeanShell` ，支持java8语法，不支持注解 
脚本目录：位于数据目录下的 `plugin` 文件夹中。

---

## 开发环境说明

本脚本运行于 **Modern BeanShell** 环境（支持 Java 8+）。编写时请遵循以下规范：

- **属性访问**：推荐使用成员符 `.` 直接访问对象属性（如 `msgData.msg`），无需调用 Getter 方法或者复杂的反射。

- **方法访问**：无需强制类型转换即可自动调用对应签名的方法和字段。

- **全局作用域**：`context`、`classLoader` 及所有 API 方法在脚本任意位置（含内部类、Lambda）均可直接调用，**无需传递**。

- **语法支持**：完整支持 Java 8 特性，包括 Lambda 表达式 (`->`)、流式 API (`Stream`)，此外，支持 Kotlin 空安全操作符 `?.`（安全调用）与 `?:`（Elvis 操作符）以简化空值处理。

- **线程模型**：事件回调默认在 **IO 线程** 执行。操作 UI 需切换至主线程，内置 `toast` 已自动处理线程切换。

- **宿主交互**：脚本持有宿主和模块的 `ClassLoader`，可直接 `import` 类并使用。

---

## Lambda 表达式支持

理论支持标准的 Java Lambda 语法，可用于简化代码或实现各类函数式接口（如 `Runnable`, `Comparator`, `Consumer` 等，以及脚本中自定义的符合单抽象方法的接口）。

### 1. 基础语法
支持 `->` 表达式，可用于单行语句或代码块。

```java
// 示例 1: 启动线程 (Runnable)
new Thread(() -> {
    // 处理逻辑...
    log("线程执行中");
}).start();

// 示例 2: 列表排序 (Comparator)
Collections.sort(list, (a, b) -> a.length() - b.length());

// 示例 3: 结合 Stream 使用
list.stream().filter(s -> s.startsWith("A")).count();
```

### 2. 方法引用 (::)
支持通过双冒号 `::` 引用现有的 Java 方法：

*   **静态方法**：如 `Math::max`
*   **实例方法**：如 `System.out::println`
*   **类名引用实例方法**：如 `String::toUpperCase`
*   **构造函数**：如 `ArrayList::new`

### 3. 引用脚本方法 (this::)
可以使用 `this` 关键字引用当前脚本中定义的方法（包括自定义方法和 API 内置方法），检测比较宽泛，参数数量与目标接口函数匹配即可，尝试调用失败时报错。

```java
// 定义一个脚本方法
public void handleItem(Object item) {
    log("处理: " + item);
}

// 在需要函数式接口的地方引用它
list.forEach(this::handleItem);

// 也可以直接引用 API 提供的内置方法
list.forEach(this::log);
```

---

## 脚本必须文件

- **main.java**：脚本执行入口
- **desc.txt**：脚本描述文件（可选）
- **info.prop**：配置文件  
  包含如下配置项：
  - `id` → 脚本唯一标识符
  - `pluginName` → 脚本名称
  - `author` → 作者
  - `versionCode` → 版本号（默认写 `1.0`）

以上三个文件缺一不可。脚本逻辑复杂时可以自行分层、拆出内部类，但无论多复杂，
这三个文件都必须完整给出，生成的三个文件段一个都不能少。

## 全局变量

| 变量名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `context` | `android.content.Context` | 宿主 App 全局上下文 |
| `pluginId` | `String` | 当前加载脚本 ID (**注意大小写**) |
| `classLoader` | `ClassLoader` | QQ 宿主类加载器 (HostClassLoader) |
| `pluginPath` | `String` | 当前加载脚本的文件夹绝对路径（注意无/） |
| `myUin` | `String` | 当前登录的 QQ 号 |

---

## 核心数据结构 (Java Beans)

### 1. MsgData (消息对象)
用于 `onMsg` 回调中。

> **字段名易错点（务必先读）**
>
> 1. 聊天类型字段是 `msgData.type`，**不是 `msgData.chatType`**。
>    本文档和方法签名里到处出现的「聊天类型」指的就是这个 `type`；
>    `chatType` 只是回调方法的形参名，不能用在 MsgData 上。
> 2. 写错字段名时 **BeanShell 不会报「字段不存在」**。它会返回一个空值，
>    直到对该值做强制类型转换才抛错，报错形如：
>    `bsh.TargetError: Cannot cast void value to Integer ... msgData.chatType`。
>    看到这类报错，**先怀疑属性名写错**，而不是去改类型。
> 3. 属性名一律小驼峰，且与下文逐字一致；拿不准就查本文档，不要凭直觉拼。

- `int type`: 聊天类型 (1:好友/私聊, 2:群聊, 100:陌生人)
- `int msgType`: 消息类型 —— **取值表待补**，见文末「文档维护待办」。
  补齐之前不要猜取值；需要按消息类型过滤时，先向维护者确认。
- `String peerUin`: 群号或好友QQ号
- `String peerUid`: 群号或好友UID
- `String userUin`: 发送者QQ号
- `String userUid`: 发送者UID
- `long time`: 发送时间戳 (秒)
- `long msgId`: 消息ID
- `String msg`: 文本消息内容 (包含 `[pic=url]` 等格式)
- `String path`: 文件/视频/语音保存路径
- `List<String> atList`: 消息中艾特的QQ号列表
- `Map<String, String> atMap`: 艾特映射表 (Key: Uin, Value: 艾特内容)
- `Object data`: 原始 `MsgRecord` 对象
- `Object contact`: 原始 `Contact` 对象

### 2. FriendInfo (好友信息)
- `String uin`: QQ号
- `String uid`: UID
- `String name`: 昵称
- `String remark`: 备注

### 3. GroupInfo (群信息)
- `String group`: 群号
- `String groupName`: 群名称
- `String groupOwner`: 群主QQ号
- `Object groupInfo`: 原始 `TroopInfo` 对象

### 4. MemberInfo (群成员信息)
- `String uin`: 成员QQ号
- `String uinName`: 群名片/昵称
- `int uinLevel`: 群等级
- `long joinGroupTime`: 入群时间戳
- `long lastActiveTime`: 最后发言时间戳
- `String role`: 角色 (OWNER:群主, ADMIN:管理员, MEMBER:成员)
- `Object memberInfo`: 原始 `TroopMemberInfo` 对象

### 5. ForbidInfo (禁言信息)
- `String user`: 被禁言成员QQ号
- `String userName`: 被禁言成员昵称
- `long time`: 剩余禁言时长 (秒)
- `long endTime`: 禁言结束时间戳

---

## 调用约定与失败语义

- **发送类方法**（`sendMsg` / `sendPic` / `sendPtt` / `sendCard` / `sendFile` / `sendVideo` /
  `sendReplyMsg` / `sendPai`）：⚠️ 失败时的行为（是否抛异常、是否返回布尔）**文档尚未定义**，
  见文末「文档维护待办」。写脚本时请自行 `try/catch` 包一层并 `log()` 记下原因，
  不要把「调用成功」当成「消息已送达」。
- **群管理类方法**（`shutUp` / `kickGroup` / `setGroupAdmin` …）：多为网络请求，
  可直接在 IO 线程调用，同样建议 `try/catch`。
- **存储类方法**（`put*` / `get*`）：同步读写 JSON。⚠️ 是否对并发调用加锁**文档未明确**；
  若可能同时从 IO 线程和主线程写同一个配置名，请在脚本侧自行串行化。

## 核心方法分类

### 一、消息相关方法

#### 1. sendMsg：发送消息
- **方法重载 1**：`sendMsg(String PeerUin, String 内容, int 聊天类型)`
  - 聊天类型：1好友 / 2群聊 / 100陌生人
  - 支持格式：
    - 艾特：`[atUin=QQ号]`（QQ号为0时表示艾特全体）
    - 图片：`[pic=图片链接或绝对路径]`
    - 可任意组合，将根据形式自动解析
- **方法重载 2**：`sendMsg(Object Contact对象, String 内容)`
  - ⚠️ 这个重载收的是 **Contact 对象，不是 MsgData**。手上只有 `msgData` 时，
    要么传 `msgData.contact`，要么改用重载 1：`sendMsg(msgData.peerUin, 内容, msgData.type)`。
    直接把 `msgData` 传进来会在运行时报错。

#### 2. sendPic：发送图片
- **方法重载 1**：`sendPic(String PeerUin, String 图片路径, int 聊天类型)`
- **方法重载 2**：`sendPic(Object Contact对象, String 图片路径)`

#### 3. sendPtt：发送语音
- **方法重载 1**：`sendPtt(String PeerUin, String 语音路径, int 聊天类型)`
- **方法重载 2**：`sendPtt(Object Contact对象, String 语音路径)`
- **方法重载 3**：`sendPtt(String PeerUin, String 语音路径, int 聊天类型, int durationMs)`
- **方法重载 4**：`sendPtt(Object Contact对象, String 语音路径, int durationMs)`

> `durationMs` 为毫秒；不传时从 silk 估算，失败回退 `1000ms`。

#### 4. sendCard：发送 JSON 卡片
- **方法重载 1**：`sendCard(String PeerUin, String JSON字符串, int 聊天类型)`
- **方法重载 2**：`sendCard(Object Contact对象, String JSON字符串)`

#### 5. sendFile：发送文件
- **方法重载 1**：`sendFile(String PeerUin, String 文件路径, int 聊天类型)`
- **方法重载 2**：`sendFile(Object Contact对象, String 文件路径)`

#### 6. sendVideo：发送视频
- **方法重载 1**：`sendVideo(String PeerUin, String 视频路径, int 聊天类型)`
- **方法重载 2**：`sendVideo(Object Contact对象, String 视频路径)`

#### 7. sendReplyMsg：发送引用回复
- **方法重载 1**：`sendReplyMsg(String PeerUin, long 引用消息ID, String 内容, int 聊天类型)`
- **方法重载 2**：`sendReplyMsg(Object Contact对象, long 引用消息ID, String 内容)`

#### 8. recallMsg：撤回消息
- **方法重载 1**：`recallMsg(int 聊天类型, String PeerUin, long 消息ID)`
- **方法重载 2**：`recallMsg(Object Contact对象, long 消息ID)`

#### 9. sendPai：拍一拍
- `sendPai(String 被拍者Uin, String PeerUin, int 聊天类型)`
  - `PeerUin`：在群里则是群号，私聊则是好友QQ

---

### 二、好友相关方法

#### 1. getAllFriend：获取好友列表
- `List<FriendInfo> getAllFriend()`
  - 返回 `FriendInfo` 对象列表 (见数据结构章节)。

#### 2. isFriend：判断好友
- `boolean isFriend(String uin)`

#### 3. sendZan：点赞
- `sendZan(String uin, int count)`

#### 4. Uin 与 Uid 转换
- `String getUidFromUin(String uin)`
- `String getUinFromUid(String uid)`

---

### 三、群管理与信息获取

#### 1. getGroupList：获取群列表
- `List<GroupInfo> getGroupList()`
  - 返回 `GroupInfo` 对象列表。

#### 2. getGroupMemberList：获取群成员列表
- `List<MemberInfo> getGroupMemberList(String 群号)`
  - 返回 `MemberInfo` 对象列表。

#### 3. getProhibitList：获取禁言列表
- `List<ForbidInfo> getProhibitList(String 群号)`
  - 返回 `ForbidInfo` 对象列表。

#### 4. getGroupInfo：获取单个群信息
- `TroopInfo getGroupInfo(String 群号)`
  - 返回原始 `TroopInfo` 对象。

#### 5. getMemberInfo：获取单个成员信息
- `MemberInfo getMemberInfo(String 群号, String 成员QQ)`

#### 6. shutUp：禁言成员
- `shutUp(String 群号, String 成员QQ, long 秒数)`
  - 传 0 为解禁。

#### 7. shutUpAll：全员禁言
- `shutUpAll(String 群号, boolean 是否开启)`

#### 8. kickGroup：踢出群成员
- `kickGroup(String 群号, String 成员QQ, boolean 是否拉黑)`
  - `是否拉黑`：不再接收此人申请。

#### 9. setGroupAdmin：设置管理员
- `setGroupAdmin(String 群号, String 成员QQ, boolean 是否设为管理)`

#### 10. setGroupMemberTitle：设置头衔 (仅群主)
- `setGroupMemberTitle(String 群号, String 成员QQ, String 头衔)`

#### 11. changeMemberName：修改群名片
- `changeMemberName(String 群号, String 成员QQ, String 新名片)`

#### 12. isShutUp：判断群是否全员禁言
- `boolean isShutUp(String 群号)`

#### 13. clockIn：群打卡
- `clockIn(String 群号)`

---

### 四、Cookie & Token 方法

- `String getSkey()`
- `String getRealSkey()`
- `String getPskey(String 域名)`
- `String getPt4Token(String 域名)`
- `String getStweb()`
- `String getGTK(String 域名)`
- `String getGroupRKey()`：群聊图片 RKey
- `String getFriendRKey()`：私聊图片 RKey
- `long getBkn(String key)`

---

### 五、数据存储方法

数据保存在 `plugin/脚本ID/config/` 下的 JSON 文件中。

`配置名` 就是文件名（不含 `.json`），同一个文件里放若干键值对。例如：

```java
putBoolean("cfg", "autoReply", true);   // → config/cfg.json 里 {"autoReply": true}
boolean on = getBoolean("cfg", "autoReply", false);
```

一个脚本尽量只用一两个配置名。**换过键名之后要把旧键清掉**，
否则 `config/xxx.json` 里会留下永远读不到的孤儿键，后人排查时会被误导。

#### 1. 写入数据
- `putString(String 配置名, String 键, String 值)`
- `putInt(String 配置名, String 键, int 值)`
- `putLong(String 配置名, String 键, long 值)`
- `putBoolean(String 配置名, String 键, boolean 值)`

#### 2. 读取数据
- `String getString(String 配置名, String 键, String 默认值)`
- `int getInt(String 配置名, String 键, int 默认值)`
- `long getLong(String 配置名, String 键, long 默认值)`
- `boolean getBoolean(String 配置名, String 键, boolean 默认值)`

---

### 六、回调方法 (main.java)

在脚本中实现以下方法以接收事件：

#### 1. onMsg：接收消息
- `void onMsg(Object msgData)`
  - 参数为 `MsgData` 对象，字段见「核心数据结构 → MsgData」。
  - 聊天类型在 `msgData.type`（1=好友/私聊，2=群聊，100=陌生人），**不是 `msgData.chatType`**。
  - ⚠️ **脚本自己发出的消息是否也会回调进来，文档暂未明确**（见文末「文档维护待办」）。
    做关键词自动回复这类脚本时，请自行加一道防重入保护，避免自己回自己形成死循环。

#### 2. 群变动事件
- `void joinGroup(String 群号, String 成员QQ)`：成员入群
- `void quitGroup(String 群号, String 成员QQ)`：成员退群
- `void shutUpGroup(String 群号, String 成员QQ, long 时间, String 操作者QQ)`：群禁言事件

#### 3. 交互事件
- `void chatInterface(int 聊天类型, String PeerUin, String 名称)`：进入聊天界面
- `void onPaiYiPai(String PeerUin, int 聊天类型, String 操作者QQ)`：拍一拍事件

#### 4. 发送预处理
- `String getMsg(String 原始内容)`
  - 发送文本消息前触发，返回修改后的文本内容。

#### 5. 生命周期
- `void unLoadPlugin()`：脚本停止/卸载时触发。

---

### 七、菜单功能

#### 1. 脚本菜单 (悬浮窗)
- **添加**：`addItem(String 菜单名, String 回调方法名)`
- **回调定义**：
  ```java
  // 3参数版本
  void 回调方法名(int 聊天类型, String PeerUin, String 名称) { ... }
  
  // 4参数版本 (包含 Contact 对象)
  void 回调方法名(int 聊天类型, String PeerUin, String 名称, Object contact) { ... }
  ```
#### 2. 消息菜单 (长按消息)
- **添加**：`addMenuItem(String 菜单名, String 回调方法名, int[] 消息类型数组)`
  - 最后一个参数为空或不写则默认所有消息
  - ⚠️ 「消息类型」的取值表**待补**（见文末「文档维护待办」），与 `MsgData.msgType` 同一套取值。
- **回调定义**：
  ```java
  void 回调方法名(Object msgData) { 
      //msgData为MsgData对象
  }
  ```

### 八、其他辅助方法

#### 1. 日志与提示
- `log(String 内容)`：追加写入脚本目录下的 `log.txt`
- `log(String 文件名, String 内容)`：追加写入指定文件，文件名用脚本目录内的相对名
  - ⚠️ 本框架**没有「默认参数」这回事**，上面是两个独立重载，
    不要写成 `log()` 省略参数，也不要以为第一个参数可省。
- `toast(Object 内容)`：系统 Toast
- `qqToast(int 图标类型, Object 内容)`：QQ 风格顶部弹窗
  - 图标：0=警告, 1=错误/失败, 2=成功

#### 2. 动态加载
- `loadJava(String java文件路径)`
- `loadJar(String jar文件路径)`
- `loadDex(String dex文件路径)`
- `registerActivity(Class<? extends Activity> 类)`
  - 注册后可使用startActivity启动
  
> jar，dex加载后可直接使用import导入

#### 3. 界面
- `Activity getNowActivity()`：获取当前顶层 Activity (可能为 null)

---

## 文档维护待办

下面这些内容目前缺失，脚本作者只能靠猜（而猜错的成本很高）。请维护者逐条补齐后删掉本段：

1. `MsgData.msgType` 的取值表（同时在 `addMenuItem` 的消息类型数组处引用）。
2. `addMenuItem(String, String, int[] 消息类型数组)` 里消息类型的取值表。
3. 各发送类 / 群管理类方法的**失败语义**：失败时抛异常还是静默？有没有返回值？
4. 存储 API 的**并发语义**：`put*` / `get*` 是否加锁、是否原子落盘？
5. `loadJava(String)` 的语义：是否同步执行？模块里定义的方法能否被 `main.java` 直接调用？
   脚本分层依赖「后定义覆盖前定义」这一行为，必须先确认它成立。
6. `onMsg` **是否会把脚本自己发出的消息回调进来**（直接决定关键词回复会不会自触发死循环）。
7. `addItem` 回调在**非聊天界面**被触发时，第一个参数（聊天类型）取什么值？
   写成 `if (chatType != 2) return;` 的菜单回调，在别处点开时会静默失效。
8. 脚本文件改动后**是否需要手动重载**，有无热重载机制。
9. `pluginPath` 的取值口径：文档写「注意无/」，容易被读反。建议改成明确表述
   「值**不含**结尾斜杠，拼接时自己补 `/`」，并统一本节示例写法。