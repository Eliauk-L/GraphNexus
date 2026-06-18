# =============================================================================
# GraphNexus Nginx — 共享反向代理头配置
# =============================================================================
# 用途: 被 conf.d/graphnexus.conf 中各 location 块 include 引用，避免重复
# =============================================================================

proxy_set_header Host               $host;
proxy_set_header X-Real-IP           $remote_addr;
proxy_set_header X-Forwarded-For     $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto   $scheme;
proxy_set_header X-Forwarded-Host    $host;
proxy_set_header X-Forwarded-Port    $server_port;
proxy_set_header X-Trace-ID          $request_id;

# WebSocket 支持（用于未来实时通知推送）
proxy_http_version          1.1;
proxy_set_header Upgrade    $http_upgrade;
proxy_set_header Connection $connection_upgrade;