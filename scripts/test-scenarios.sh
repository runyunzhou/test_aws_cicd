#!/bin/bash
# 场景测试脚本
# 用法: ./test-scenarios.sh <ALB_URL> <scenario>
# 示例: ./test-scenarios.sh http://xxx.elb.amazonaws.com:8080 leak

set -e

ALB_URL="${1:-http://localhost:8080}"
APP_URL="$ALB_URL/SpringBootHelloWorldExampleApplication"
SCENARIO="${2:-help}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
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

info() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1"
}

# 检查服务状态
check_health() {
    log "检查服务状态..."
    curl -s "$APP_URL/api/health" 2>/dev/null || echo "服务无响应"
    echo ""
}

# 查看内存状态
check_memory() {
    log "当前内存状态:"
    response=$(curl -s "$APP_URL/api/reports/stats" 2>/dev/null)
    if [ -n "$response" ]; then
        echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    else
        error "无法获取内存状态（服务可能已崩溃）"
    fi
    echo ""
}

# ========== 场景1: 内存泄漏（常驻内存）==========
scenario_leak() {
    log "========== 场景1: 内存泄漏测试（常驻内存）=========="
    info "原理: 每次生成报表都缓存到静态 Map，永不释放"
    info "预期: 内存持续增长 → 最终 OOM"
    echo ""
    
    check_memory
    
    for i in {1..40}; do
        log "生成报表 $i/40 (类型: detailed, 每次约 10MB)..."
        response=$(curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "detailed", "dateRange": "last_month"}' 2>/dev/null)
        
        if [ -z "$response" ]; then
            error "请求失败，服务可能已崩溃"
            break
        fi
        
        echo "  响应: $(echo $response | head -c 80)..."
        
        if [ $((i % 5)) -eq 0 ]; then
            check_memory
        fi
        sleep 0.5
    done
    
    log "场景1测试完成"
    check_memory
}

# ========== 场景2: GC 压力（临时大对象）==========
scenario_gc() {
    log "========== 场景2: GC 压力测试（临时大对象）=========="
    info "原理: 频繁分配大对象，触发频繁 GC"
    info "预期: GC 频繁 → 响应时间上升 → 极端情况 OOM"
    echo ""
    
    check_memory
    
    log "测试导出功能（观察响应时间变化）..."
    for i in {1..20}; do
        log "导出请求 $i/20..."
        
        start_time=$(date +%s%3N)
        response=$(curl -s "$APP_URL/api/export/reports?dateRange=last_quarter" 2>/dev/null)
        end_time=$(date +%s%3N)
        elapsed=$((end_time - start_time))
        
        if [ -z "$response" ]; then
            error "请求失败"
        else
            echo "  耗时: ${elapsed}ms"
        fi
        
        sleep 0.2
    done
    
    log "场景2测试完成"
    check_memory
}

# ========== 场景3: 压力测试 ==========
scenario_stress() {
    log "========== 场景3: 压力测试 =========="
    info "原理: 快速分配大量临时内存，测试 GC 极限"
    echo ""
    
    check_memory
    
    log "执行压力测试..."
    for i in {1..10}; do
        log "压力测试 $i/10 (每次 20MB x 5 次迭代)..."
        
        start_time=$(date +%s%3N)
        response=$(curl -s "$APP_URL/api/export/stress?sizeMB=20&iterations=5" 2>/dev/null)
        end_time=$(date +%s%3N)
        elapsed=$((end_time - start_time))
        
        if [ -n "$response" ]; then
            echo "  耗时: ${elapsed}ms, 响应: $(echo $response | head -c 100)..."
        else
            error "请求失败，服务可能已崩溃"
            break
        fi
        
        sleep 0.5
    done
    
    log "场景3测试完成"
    check_memory
}

# ========== 场景4: 复合场景（泄漏 + GC 压力）==========
scenario_combined() {
    log "========== 场景4: 复合场景测试 =========="
    info "原理: 同时触发内存泄漏和 GC 压力"
    info "预期: 内存增长 + 响应变慢"
    echo ""
    
    check_memory
    
    for i in {1..15}; do
        log "复合请求 $i/15..."
        
        # 并发发起多种请求
        curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "detailed"}' > /dev/null 2>&1 &
        
        curl -s "$APP_URL/api/export/reports?dateRange=last_quarter" > /dev/null 2>&1 &
        
        curl -s "$APP_URL/api/search?query=test" > /dev/null 2>&1 &
        
        wait
        
        if [ $((i % 5)) -eq 0 ]; then
            check_memory
        fi
        sleep 1
    done
    
    log "场景4测试完成"
    check_memory
}

# ========== 快速 OOM ==========
scenario_oom() {
    log "========== 快速 OOM 测试 =========="
    error "警告: 这将快速消耗内存，服务会崩溃！"
    read -p "确认继续? (y/n) " confirm
    if [ "$confirm" != "y" ]; then
        log "已取消"
        return
    fi
    
    check_memory
    
    log "快速生成大报表..."
    for i in {1..100}; do
        log "生成报表 $i/100 (类型: full, 每次约 20MB)..."
        response=$(curl -s -X POST "$APP_URL/api/reports/generate" \
            -H "Content-Type: application/json" \
            -d '{"type": "full", "dateRange": "last_year"}' 2>/dev/null)
        
        if [ -z "$response" ]; then
            error "服务已崩溃！"
            break
        fi
        
        if [ $((i % 10)) -eq 0 ]; then
            check_memory
        fi
    done
    
    log "OOM 测试完成"
}

# ========== 重置测试环境 ==========
reset_env() {
    log "重置测试环境..."
    curl -s -X DELETE "$APP_URL/api/reports/cache" 2>/dev/null
    echo ""
    check_memory
}

# ========== 主菜单 ==========
case "$SCENARIO" in
    leak)
        scenario_leak
        ;;
    gc)
        scenario_gc
        ;;
    stress)
        scenario_stress
        ;;
    combined)
        scenario_combined
        ;;
    oom)
        scenario_oom
        ;;
    memory)
        check_memory
        ;;
    health)
        check_health
        ;;
    reset)
        reset_env
        ;;
    *)
        echo "用法: $0 <ALB_URL> <scenario>"
        echo ""
        echo "场景:"
        echo "  leak      - 内存泄漏测试（常驻内存，逐渐 OOM）"
        echo "  gc        - GC 压力测试（临时大对象，响应变慢）"
        echo "  stress    - 压力测试（快速分配内存）"
        echo "  combined  - 复合场景（泄漏 + GC 压力）"
        echo "  oom       - 快速 OOM 测试（危险）"
        echo ""
        echo "辅助命令:"
        echo "  memory    - 查看内存状态"
        echo "  health    - 检查服务健康"
        echo "  reset     - 清理缓存，重置测试环境"
        echo ""
        echo "示例:"
        echo "  $0 http://xxx.elb.amazonaws.com:8080 leak"
        echo "  $0 http://xxx.elb.amazonaws.com:8080 gc"
        echo "  $0 http://xxx.elb.amazonaws.com:8080 memory"
        ;;
esac
