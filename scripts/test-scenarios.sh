#!/bin/bash
# 场景测试脚本
# 用法: ./test-scenarios.sh <ALB_URL> <scenario>
# 示例: ./test-scenarios.sh http://xxx.elb.amazonaws.com:8080 a

set -e

ALB_URL="${1:-http://localhost:8080}"
APP_URL="$ALB_URL/SpringBootHelloWorldExampleApplication"
SCENARIO="${2:-help}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log() {
    echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[$(date '+%H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[$(date '+%H:%M:%S')]${NC} $1"
}

# 检查服务状态
check_health() {
    log "检查服务状态..."
    curl -s "$APP_URL/api/health" | head -c 200
    echo ""
}

# 查看内存状态
check_memory() {
    log "当前内存状态:"
    curl -s "$APP_URL/api/reports/stats" | python3 -m json.tool 2>/dev/null || curl -s "$APP_URL/api/reports/stats"
    echo ""
}

# ========== 场景A: 可观测性缺失 ==========
scenario_a() {
    log "========== 场景A: 可观测性缺失测试 =========="
    warn "测试随机延迟，观察响应时间变化"
    
    for i in {1..20}; do
        log "请求 $i/20..."
        time curl -s -o /dev/null -w "状态码: %{http_code}, 耗时: %{time_total}s\n" \
            "$APP_URL/api/search?query=test&page=$i"
        sleep 0.5
    done
    
    log "场景A测试完成"
    warn "预期现象: 部分请求响应时间异常长，但无日志可查"
}

# ========== 场景B: 资源瓶颈（内存泄漏）==========
scenario_b() {
    log "========== 场景B: 资源瓶颈测试 =========="
    warn "通过报表生成触发内存泄漏"
    
    check_memory
    
    for i in {1..30}; do
        log "生成报表 $i/30 (类型: detailed, 每次约10MB)..."
        curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "detailed", "dateRange": "last_month"}' | head -c 100
        echo ""
        
        if [ $((i % 5)) -eq 0 ]; then
            check_memory
        fi
        sleep 1
    done
    
    log "场景B测试完成"
    warn "预期现象: 内存持续增长，最终可能 OOM"
}

# ========== 场景B2: 快速触发OOM ==========
scenario_b_fast() {
    log "========== 场景B2: 快速OOM测试 =========="
    error "警告: 这将快速消耗内存，可能导致服务崩溃！"
    read -p "确认继续? (y/n) " confirm
    if [ "$confirm" != "y" ]; then
        log "已取消"
        return
    fi
    
    check_memory
    
    for i in {1..50}; do
        log "生成大报表 $i/50 (类型: full, 每次约20MB)..."
        curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "full", "dateRange": "last_year"}' &
        
        if [ $((i % 10)) -eq 0 ]; then
            wait
            check_memory
        fi
    done
    wait
    
    log "场景B2测试完成"
}

# ========== 场景C: 复合场景 ==========
scenario_c() {
    log "========== 场景C: 复合场景测试 =========="
    warn "同时触发内存泄漏和随机延迟"
    
    check_memory
    
    for i in {1..20}; do
        log "复合请求 $i/20..."
        
        # 同时发起报表生成和搜索请求
        curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "detailed"}' > /dev/null &
        
        curl -s "$APP_URL/api/search?query=task" > /dev/null &
        
        curl -s "$APP_URL/api/export/tasks?includeHistory=true&limit=5000" > /dev/null &
        
        wait
        
        if [ $((i % 5)) -eq 0 ]; then
            check_memory
        fi
        sleep 1
    done
    
    log "场景C测试完成"
    warn "预期现象: 响应变慢 + 内存增长"
}

# ========== 导出测试 ==========
scenario_export() {
    log "========== 导出功能测试 =========="
    
    log "测试小数据导出..."
    time curl -s "$APP_URL/api/export/tasks?format=csv&limit=100"
    echo ""
    
    log "测试大数据导出..."
    time curl -s "$APP_URL/api/export/tasks?format=xlsx&includeHistory=true&limit=10000"
    echo ""
    
    log "测试年度报表导出..."
    time curl -s "$APP_URL/api/export/reports?dateRange=last_year"
    echo ""
}

# ========== 主菜单 ==========
case "$SCENARIO" in
    a)
        scenario_a
        ;;
    b)
        scenario_b
        ;;
    b-fast)
        scenario_b_fast
        ;;
    c)
        scenario_c
        ;;
    export)
        scenario_export
        ;;
    memory)
        check_memory
        ;;
    health)
        check_health
        ;;
    all)
        scenario_a
        echo ""
        scenario_b
        echo ""
        scenario_c
        ;;
    *)
        echo "用法: $0 <ALB_URL> <scenario>"
        echo ""
        echo "场景:"
        echo "  a        - 可观测性缺失测试（随机延迟）"
        echo "  b        - 资源瓶颈测试（内存泄漏，渐进）"
        echo "  b-fast   - 快速OOM测试（危险）"
        echo "  c        - 复合场景测试"
        echo "  export   - 导出功能测试"
        echo "  memory   - 查看内存状态"
        echo "  health   - 检查服务健康"
        echo "  all      - 运行所有场景"
        echo ""
        echo "示例:"
        echo "  $0 http://xxx.elb.amazonaws.com:8080 a"
        echo "  $0 http://xxx.elb.amazonaws.com:8080 b"
        ;;
esac
