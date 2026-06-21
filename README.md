# 公安视图智能综合实战指挥平台

> 基于Spring Cloud微服务架构的"情指行"一体化实战体系，融合GIS地图、视频AI分析、大数据研判，实现警情智能接报派单、警力实时调度、重点人员/车辆布控、应急指挥协同等核心功能。

---

## 📋 目录

- [项目概述](#项目概述)
- [技术架构](#技术架构)
- [核心功能](#核心功能)
- [项目结构](#项目结构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [部署指南](#部署指南)
- [API文档](#api文档)
- [开发规范](#开发规范)
- [性能指标](#性能指标)

---

## 🎯 项目概述

公安视图智能综合实战指挥平台是面向公安实战的综合性指挥调度系统，构建"情指行"一体化实战体系，实现：

- **情报主导**：多源数据融合、AI智能研判
- **指挥精准**：智能派单、可视化调度、协同作战
- **行动高效**：移动警务、实时反馈、全程监督

平台采用微服务架构设计，支持高并发、高可用，满足7×24小时不间断运行要求。

---

## 🏗️ 技术架构

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      前端展示层                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │  指挥中心大屏     │  │  指挥调度台      │  │  移动警务端   │ │
│  │  (Ant Design Pro)│  │  (PC管理端)      │  │  (Kotlin MP)  │ │
│  └──────────────────┘  └──────────────────┘  └───────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API网关层                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Spring Cloud Gateway + Sentinel 限流熔断 + JWT认证      │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      微服务层                                    │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ 认证服务│ │ GIS服务 │ │ 警情服务│ │ 视频服务│ │WebSocket│   │
│  │  Auth   │ │  GIS    │ │  Alarm  │ │  Video  │ │  Push   │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据处理层                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │   Flink 流计算   │  │  Elasticsearch  │  │    Neo4j       │  │
│  │  (实时告警聚合)  │  │  (全文检索)     │  │  (关系图谱)    │  │
│  └─────────────────┘  └─────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据存储层                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐  │
│  │ MySQL 主从  │ │ Redis 集群  │ │  MinIO 对象 │ │RocketMQ  │  │
│  │ (业务数据)  │ │ (缓存/GEO)  │ │  (视频/图片) │ │ (消息队列)│  │
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      基础设施层                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐  │
│  │   Nacos     │ │   Seata     │ │ SkyWalking  │ │ Prometheus│ │
│  │ (服务治理)  │ │ (分布式事务)│ │ (链路追踪)  │ │ (监控)    │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 数据流图

```
视频流 ───▶ Flink ───▶ AI分析 ───▶ 告警 ───▶ RocketMQ ───▶ WebSocket ───▶ 大屏
                              │
                              └──▶ 人脸/车牌识别 ──▶ Elasticsearch ──▶ 以图搜图

110报警 ──▶ 语音转文字 ──▶ 地址解析 ──▶ Drools规则引擎 ──▶ 智能派单 ──▶ 移动端
                                                       │
                                                       └──▶ Seata分布式事务

GPS定位 ──▶ Redis GEO ──▶ 位置更新 ──▶ GIS地图展示
```

---

## 🚀 核心功能

### 1. 综合态势感知一张图

- **GIS地图展示**：基于腾讯地图API，实时展示警力分布、警情热力图、摄像头点位、重点区域
- **警力定位**：单兵GPS、巡逻车、无人机实时位置追踪，支持轨迹回放
- **图层控制**：支持治安/交通/重点区域等多图层切换
- **警情弹窗**：警情发生时自动定位并弹窗，显示报警内容、周边摄像头一键调取
- **实时推送**：WebSocket推送，延迟<3秒

### 2. 智能警情接报与派单

- **多渠道接警**：110电话、短信、APP、视频识别自动转工单
- **语音转文字**：自动语音识别，生成报警内容
- **地址解析**：自动地址标准化，经纬度解析
- **智能派单**：Drools规则引擎，基于警情类型、位置、警力负载自动推荐最优出警单位
- **调度协同**：支持一键催办、转单、联合出警
- **超时升级**：派单超时未响应自动升级至指挥长
- **分布式事务**：Seata保证工单状态与派单记录一致性

### 3. 视频智能化分析

- **多品牌接入**：支持海康/大华/华为等主流品牌摄像头
- **AI分析**：人脸识别(ArcFace)、车牌识别(YOLOv8)、行为分析
- **实时告警**：检测到异常立即推送指挥中心，支持语音播报
- **以图搜图**：基于Elasticsearch人脸特征向量检索，支持嫌疑人照片检索历史轨迹
- **视频存储**：告警视频前后各10秒自动存储至MinIO
- **布控管理**：重点人员/车辆布控，自动识别预警

### 4. 应急指挥协同

- **预案管理**：分级分类应急预案，一键启动
- **资源调度**：警力、车辆、无人机统一调度
- **视频会议**：WebRTC多方视频会议，指挥协同
- **无人机联动**：无人机实时视频回传，空中侦查
- **指令下达**：一键指令推送，任务追踪

### 5. 移动警务

- **跨端支持**：Kotlin Multiplatform覆盖鸿蒙/Android/iOS
- **任务接收**：派单实时推送，语音播报
- **导航路线**：高德/腾讯导航，最优路线规划
- **现场处置**：现场拍照、录像、笔录、取证上传
- **执法记录**：执法记录仪自动同步，全程留痕

### 6. 执法监督

- **执法规范**：执法过程标准化检查，AI自动识别不规范行为
- **质量考核**：执法质量自动评分，绩效考核
- **问题整改**：问题发现-整改-验证闭环管理
- **数据研判**：多维度执法数据分析

---

## 📁 项目结构

```
police-vision-command/
├── police-vision-common/          # 公共模块
│   ├── src/main/java/com/police/vision/common/
│   │   ├── result/                # 统一返回结果
│   │   ├── exception/             # 异常处理
│   │   ├── constant/              # 常量定义
│   │   ├── enums/                 # 枚举定义
│   │   ├── entity/                # 基础实体
│   │   ├── dto/                   # 数据传输对象
│   │   ├── util/                  # 工具类
│   │   └── config/                # 公共配置
│
├── police-vision-gateway/         # 网关服务 (端口: 8080)
│   ├── filter/                    # 过滤器(鉴权、限流、黑名单)
│   └── config/                    # 网关配置
│
├── police-vision-auth/            # 认证授权服务 (端口: 8081)
│   ├── entity/                    # 用户/角色实体
│   ├── mapper/                    # DAO层
│   ├── service/                   # 业务逻辑
│   └── controller/                # API接口
│
├── police-vision-gis/             # GIS地图服务 (端口: 8082)
│   ├── entity/                    # 位置/摄像头/车辆实体
│   ├── mapper/
│   ├── service/                   # 警力分布、热力图、图层管理
│   ├── mq/                        # GPS位置消费
│   └── controller/
│
├── police-vision-alarm/           # 警情派单服务 (端口: 8083)
│   ├── entity/                    # 工单/派单/日志实体
│   ├── mapper/
│   ├── service/                   # 警情管理、Drools派单引擎
│   ├── drools/                    # 规则引擎配置
│   ├── mq/                        # 超时消息消费
│   └── controller/
│
├── police-vision-video/           # 视频分析服务 (端口: 8084)
│   ├── entity/                    # 摄像头/人脸/车牌/告警实体
│   ├── mapper/
│   ├── service/                   # AI识别、告警、以图搜图
│   ├── mq/                        # 视频流消费
│   └── controller/
│
├── police-vision-websocket/       # WebSocket推送服务 (端口: 8085)
│   ├── handler/                   # WebSocket处理器
│   ├── interceptor/               # 认证拦截器
│   ├── mq/                        # 实时数据消费
│   └── controller/
│
├── police-vision-flink/           # Flink流处理服务
│   ├── job/                       # Flink作业(告警聚合/热力图/实时统计)
│   ├── entity/                    # Flink实体
│   ├── sink/                      # 输出(Redis/WebSocket)
│   ├── schema/                    # 序列化
│   └── util/
│
├── police-vision-web/             # 前端指挥中心大屏
│   ├── src/
│   │   ├── pages/Screen/          # 大屏主页面
│   │   ├── components/            # 组件(地图/图表/列表)
│   │   ├── services/              # API/WebSocket服务
│   │   ├── types/                 # TypeScript类型
│   │   └── utils/                 # 工具函数
│   └── package.json
│
├── sql/                           # 数据库脚本
│   └── init.sql                   # 初始化脚本
│
└── pom.xml                        # 父POM
```

---

## 🛠️ 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用开发框架 |
| Spring Cloud | 2023.0.1 | 微服务框架 |
| Spring Cloud Alibaba | 2023.0.1.0 | 微服务生态 |
| Nacos | 2.3.0 | 服务发现与配置中心 |
| Sentinel | 1.8.7 | 限流熔断 |
| Seata | 2.0.0 | 分布式事务 |
| Gateway | 4.1.4 | API网关 |
| MyBatis Plus | 3.5.5 | ORM框架 |
| MySQL | 8.0 | 关系数据库 |
| Redis | 7.0+ | 缓存、分布式锁、GEO |
| Elasticsearch | 8.x | 全文检索、向量检索 |
| Neo4j | 4.4 | 图数据库 |
| RocketMQ | 5.1+ | 消息队列 |
| Flink | 1.18.1 | 流处理引擎 |
| Drools | 8.44.0 | 规则引擎 |
| MinIO | 8.5+ | 对象存储 |
| JWT | 0.12.5 | 认证鉴权 |
| DeepSeek | - | 大语言模型 |
| ArcFace | 3.0 | 人脸识别 |
| YOLOv8 | - | 车牌/行为识别 |
| WebRTC | - | 视频会议 |
| SkyWalking | 9.0+ | 链路追踪 |
| Prometheus | 2.40+ | 监控告警 |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18.x | UI框架 |
| TypeScript | 5.x | 类型系统 |
| Ant Design | 5.x | UI组件库 |
| ProComponents | 2.x | 高级组件 |
| Vite | 5.x | 构建工具 |
| ECharts | 5.x | 图表库 |
| 腾讯地图API | GL版 | GIS地图 |
| WebSocket | - | 实时通信 |
| Axios | 1.x | HTTP客户端 |
| Day.js | 1.x | 日期处理 |

---

## ⚡ 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- Nacos 2.3.0+
- RocketMQ 5.1+
- MinIO RELEASE.2024-01-01T16-36-33Z+

### 1. 数据库初始化

```bash
# 创建数据库并执行初始化脚本
mysql -uroot -p < sql/init.sql
```

### 2. 启动基础设施

```bash
# 启动Nacos
cd nacos/bin
./startup.sh -m standalone

# 启动RocketMQ
cd rocketmq/bin
./mqnamesrv &
./mqbroker -n localhost:9876 &

# 启动Redis
redis-server redis.conf

# 启动MinIO
minio server /data --console-address ":9001"
```

### 3. 配置修改

修改各模块 `application-dev.yml` 中的数据库、Redis、Nacos等配置。

### 4. 后端编译启动

```bash
# 编译整个项目
mvn clean install -DskipTests

# 启动网关服务
cd police-vision-gateway
mvn spring-boot:run

# 启动认证服务
cd ../police-vision-auth
mvn spring-boot:run

# 启动GIS服务
cd ../police-vision-gis
mvn spring-boot:run

# 启动警情服务
cd ../police-vision-alarm
mvn spring-boot:run

# 启动视频服务
cd ../police-vision-video
mvn spring-boot:run

# 启动WebSocket服务
cd ../police-vision-websocket
mvn spring-boot:run

# 启动Flink作业
cd ../police-vision-flink
mvn spring-boot:run
```

### 5. 前端启动

```bash
cd police-vision-web

# 安装依赖
npm install

# 配置腾讯地图密钥
# 修改 index.html 第8行 YOUR_TENCENT_MAP_KEY_HERE

# 启动开发服务器
npm run dev
```

### 6. 访问系统

| 服务 | 地址 | 账号/密码 |
|------|------|-----------|
| 指挥中心大屏 | http://localhost:3000 | admin / 123456 |
| API文档 | http://localhost:8080/doc.html | - |
| Nacos控制台 | http://localhost:8848/nacos | nacos / nacos |
| MinIO控制台 | http://localhost:9001 | minioadmin / minioadmin |
| Sentinel控制台 | http://localhost:8858 | sentinel / sentinel |

---

## 📦 部署指南

### Docker部署示例

```dockerfile
# 以网关服务为例
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY police-vision-gateway/target/police-vision-gateway.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
```

### Kubernetes部署

参考 `k8s/` 目录下的Deployment和Service配置文件。

### 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| police-vision-gateway | 8080 | API网关 |
| police-vision-auth | 8081 | 认证服务 |
| police-vision-gis | 8082 | GIS服务 |
| police-vision-alarm | 8083 | 警情服务 |
| police-vision-video | 8084 | 视频服务 |
| police-vision-websocket | 8085 | WebSocket服务 |
| Nacos | 8848 | 服务注册配置 |
| Sentinel | 8858 | 限流熔断控制台 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| RocketMQ | 9876 | 消息队列NameServer |
| MinIO | 9000/9001 | 对象存储/控制台 |
| Elasticsearch | 9200 | 全文检索 |

---

## 📚 API文档

启动服务后访问：`http://localhost:8080/doc.html`

### 核心接口

| 模块 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 认证 | /api/auth/login | POST | 用户登录 |
| 认证 | /api/auth/logout | POST | 用户登出 |
| GIS | /api/gis/police/list | GET | 警力列表 |
| GIS | /api/gis/police/nearby | GET | 周边警力查询 |
| GIS | /api/gis/heatmap | GET | 警情热力图 |
| GIS | /api/gis/camera/list | GET | 摄像头列表 |
| 警情 | /api/alarm/create | POST | 创建警情 |
| 警情 | /api/alarm/dispatch | POST | 派单 |
| 警情 | /api/alarm/list | GET | 警情列表 |
| 警情 | /api/alarm/{id} | GET | 警情详情 |
| 视频 | /api/video/camera/{id} | GET | 摄像头详情 |
| 视频 | /api/video/alert/list | GET | 告警列表 |
| 视频 | /api/video/face/search | POST | 以图搜图 |
| WebSocket | /ws/screen | WS | 大屏实时推送 |

---

## 📏 开发规范

### 代码规范

1. **命名规范**
   - 类名：大驼峰 `UserService`
   - 方法名：小驼峰 `getUserById`
   - 常量：全大写下划线分隔 `MAX_COUNT`
   - 数据库表：下划线分隔 `sys_user`

2. **分层架构**
   - Controller：参数校验、调用Service、返回结果
   - Service：业务逻辑、事务控制
   - Mapper：数据访问
   - Entity：数据库实体
   - DTO：数据传输对象

3. **注释规范**
   - 类/方法必须有Javadoc注释
   - 复杂业务逻辑必须有行内注释
   - 接口必须有Swagger注解

4. **异常处理**
   - 业务异常抛出 `BusinessException`
   - 统一由 `GlobalExceptionHandler` 处理
   - 返回统一 `Result` 格式

### Git提交规范

```
<type>(<scope>): <subject>

type:
  feat: 新功能
  fix: 修复bug
  docs: 文档更新
  style: 代码格式
  refactor: 重构
  perf: 性能优化
  test: 测试相关
  chore: 构建/工具

示例:
feat(gis): 添加警力轨迹回放功能
fix(alarm): 修复派单超时逻辑错误
```

---

## 📊 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| API响应时间 | < 200ms | 95%接口 |
| WebSocket推送延迟 | < 3s | 从MQ到前端 |
| 并发用户数 | > 1000 | 同时在线 |
| TPS | > 5000 | 单节点 |
| 可用性 | > 99.95% | 年度 |
| 故障恢复时间 | < 5min | 服务自动恢复 |
| 数据一致性 | 100% | 关键业务 |
| AI识别准确率 | > 95% | 人脸识别 |
| 车牌识别准确率 | > 98% | - |

---

## 🔒 安全设计

1. **认证授权**
   - JWT无状态认证
   - 基于角色的权限控制(RBAC)
   - 接口级权限校验

2. **数据安全**
   - 敏感数据加密存储(AES)
   - 密码BCrypt加密
   - HTTPS传输加密
   - SQL注入防护(MyBatis Plus)
   - XSS攻击防护

3. **接口安全**
   - 请求签名校验
   - 重放攻击防护
   - Sentinel限流熔断
   - IP黑名单

4. **审计日志**
   - 所有操作记录日志
   - 关键操作全程留痕
   - 日志防篡改

---

## 📞 技术支持

如有问题，请联系技术支持团队。

---

## 📄 许可证

本项目为内部项目，未经授权不得外传。

---

## ⏰ 更新日志

### v1.0.0 (2024-01-01)
- ✅ 完成微服务基础架构搭建
- ✅ 实现GIS地图一张图功能
- ✅ 实现警情智能接报与派单
- ✅ 实现视频AI分析与告警
- ✅ 实现WebSocket大屏实时推送
- ✅ 实现Flink实时流处理
- ✅ 完成前端指挥中心大屏
- ✅ 完成数据库设计与初始化脚本
