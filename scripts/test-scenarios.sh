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
    info "原理: 并发分配大对象，触发频繁 GC"
    info "预期: GC 频繁 → 响应时间上升 → 极端情况 OOM"
    echo ""
    
    check_memory
    
    CONCURRENCY=10  # 并发数
    ROUNDS=5        # 轮数
    
    log "并发测试导出功能（并发数: $CONCURRENCY，轮数: $ROUNDS）..."
    
    # 创建临时文件记录结果
    RESULT_FILE="/tmp/gc_test_results_$$.txt"
    > "$RESULT_FILE"
    
    for round in $(seq 1 $ROUNDS); do
        log "第 $round/$ROUNDS 轮（每轮 $CONCURRENCY 个并发请求，每个 50MB）..."
        
        # 并发发起请求，记录状态码和耗时
        for i in $(seq 1 $CONCURRENCY); do
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
                "$APP_URL/api/export/reports?dateRange=last_year" 2>/dev/null >> "$RESULT_FILE" &
        done
        
        # 等待所有并发请求完成
        wait
        
        # 统计本轮结果
        total=$(wc -l < "$RESULT_FILE" | tr -d ' ')
        success=$(grep "^200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        errors=$(grep -v "^200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        avg_time=$(awk '{sum+=$2} END {if(NR>0) printf "%.3f", sum/NR; else print "0"}' "$RESULT_FILE")
        max_time=$(awk 'BEGIN{max=0} {if($2>max)max=$2} END{printf "%.3f", max}' "$RESULT_FILE")
        
        echo "  统计: 成功=$success, 失败=$errors, 平均耗时=${avg_time}s, 最大耗时=${max_time}s"
        
        # 显示错误详情
        if [ "$errors" -gt 0 ]; then
            error "错误响应:"
            grep -v "^200 " "$RESULT_FILE" | head -5
        fi
        
        > "$RESULT_FILE"  # 清空准备下一轮
        
        echo ""
        check_memory
        sleep 1
    done
    
    rm -f "$RESULT_FILE"
    log "场景2测试完成"
}

# ========== 场景3: 压力测试 ==========
scenario_stress() {
    log "========== 场景3: 高并发压力测试 =========="
    info "原理: 高并发分配大量临时内存，测试 GC 极限"
    echo ""
    
    check_memory
    
    CONCURRENCY=30  # 并发数
    ROUNDS=3        # 轮数
    
    log "执行高并发压力测试（并发数: $CONCURRENCY，轮数: $ROUNDS）..."
    
    RESULT_FILE="/tmp/stress_test_results_$$.txt"
    > "$RESULT_FILE"
    
    for round in $(seq 1 $ROUNDS); do
        log "第 $round/$ROUNDS 轮（每轮 $CONCURRENCY 个并发请求，每个 90MB）..."
        
        # 并发发起 stress 请求
        for i in $(seq 1 $CONCURRENCY); do
            curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
                "$APP_URL/api/export/stress?sizeMB=30&iterations=3" 2>/dev/null >> "$RESULT_FILE" &
        done
        
        wait
        
        # 统计本轮结果
        total=$(wc -l < "$RESULT_FILE" | tr -d ' ')
        success=$(grep "^200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        errors=$(grep -v "^200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        avg_time=$(awk '{sum+=$2} END {if(NR>0) printf "%.3f", sum/NR; else print "0"}' "$RESULT_FILE")
        max_time=$(awk 'BEGIN{max=0} {if($2>max)max=$2} END{printf "%.3f", max}' "$RESULT_FILE")
        
        echo "  统计: 成功=$success, 失败=$errors, 平均耗时=${avg_time}s, 最大耗时=${max_time}s"
        
        # 显示错误详情
        if [ "$errors" -gt 0 ]; then
            error "错误响应码:"
            grep -v "^200 " "$RESULT_FILE" | awk '{print $1}' | sort | uniq -c
        fi
        
        > "$RESULT_FILE"
        
        echo ""
        check_memory
        sleep 1
    done
    
    rm -f "$RESULT_FILE"
    log "场景3测试完成"
}

# ========== 场景4: 复合场景（泄漏 + GC 压力）==========
scenario_combined() {
    log "========== 场景4: 复合场景测试 =========="
    info "原理: 同时触发内存泄漏和高并发 GC 压力"
    info "预期: 内存持续增长 + 响应变慢 + 最终 OOM"
    echo ""
    
    check_memory
    
    ROUNDS=10
    CONCURRENCY=5
    
    RESULT_FILE="/tmp/combined_test_results_$$.txt"
    
    for round in $(seq 1 $ROUNDS); do
        log "第 $round/$ROUNDS 轮..."
        > "$RESULT_FILE"
        
        # 内存泄漏请求（常驻内存）
        for i in $(seq 1 3); do
            curl -s -o /dev/null -w "leak:%{http_code} %{time_total}\n" \
                -X POST "$APP_URL/api/reports/generate" \
                -H "Content-Type: application/json" \
                -d '{"type": "detailed"}' 2>/dev/null >> "$RESULT_FILE" &
        done
        
        # GC 压力请求（临时大对象）
        for i in $(seq 1 $CONCURRENCY); do
            curl -s -o /dev/null -w "gc:%{http_code} %{time_total}\n" \
                "$APP_URL/api/export/reports?dateRange=last_year" 2>/dev/null >> "$RESULT_FILE" &
        done
        
        wait
        
        # 统计结果
        leak_success=$(grep "^leak:200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        leak_errors=$(grep "^leak:" "$RESULT_FILE" | grep -v "^leak:200 " | wc -l | tr -d ' ')
        gc_success=$(grep "^gc:200 " "$RESULT_FILE" | wc -l | tr -d ' ')
        gc_errors=$(grep "^gc:" "$RESULT_FILE" | grep -v "^gc:200 " | wc -l | tr -d ' ')
        
        echo "  泄漏请求: 成功=$leak_success, 失败=$leak_errors"
        echo "  GC请求: 成功=$gc_success, 失败=$gc_errors"
        
        # 显示错误
        total_errors=$((leak_errors + gc_errors))
        if [ "$total_errors" -gt 0 ]; then
            error "有 $total_errors 个错误请求"
        fi
        
        check_memory
        sleep 1
    done
    
    rm -f "$RESULT_FILE"
    log "场景4测试完成"
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
