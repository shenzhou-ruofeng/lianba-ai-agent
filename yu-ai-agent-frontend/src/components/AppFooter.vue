<template>
  <footer class="app-footer">
    <div class="footer-bottom">
      <p>© {{ currentYear }} 恋吧AI超级智能体应用平台 - 让AI为你服务</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background-color: #fff;
  padding: 20px 0;
  color: #666;
  border-top: 1px solid #eee;
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-bottom {
  text-align: center;
  color: #999;
  font-size: 0.9rem;
}
</style> 