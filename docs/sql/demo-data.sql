-- DataRobort 演示业务库初始化脚本
-- 用法：在 MySQL 8 中执行，创建 demo_business 库并灌入样例数据

CREATE DATABASE IF NOT EXISTS demo_business DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE demo_business;

DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '客户ID',
    name          VARCHAR(64)  NOT NULL COMMENT '客户姓名',
    city          VARCHAR(32)  DEFAULT NULL COMMENT '所在城市',
    level         VARCHAR(16)  DEFAULT NULL COMMENT '客户等级',
    registered_at DATETIME     DEFAULT NULL COMMENT '注册时间',
    PRIMARY KEY (id),
    KEY idx_city (city),
    KEY idx_level (level)
) ENGINE = InnoDB COMMENT='客户信息表';

CREATE TABLE orders (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no     VARCHAR(32)  NOT NULL COMMENT '订单号',
    customer_id  BIGINT       NOT NULL COMMENT '客户ID',
    customer_name VARCHAR(64) DEFAULT NULL COMMENT '客户名称（快照）',
    category     VARCHAR(32)  DEFAULT NULL COMMENT '商品类目',
    amount       DECIMAL(12,2) DEFAULT NULL COMMENT '订单金额',
    status       VARCHAR(16)  DEFAULT NULL COMMENT '订单状态',
    created_at   DATETIME     DEFAULT NULL COMMENT '下单时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_customer (customer_id),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB COMMENT='销售订单表';

-- 客户样例数据
INSERT INTO customers (id, name, city, level, registered_at) VALUES
(1, '张三', '北京', 'VIP', '2023-01-10 10:00:00'),
(2, '李四', '上海', '普通', '2023-03-15 14:30:00'),
(3, '王五', '深圳', 'VIP', '2023-06-20 09:15:00'),
(4, '赵六', '北京', '普通', '2023-08-05 16:45:00'),
(5, '孙七', '上海', 'VIP', '2023-11-12 11:20:00');

-- 订单样例数据（覆盖多类目、多状态、多金额，便于分析验证）
INSERT INTO orders (id, order_no, customer_id, customer_name, category, amount, status, created_at) VALUES
(1, 'O20240101001', 1, '张三', '电子产品', 12999.00, '已完成', '2024-01-01 09:12:00'),
(2, 'O20240102002', 2, '李四', '家居', 3599.50, '已完成', '2024-01-02 15:23:00'),
(3, 'O20240103003', 1, '张三', '服装', 899.00, '已发货', '2024-01-03 11:08:00'),
(4, 'O20240105004', 3, '王五', '电子产品', 5699.00, '已完成', '2024-01-05 18:45:00'),
(5, 'O20240108005', 4, '赵六', '食品', 128.50, '已取消', '2024-01-08 08:56:00'),
(6, 'O20240110006', 5, '孙七', '家居', 7299.00, '已完成', '2024-01-10 20:17:00'),
(7, 'O20240112007', 2, '李四', '电子产品', 2199.00, '已发货', '2024-01-12 13:42:00'),
(8, 'O20240115008', 3, '王五', '服装', 2599.00, '已完成', '2024-01-15 10:30:00'),
(9, 'O20240118009', 1, '张三', '食品', 345.00, '已完成', '2024-01-18 17:05:00'),
(10, 'O20240120010', 5, '孙七', '电子产品', 9999.00, '已发货', '2024-01-20 12:28:00');

-- 预置业务术语（可选：在 P2 业务知识中手动导入）
-- INSERT INTO datarobort.business_knowledge(term, synonyms, recall_enabled) VALUES('GMV','成交额, 销售额',1);
